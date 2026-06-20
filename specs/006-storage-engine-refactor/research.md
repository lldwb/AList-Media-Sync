# 研究报告：存储引擎重构与体验优化

**日期**：2026-06-20 | **功能**：006-storage-engine-refactor

## 决策 1：存储引擎策略模式接口设计

### 决策

定义 `StorageEngineStrategy` 接口，使用 Spring `@Component` + 构造器注入 `List<StorageEngineStrategy>` + 按 `type()` 方法构建分发 Map。`StorageEngineService` 通过 `resolve(StorageEngine)` 方法根据 `engineType` 字段选择策略实现。

### 接口方法

```java
public interface StorageEngineStrategy {
    String type();
    List<FileEntry> listFiles(StorageEngine engine, String path, int page, int perPage);
    FileEntry getFileInfo(StorageEngine engine, String path);
    InputStream downloadFile(StorageEngine engine, String path);
    void uploadFile(StorageEngine engine, String remotePath, InputStream inputStream, long fileSize);
    void createDirectory(StorageEngine engine, String path);
    void deleteFile(StorageEngine engine, String path);
    List<DirectoryEntry> listDirectories(StorageEngine engine, String path);
    boolean testConnection(StorageEngine engine);
}
```

- `AListStorageStrategy`：内部使用 `RestClient` + HTTP 调用 AList API
- `LocalStorageStrategy`：内部使用 `java.nio.file.Files` + `Path` 操作本地文件系统

### 策略分发

```java
@Service
public class StorageEngineService {
    private final Map<String, StorageEngineStrategy> strategyMap;

    public StorageEngineService(List<StorageEngineStrategy> strategies) {
        this.strategyMap = strategies.stream()
            .collect(Collectors.toMap(StorageEngineStrategy::type, Function.identity()));
    }

    private StorageEngineStrategy resolve(StorageEngine engine) {
        StorageEngineStrategy s = strategyMap.get(engine.getEngineType());
        if (s == null) throw new IllegalArgumentException("不支持的引擎类型：" + engine.getEngineType());
        return s;
    }
}
```

### 理由

1. 接口解耦：AList 用 HTTP、本地用 NIO，策略模式隐藏实现差异
2. Spring 原生支持：构造器注入 `List<StorageEngineStrategy>` 自动收集所有实现
3. FileEntry/DirectoryEntry 统一 DTO：消除当前代码中的 `@SuppressWarnings("unchecked")`
4. InputStream 传输抽象：AList 从 HTTP 响应读取，本地从 `Files.newInputStream()` 读取

### 考虑的替代方案

| 方案 | 被拒绝原因 |
|------|-----------|
| if-else 分支分发 | 违反开闭原则，每新增引擎类型都要修改分发代码 |
| 抽象类 + 模板方法 | 两种引擎无共享逻辑，强行抽象增加复杂度 |
| Decorator 模式 | 当前不需要叠加行为，不符合 YAGNI |

---

## 决策 2：转码 8 状态机实现

### 决策

在 `TranscodeStatus` 枚举中定义 8 个值（8 状态模型：3 对失败/重试状态 + PENDING + COMPLETED），采用状态转换表 + 守卫条件校验，将 `TranscodeFileProcessor.doProcess` 拆分为三个独立步骤方法。

### 状态枚举

```java
public enum TranscodeStatus {
    PENDING(0),          // 待处理
    DOWNLOADING(1),      // 下载中
    DOWNLOAD_FAILED(2),  // 下载失败
    TRANSCODING(3),      // 转码中
    TRANSCODE_FAILED(4), // 转码失败
    UPLOADING(5),        // 上传中
    UPLOAD_FAILED(6),    // 上传失败
    COMPLETED(7);        // 完成
}
```

### 状态转换规则

```text
PENDING → DOWNLOADING → TRANSCODING → UPLOADING → COMPLETED
              ↓              ↓             ↓
      DOWNLOAD_FAILED  TRANSCODE_FAILED  UPLOAD_FAILED
              ↓              ↓             ↓
         [重试回DOWNLOADING] [重试回TRANSCODING] [重试回UPLOADING]
         删除部分下载文件  保留源临时文件    保留源+输出临时文件
```

### 合法性校验

使用 `Set<Map.Entry<TranscodeStatus, TranscodeStatus>>` 定义合法转换集合，每次状态变更前校验，非法转换抛 `IllegalStateException`。

### 重试逻辑

- `DOWNLOAD_FAILED` → 回退到 `DOWNLOADING`，删除部分下载文件，重新下载
- `TRANSCODE_FAILED` → 回退到 `TRANSCODING`，保留已下载源文件，跳过下载步骤
- `UPLOAD_FAILED` → 回退到 `UPLOADING`，保留源文件和转码输出，跳过前两步

### 临时文件生命周期

| 阶段 | 文件 | 创建时机 | 保留条件 | 清理时机 |
|------|-----|---------|---------|---------|
| 下载 | sourceTemp | 步骤开始 | 下载成功后保留 | 上传成功后删除 |
| 转码 | outputTemp | 转码完成 | 保留直至上传成功 | 上传成功后删除 |
| 孤立 | 两者 | — | 超过 24 小时 | CleanupService 定时清理 |

### 理由

1. 细粒度失败隔离：三步独立 + 8 状态模型使得每步失败不影响已完成工作
2. 显式状态转换表：可测试、可审计、可维护
3. 步骤拆分比引入状态机框架更轻量，符合 YAGNI

### 考虑的替代方案

| 方案 | 被拒绝原因 |
|------|-----------|
| Spring State Machine | 过度设计，8 状态 10 条转换规则不需要框架 |
| EnumMap 二维表 | Set<Pair> + switch 更直观 |
| ACID 事务跨三步 | 转码是长时间操作，不能占用数据库连接 |

---

## 决策 3：JAVE2 编解码参数优化

### 决策

将 `AudioAttributes.setCodec(null)` 和 `VideoAttributes.setCodec(null)`，让 FFmpeg 根据输出容器格式自动选择编解码器。码率从 `AppProperties.Transcode` 配置中读取，默认 128 kbps。

### 理由

1. JAVE2 `AudioAttributes` 源码确认：codec 为 null 时完全跳过 `-acodec`/`-vcodec` 参数，FFmpeg 执行默认编解码器选择逻辑
2. VideoConversionMonitor 项目（同一作者）已实践验证 `codec=null` 可行
3. 未来兼容性：FFmpeg 升级后可自动使用更优编解码器（如 libx265），无需修改代码
4. 各格式行为：MP3 自动用 libmp3lame，MP4 自动用 libx264，FLV 自动用 flv+libmp3lame

### 考虑的替代方案

| 方案 | 被拒绝原因 |
|------|-----------|
| 保留硬编码 + 可配置 | 增加配置项数量，用户需知道编解码器名称 |
| 仅 MP3 保留 codec | 不一致，既然 null 可行应统一处理 |
| 编解码器优先级列表 | 过度设计，FFmpeg 自身已有选择算法 |

---

## 决策 4：树状目录浏览组件设计

### 决策

扁平化 `Map<path, TreeNode>` + `useReducer` 管理树状态，内联展开式树选择面板，后端新增 `GET /api/storage-engines/{id}/directories?path=xxx` 端点。

### 组件方案

- 状态管理：`useReducer` + 扁平化 Map，按路径做 key 天然去重
- 交互方式：内联展开（非弹窗），点击目录节点实时加载子目录
- 路径回填：选中路径自动填入输入框，面包屑显示当前层级
- 后端接口：通过策略模式调用 `listDirectories()`，AList 和本地引擎对前端透明

### 理由

1. 扁平化 Map 避免深层嵌套不可变更新的性能问题
2. `useReducer` 天然适合多 action 状态机，项目已有 `usePagination`/`usePolling` 先例
3. 内联展开比弹窗更适合表单上下文，不打断用户填写流程
4. 后端过滤只返回目录，减少传输量

### 考虑的替代方案

| 方案 | 被拒绝原因 |
|------|-----------|
| 递归组件每节点独立 useState | 状态分散，无法全局操作，重复请求无缓存 |
| 引入 rc-tree 等第三方组件 | 与项目"纯手写无第三方 UI 库"原则冲突 |
| 弹窗式目录选择器 | 表单场景操作路径过深，不够高效 |

---

## 决策 5：Cron 图形化配置组件设计

### 决策

五字段独立选择器（每/指定值/范围/步进）+ 预设快捷模式 + 复用已有 `parseCron`/`buildDescription`/`calcNextExecution` 逻辑。

### 组件方案

- 每个字段（分钟/小时/日/月/周）：快速选项 radio 组（每(*) / 指定值 / 范围 / 步进）
- 预设常用模式：每小时、每天凌晨、每天 8 点、每周一、每月 1 号、每 6 小时
- 新增 `buildCronExpression(fields)` 反向生成函数
- 复用 `cron.ts` 已有解析和描述逻辑，零第三方依赖

### 理由

1. 项目已有完整 `cron.ts` 解析器，扩展成本远低于引入第三方库
2. 预设快捷模式兼顾新手和高级用户
3. 双模式（图形化 + 手动输入）保持灵活性

### 考虑的替代方案

| 方案 | 被拒绝原因 |
|------|-----------|
| 引入 cronstrue | 增加 30KB 打包体积，中文 i18n 不可控 |
| 引入 cron-parser | 项目已有 `calcNextExecution` 功能等价 |
| 纯文本输入 | Cron 语法对非技术用户不友好 |

---

## 决策 6：Webhook 地址复制功能

### 决策

`navigator.clipboard.writeText()` + `execCommand('copy')` 降级 + 按钮内状态切换提示（2 秒自动恢复）。

### 方案

- 复制：优先 `navigator.clipboard`，降级 `execCommand('copy')`
- URL 来源：`window.location.origin + '/api/webhooks/recorder'`
- 反馈：按钮图标从复制切换为勾选 + 文字"已复制"，2 秒后恢复
- 展示：只读输入框显示 URL + 内嵌复制按钮

### 理由

1. 项目没有 Toast 通知系统，按钮内状态切换是最轻量反馈方式
2. 降级代码仅 8 行，保留增加健壮性
3. 与 GitHub 复制按钮体验一致

### 考虑的替代方案

| 方案 | 被拒绝原因 |
|------|-----------|
| 引入 react-hot-toast | 复制是低频微交互，全局 Toast 信息层级过高 |
| window.alert() 提示 | 阻塞 UI，体验差 |
| 仅 navigator.clipboard 不降级 | 降级代码成本极低，保留更健壮 |
