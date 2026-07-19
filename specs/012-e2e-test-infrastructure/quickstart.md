# 快速入门：端到端测试基础设施

**功能**：`012-e2e-test-infrastructure` | **用途**：端到端验证运行指南

本文档记录可运行的验证场景，证明本功能端到端可用。包含前提条件、设置命令、运行命令与预期结果。实现细节见 `tasks.md`（待 `/speckit-tasks` 生成）。

## 前提条件

- **JDK 21** 已安装（项目主开发环境，见 `docs/02-开发环境搭建.md`）
- **Maven Wrapper**（`./mvnw` 或 `mvnw.cmd`）可用
- **PowerShell**（Windows，运行 `scripts/e2e/*.ps1`）或 **Bash**（Linux，运行 `*.sh`）
- **网络可达 GitHub Releases**（首次下载 AList/录播姬二进制；不可达时使用 `ALIST_LOCAL_PATH`/`DANMUJI_LOCAL_PATH` 指定本地二进制）
- **B 站直播间可用**（E2E 录播姬链路需要稳定开播的直播源；不可用时 E2E 自动 skip，详见 [research.md R5](./research.md#r5-录播姬真实直播源依赖风险与缓解)）

## 场景 1：运行单元测试（默认 profile）

**命令**：
```bash
./mvnw test
```

**预期结果**：
- 执行 `src/test/java/**/*Test.java`（现有 37 个单元测试 + 本功能不新增单元测试）
- 排除 `*IT.java` 与 `*E2ETest.java`
- 不启动外部依赖
- 完成时间 < 60 秒
- 全部测试通过

**验证点**：常规开发反馈未被本功能污染（FR-005）。

## 场景 2：运行集成测试（integration profile）

**命令**：
```bash
./mvnw verify -Pintegration
```

**预期结果**：
- 执行 `src/test/java/**/*IT.java`：
  - 6 个 Repository 集成测试（`*RepositoryIT.java`，`@DataJpaTest`，H2 内存）
  - 1 个 AList 客户端契约测试（`AListStorageStrategyIT.java`，WireMock 桩）
- 不启动外部二进制（H2 内存 + WireMock 进程内）
- 完成时间 < 120 秒
- 全部测试通过

**验证点**：
- Repository 层乐观锁、事务回滚、幂等去重等数据完整性约束真实生效（原则 II、V）
- AList 客户端 9 个公共方法符合 `md/alist/` 契约（含错误场景与重试）

## 场景 3：准备 E2E 环境

**命令**（Windows）：
```powershell
.\scripts\e2e\prepare-e2e-env.ps1
```

**预期结果**：
- 下载 AList 二进制到 `scripts/e2e/bin/alist/`（版本锁定，SHA256 校验通过）
- 下载录播姬二进制到 `scripts/e2e/bin/danmuji/`（版本锁定，SHA256 校验通过）
- 重复执行：跳过已存在下载，输出"已存在，跳过"（幂等）
- 完成时间 < 10 分钟（SC-001）

**验证点**：一键环境准备可用（用户故事 3）。

## 场景 4：运行端到端测试（e2e profile）

**命令**：
```bash
./mvnw verify -Pe2e
```

**预期结果**：
- 自动启动 AList 实例（端口 5344，`/ping` 返回 `pong`）
- 自动启动录播姬实例（WebUI 端口 2356，webhook 指向系统 `/api/webhooks/recorder`）
- 启动系统实例（`application-e2e.yaml`，独立数据目录 `./data-e2e/`）
- 执行 `src/test/java/**/*E2ETest.java`：
  - `WebhookSyncE2ETest`：链路 1 - 录播姬录制-停止-触发 FileClosed -> 系统同步到 AList
  - `ManualSyncE2ETest`：链路 2 - 手动同步任务执行
  - `TranscodeE2ETest`：链路 3 - 转码任务执行
  - `TraceIdChainE2ETest`：traceId 全链路传播 + error.log 分流 + X-Trace-Id 响应头
- 每条链路断言点 AP1-AP9 通过（见 [data-model.md §3](./data-model.md#3-链路断言点实体)）
- 单链路完成时间 < 5 分钟
- 测试后自动清理（数据库 + 文件系统 + 临时文件）
- 连续 3 次运行全部通过（SC-004）

**验证点**：
- 全链路真实可用（用户故事 1）
- traceId 全链路传播与诊断包可追溯（原则 VII、FR-006）
- 数据持久化完整与幂等去重（原则 II、FR-007）

**直播源不可用时**：`E2ETestBase` 通过 `Assumptions.assumeTrue(false)` 跳过录播姬相关测试，报告"直播源不可用，跳过"，不标记失败（R5）。

## 场景 5：停止与清理 E2E 环境

**命令**：
```powershell
.\scripts\e2e\stop-e2e-env.ps1           # 停止实例，保留数据
.\scripts\e2e\stop-e2e-env.ps1 -CleanData # 停止并清理数据目录
```

**预期结果**：
- 基于 PID 文件精准停止 AList 与录播姬进程
- 删除 PID 文件
- `-CleanData` 时清理 `scripts/e2e/data/`（不删除 `bin/` 二进制）

## 场景 6：traceId 链路诊断验证

**命令**：
```bash
# 运行 E2E 后，用诊断脚本验证 traceId 链路
./scripts/diagnose.sh --trace-id {从 E2E 响应头 X-Trace-Id 获取}
```

**预期结果**：
- 诊断脚本（009-lightweight-diagnostics 产出）收集证据包
- 输出 `diagnostics/latest/summary.md` 摘要
- 摘要中包含指定 traceId 的全链路日志（webhook 接收 -> 同步 -> 转码）
- `logs/error.log` 与 `logs/app.log` 中 ERROR 事件一致（双写验证）

**验证点**：E2E 与诊断系统协作，traceId 链路可追溯（FR-006、原则 VII）。

## 验证清单

| 场景 | 验证需求 | 成功标准 |
|------|---------|---------|
| 1 | FR-005 不污染常规测试 | `mvn test` 不触发 IT/E2E |
| 2 | FR-003/FR-004 集成测试 | 6 Repository + 1 AList 契约测试通过 |
| 3 | FR-002/FR-010 环境准备 | 二进制下载幂等，<10 分钟 |
| 4 | FR-001/FR-006/FR-007 E2E | 3 链路 + traceId 通过，幂等 3 次 |
| 5 | FR-009 状态清理 | 停止清理后无残留 |
| 6 | FR-006 诊断协作 | traceId 链路可追溯 |

## 故障排查

| 问题 | 可能原因 | 解决方案 |
|------|---------|---------|
| 下载失败 | 网络不可达 GitHub | 使用 `ALIST_LOCAL_PATH`/`DANMUJI_LOCAL_PATH` 指定本地二进制 |
| 端口冲突 | 5344/2356/8080 被占用 | `stop-e2e-env.ps1` 停止残留进程，或修改脚本 `-Port` 参数 |
| E2E 跳过 | 直播源不可用 | 检查 B 站直播间状态，或更换房间号（`application-e2e.yaml`） |
| AList 就绪超时 | 二进制损坏或配置错误 | 重新下载（`-Force`），检查 `e2e-config/alist.config.json` |
| 录播姬 webhook 未收到 | webhookUrl 配置错误 | 确认 `danmuji.config.toml` 中 webhookUrl 指向系统端口 |
| traceId 链路断裂 | MDC 未透传 | 检查 `TraceContext.runWith` 在异步边界是否正确传播 |

## 引用

- 规格文件：[spec.md](./spec.md)
- 实现计划：[plan.md](./plan.md)
- 技术研究：[research.md](./research.md)
- 数据模型与断言点：[data-model.md](./data-model.md)
- 契约：[contracts/](./contracts/)
- 章程依据：原则 V（测试不可省略）、VII（日志规范）、II（数据完整性）
