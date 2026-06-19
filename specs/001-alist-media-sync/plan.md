# 实现计划：AList 媒体同步与转码工具

**分支**：`001-alist-media-sync` | **日期**：2026-06-19 | **规格**：[spec.md](./spec.md)

**输入**：来自 `/specs/001-alist-media-sync/spec.md` 的功能规格

**参考实现**：
- TaoSync（`https://github.com/dr34m-cn/taosync`）：AList 同步引擎的 Python/Tornado 参考
- videoConversionMonitor（`https://github.com/lldwb/videoConversionMonitor`）：Java/Spring Boot + JAVE2 转码参考
- 录播姬 Webhook v2 协议

## 摘要

实现 AList 媒体同步与转码工具的全部业务逻辑（27 个 FR，4 个用户故事）。核心交付物包括：存储引擎管理（CRUD + 连接测试）、同步任务引擎（三模式：仅新增/全同步/移动，支持 cron/间隔/手动触发）、JAVE2 视频转码（MP3/MP4/FLV，两阶段处理——单线程递归扫描 + 多线程并行转码）、录播姬 Webhook v2 接收端点（六种事件类型，EventId 去重）、任务调度持久化与崩溃恢复。Spring Boot 4.1.0 + Java 21（虚拟线程）+ Spring Data JPA（H2 文件持久化）+ JAVE2 3.5.0 封装的 FFmpeg。

## 技术上下文

**语言/版本**：Java 21（LTS，启用虚拟线程）

**主要依赖**：Spring Boot 4.1.0（Spring Framework 7.x）、Spring Data JPA + Hibernate、H2（文件模式）、Spring WebMVC、Spring Validation、Lombok、JAVE2（jave-core 3.5.0 + jave-nativebin-win64/linux64）、Jackson 3.x（Spring Boot 4.x 内置）、Jakarta Servlet 6.1

**存储**：H2 内嵌数据库（`jdbc:h2:file:${app.data-dir}/alist_media_sync`），文件持久化模式。核心实体：StorageEngine、SyncTask、TaskExecution、WebhookRule、WebhookEvent、TranscodeTask

**测试**：Spring Boot Test（`@WebMvcTest`、`@DataJpaTest`、`@SpringBootTest`），JUnit 5

**目标平台**：x86-64 Linux 服务器 / Docker 容器（也兼容 Windows 开发环境）

**项目类型**：单体 Web 服务（REST API + Web 管理界面）

**性能目标**：单文件 1GB 以内转码 < 5 分钟，并发转码 5 个任务降级 < 20%，单次同步 10000+ 文件无崩溃/内存泄漏

**约束**：分层架构（Controller → Service → Repository）不可协商、所有写操作必须 `@Transactional`、实体不可直接暴露（DTO 包装）、API 响应统一 `ApiResult<T>` 格式、中文优先

**规模/范围**：约 30-40 个 Java 源文件（实体 6、Repository 6、Service 8、Controller 5、DTO 10+、配置 5+、工具类 3+）

## 章程检查

*门禁：必须在阶段 0 研究之前通过。在阶段 1 设计后重新检查。*

| 章程原则 | 适用性 | 状态 |
|---------|--------|------|
| I. 分层架构（不可协商） | **完全适用** — 所有业务代码严格遵循 Controller/Service/Repository 分层 | ✅ 通过 |
| II. 数据完整性优先 | **完全适用** — 所有实体 `@Version` 乐观锁、所有写操作 `@Transactional`、中间状态持久化 | ✅ 通过 |
| III. RESTful API 契约优先 | **完全适用** — 所有端点定义 DTO 输入/输出、`ApiResult<T>` 统一响应 | ✅ 通过 |
| IV. 中文优先 | **完全适用** — 所有注释、日志消息、提交信息、文档使用简体中文 | ✅ 通过 |
| V. 测试不可省略 | **完全适用** — Service 层 > 80%、Repository 层 > 60%、Controller 层 > 70% | ✅ 通过 |
| VI. 简洁至上（YAGNI） | **适用** — 仅添加 JAVE2 一个新增依赖（规范已论证），认证不使用 Spring Security（配置文件凭据即可） | ✅ 通过 |

**门禁结果**：全部通过，无违规项。

## 项目结构

### 文档（本功能）

```text
specs/001-alist-media-sync/
├── plan.md              # 本文件（/speckit-plan 命令输出）
├── research.md          # 阶段 0 输出
├── data-model.md        # 阶段 1 输出
├── quickstart.md        # 阶段 1 输出
├── contracts/           # 阶段 1 输出（API 契约定义）
└── tasks.md             # 阶段 2 输出（/speckit-tasks 命令创建）
```

### 源代码（仓库根目录）

```text
src/main/java/top/lldwb/alistmediasync/
├── AListMediaSyncApplication.java          # 已有：启动类
│
├── config/
│   ├── AsyncConfig.java                   # 新增：@EnableAsync + 虚拟线程/线程池配置
│   ├── WebMvcConfig.java                  # 新增：CORS、拦截器（认证检查）
│   ├── SchedulingConfig.java              # 新增：@EnableScheduling
│   └── AppProperties.java                 # 新增：@ConfigurationProperties 绑定
│
├── entity/
│   ├── StorageEngine.java                 # 新增：存储引擎实体
│   ├── SyncTask.java                      # 新增：同步任务实体
│   ├── TaskExecution.java                 # 新增：任务执行记录实体
│   ├── WebhookRule.java                   # 新增：Webhook 处理规则实体
│   ├── WebhookEvent.java                  # 新增：Webhook 事件记录实体
│   └── TranscodeTask.java                 # 新增：转码任务实体
│
├── repository/
│   ├── StorageEngineRepository.java       # 新增：JPA Repository
│   ├── SyncTaskRepository.java            # 新增：JPA Repository
│   ├── TaskExecutionRepository.java       # 新增：JPA Repository
│   ├── WebhookRuleRepository.java         # 新增：JPA Repository
│   ├── WebhookEventRepository.java        # 新增：JPA Repository
│   └── TranscodeTaskRepository.java       # 新增：JPA Repository
│
├── dto/
│   ├── ApiResult.java                     # 新增：统一响应包装
│   ├── storage/
│   │   ├── StorageEngineCreateDTO.java    # 新增
│   │   ├── StorageEngineUpdateDTO.java    # 新增
│   │   └── StorageEngineVO.java           # 新增（脱敏，不返回凭据）
│   ├── sync/
│   │   ├── SyncTaskCreateDTO.java         # 新增
│   │   ├── SyncTaskUpdateDTO.java         # 新增
│   │   ├── SyncTaskVO.java                # 新增
│   │   └── SyncProgressVO.java            # 新增（实时进度）
│   ├── transcode/
│   │   ├── TranscodeTaskCreateDTO.java    # 新增
│   │   └── TranscodeTaskVO.java           # 新增
│   └── webhook/
│       ├── WebhookRuleCreateDTO.java      # 新增
│       └── WebhookRuleVO.java             # 新增
│
├── service/
│   ├── StorageEngineService.java          # 新增：CRUD + 连接测试
│   ├── SyncService.java                   # 新增：同步任务执行引擎
│   ├── SyncTaskManageService.java         # 新增：同步任务 CRUD + 启用/禁用
│   ├── TranscodeService.java              # 新增：JAVE2 转码引擎（核心）
│   ├── WebhookService.java                # 新增：Webhook 事件处理
│   ├── WebhookRuleService.java            # 新增：Webhook 规则 CRUD
│   ├── ScheduleService.java               # 新增：定时任务注册/恢复
│   └── CleanupService.java               # 新增：过期记录清理
│
├── controller/
│   ├── StorageEngineController.java       # 新增：存储引擎管理 API
│   ├── SyncTaskController.java            # 新增：同步任务管理 API
│   ├── TranscodeTaskController.java       # 新增：转码任务管理 API
│   ├── WebhookController.java             # 新增：录播姬 Webhook 接收端点（无需认证）
│   └── WebhookRuleController.java         # 新增：Webhook 规则管理 API
│
├── client/
│   └── AListClient.java                   # 新增：AList API HTTP 客户端
│
├── interceptor/
│   └── AuthInterceptor.java               # 新增：配置文件凭据认证拦截器
│
└── util/
    ├── MagicBytesDetector.java            # 新增：文件魔数检测工具
    └── FileUtil.java                      # 新增：文件操作工具

src/main/resources/
└── application.yaml                       # 修改：添加转码、调度、认证等配置项
```

**结构决策**：采用"选项 1：单项目"布局。由于本项目是单体 Spring Boot 应用，按技术分层（config/entity/repository/dto/service/controller/client/interceptor/util）组织源代码。严格遵循章程的分层架构原则。

## 技术设计决策

### 1. 虚拟线程 vs 传统线程池

**决策**：使用 Java 21 虚拟线程（`spring.threads.virtual.enabled=true`），但对于转码执行器显式配置 `ThreadPoolTaskExecutor`。

**理由**：
- 虚拟线程极大简化了并发编程模型，`@Async` 方法无需复杂的线程池调优
- **但转码是 CPU 密集型任务**，虚拟线程对此类任务不如有界线程池有效——无限创建虚拟线程执行 FFmpeg 会导致 CPU 颠簸
- 因此：Tomcat 请求处理使用虚拟线程（默认），转码执行器使用有界 `ThreadPoolTaskExecutor`（core=8, max=CPU 核心数, queue=64, CallerRunsPolicy）

**配置**：
```yaml
spring:
  threads:
    virtual:
      enabled: true
---
# 转码线程池（显式配置，禁用虚拟线程）
app:
  transcode:
    pool:
      core-size: 8
      max-size: ${app.transcode.max-concurrent:32}
      queue-capacity: 64
```

### 2. 认证方案

**决策**：不使用 Spring Security，使用自定义 `AuthInterceptor` + 配置文件凭据。

**理由**（YAGNI 原则）：
- 规范 FR-028 仅要求用户名+密码认证，配置文件设置凭据
- Spring Security 引入约 8 个传递依赖，复杂度远超需求
- 自定义拦截器只需约 80 行代码：从 `application.yaml` 读取 `app.auth.username` / `app.auth.password`（BCrypt 哈希），拦截所有 `/api/**` 请求（除 `/api/webhooks/**`），检查 `Authorization: Basic <base64>` 头

**例外路径**：`/api/webhooks/**`、`/actuator/health` 不要求认证。

### 3. AList API 客户端

**决策**：使用 Spring `RestClient`（Spring Framework 6.1+ 引入，替代 RestTemplate）。

**理由**：
- Spring Boot 4.1 内置，零额外依赖
- 支持同步 HTTP 调用（AList API 交互不需要响应式）
- 比 RestTemplate 更现代的 Fluent API，比 WebClient 更轻量

**核心方法**：
```java
// 文件列表
List<AListFileVO> listFiles(String engineBaseUrl, String token, String path, int page, int perPage)
// 文件下载（返回 InputStream）
InputStream downloadFile(String engineBaseUrl, String token, String filePath)
// 文件上传（流式）
void uploadFile(String engineBaseUrl, String token, String remotePath, InputStream fileStream, long fileSize)
// 文件删除
void deleteFile(String engineBaseUrl, String token, String remotePath)
// 创建目录
void createDirectory(String engineBaseUrl, String token, String path)
```

### 4. 两阶段处理模式（参考 videoConversionMonitor）

**决策**：转码流程严格分为两阶段：

**阶段 1 — 单线程递归扫描**：
1. 通过 AList API `fs/list` 递归遍历源目录（深度优先）
2. 对每个文件调用 `MagicBytesDetector` 检测视频格式（FLV/MP4/M4V）
3. 检查目标路径是否已存在输出文件（`.mp3`/`.mp4`/`.flv`）
4. 跳过已存在且非覆盖模式的文件
5. 收集符合条件的文件到 `List<TranscodeCandidate>`

**阶段 2 — 多线程并行转码**：
1. 对候选列表中的每个文件调用 `self.transcodeFile(candidate)`（`@Async` + `@Lazy` 自注入代理）
2. 每个文件返回 `CompletableFuture<TranscodeResult>`
3. 收集所有 Future，串行 `.get()` 等待完成（保持调用线程不阻塞太多）
4. 转码过程中通过 `EncoderProgressListener` 持续更新 `TranscodeTask.progress` 到数据库

### 5. 临时文件策略（与 Feature 002 的关系）

Feature 001 的转码实现**直接采用 Feature 002 规定的完整临时文件策略**（两者在同一实现周期中完成）：

- 临时文件写入 `app.temp-dir`（用户可配置，默认 `java.io.tmpdir/alist-media-sync/transcode`）
- 使用可配置后缀（`app.temp-suffix`，默认 `.tmp`）
- 转码中：`output.mp4.tmp` → 转码成功：重命名为 `output.mp3` → 上传至 AList → 上传成功：删除本地文件
- 并发安全：临时文件名包含 UUID（`output_<uuid>.mp4.tmp`）

详见 `specs/002-transcode-temp-suffix-config/plan.md`。

### 6. 定时任务持久化与崩溃恢复

**决策**：使用 Spring `@Scheduled` + 数据库持久化的自定义调度管理，而非直接使用 `TaskSchedulingConfigurer`。

**理由**：
- Spring Boot 内置 `@Scheduled` 足够满足 cron/间隔触发需求
- 自定义 `ScheduleService` 在应用启动时从数据库加载所有已启用的 `SyncTask`，动态注册 `@Scheduled` 任务
- 应用关闭/崩溃后重启：`@PostConstruct` 扫描 `SyncTask` 表中 `enabled=true` 的记录，重新创建调度任务；将状态为"运行中"的 `TaskExecution` 标记为"中断"

### 7. Jackson 3.x 迁移（Spring Boot 4.x 强制）

**注意事项**：
- Jackson 3.x 的 Maven 坐标为 `tools.jackson`，但 Spring Boot 4.x starter 已自动管理
- 代码中如直接使用 Jackson 注解，需从 `com.fasterxml.jackson.annotation` 导入（此包路径未变）
- 避免直接使用 Jackson ObjectMapper——使用 Spring 注入的实例确保配置一致

## 数据模型概要

参见 `data-model.md`（阶段 1 产物）中完整定义。核心实体关系：

```
StorageEngine 1──N SyncTask 1──N TaskExecution
StorageEngine 1──N WebhookRule 1──N WebhookEvent
SyncTask 1──N TranscodeTask
```

关键设计规则：
- 所有实体包含 `@Version` 乐观锁字段
- 密码/Token 字段使用 AES-256-GCM 加密存储（`@Converter`）
- 时间戳使用 `LocalDateTime`（不依赖时区，存储为 UTC）
- 状态字段使用 `enum` 映射（`@Enumerated(EnumType.STRING)`）

## 复杂性追踪

> 无违规项，无需记录。

## 与其他规格的关系

| 规格 | 关系 | 说明 |
|------|------|------|
| 002-transcode-temp-suffix-config | **并行实现** | 002 是对 001 转码子系统的增强，由于两者都未实现，应在同一迭代周期中并行构建。001 的转码代码直接采用 002 的临时文件策略。 |
| 003-docker-deploy | **已实现（基础设施）** | 003 提供的 Docker 部署和 Actuator 健康检查已就绪，为 001/002 的业务代码测试和部署提供基础。 |
