# 快速验证指南

**关联**：[plan.md](./plan.md)

本文档描述如何在本地验证重构是否成功。任何 PR 提交前必须按此清单跑一遍。

## 步骤 1：建立基线（仅 PR 1 之前需要）

```bash
cd C:\Users\Administrator\Documents\GitHub\AList-Media-Sync
./mvnw test -DskipITs=false 2>&1 | tee baseline-tests.log
```

记录：

- 通过用例数（`Tests run: X, Failures: Y, Errors: Z, Skipped: W`）
- 提交基线对应的 git commit SHA
- 写入 `quickstart-results.md` 的"基线"段落

## 步骤 2：每个 PR 内的回归验证

### 2.1 编译与单元测试

```bash
./mvnw clean test
```

**通过标准**：

- `BUILD SUCCESS`
- 用例数 ≥ 基线
- 无新增 Failures / Errors

### 2.2 启动应用快速冒烟

```bash
./mvnw spring-boot:run
```

打开 http://localhost:8080/app/ ，使用 `admin / admin123` 登录，逐项点击：

| 页面 | 期望 |
|------|------|
| 存储引擎 | 列表正常加载，"测试连接"对 AList 引擎返回成功 |
| 同步任务 | 列表加载、详情查看正常 |
| 转码任务 | 列表加载、详情查看正常 |
| Webhook 规则 | 列表加载、创建一条新规则成功 |
| 仪表板 | 统计数字加载 |

任何 500/异常即视为回归。

### 2.3 关键日志字段检查

随便触发一次同步或转码任务，检查 `logs/app.log`：

- `traceId` 出现在任务相关的所有 log 行；
- `module=sync` / `module=transcode` MDC 字段正确；
- WebSocket 推送的 message type 不变（`SYNC_PROGRESS` / `TRANSCODE_PROGRESS` / `WEBHOOK_EVENT`）。

### 2.4 行数与方法长度核查

```bash
# 验证关键方法行数（粗略统计）
grep -n "private void executeSyncTaskInternal" src/main/java/top/lldwb/alistmediasync/sync/service/SyncService.java
grep -n "private TranscodeResult doProcess" src/main/java/top/lldwb/alistmediasync/transcode/service/TranscodeFileProcessor.java

# 验证整体文件行数
wc -l src/main/java/top/lldwb/alistmediasync/sync/service/SyncService.java
wc -l src/main/java/top/lldwb/alistmediasync/transcode/service/TranscodeService.java
wc -l src/main/java/top/lldwb/alistmediasync/transcode/service/TranscodeFileProcessor.java
```

**通过标准**（参见 spec.md SC-001~SC-004）：

- `SyncService.java` ≤ 420 行
- `TranscodeService.java` ≤ 560 行
- `TranscodeFileProcessor.java` ≤ 400 行
- `executeSyncTaskInternal` ≤ 50 行
- `doProcess` ≤ 40 行

### 2.5 验证零行为变更（行为对等）

在本地启动 AList + 应用，准备一个含 5 个文件、1 个子目录的源目录，分别以下列组合执行同步任务，重构前后产生的 `TaskExecution` 必须一致：

| 组合 | SyncMode | ConflictStrategy | 期望 |
|------|----------|-----------------|------|
| C1 | NEW_ONLY | SKIP | 5 success / 0 failed |
| C2 | FULL | OVERWRITE | 5 success / 0 failed，目标多余文件被删除 |
| C3 | MOVE | RENAME | 5 success / 0 failed，源文件被删除 |

## 步骤 3：PR 提交前的最终核对

```bash
# 章程合规自检（手动逐项）
cat AGENTS.md | head -50   # 重读 10 项检查表

# 提交信息样例
git commit -m "refactor(common): 抽取 PathUtils 与 TraceContext.runWith 消除路径与 traceId 样板重复

- 新增 PathUtils（join/parentDir/baseName/swapExtension/trimLeadingSlash/normalize）
- 在 TraceContext 新增 runWith(module, operation, Runnable)
- 删除 SyncService/TranscodeService/TranscodeFileProcessor 中重复的私有路径工具方法
- 修复 SyncService L96 / WebhookService L112 的格式问题
- 配套新增 PathUtilsTest，覆盖率 ≥95%

零行为变更，./mvnw test 通过 N 个用例（基线 N 个）。"
```
