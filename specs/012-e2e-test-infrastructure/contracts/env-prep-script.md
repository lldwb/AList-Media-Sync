# 契约：E2E 环境准备脚本

**功能**：`012-e2e-test-infrastructure` | **用途**：定义一键下载与启动 AList/录播姬二进制的脚本接口契约

本文档定义 `scripts/e2e/` 下脚本的命令接口、参数、行为与退出码，实现 FR-002、FR-010、FR-014（动态端口）、用户故事 3（一键准备环境）。

## 脚本清单

| 脚本 | 平台 | 职责 |
|------|------|------|
| `prepare-e2e-env.ps1` | Windows（主） | 下载 AList 二进制到 `scripts/e2e/bin/`（录播姬可选，`-IncludeDanmuji` 触发），SHA256 校验，幂等 |
| `prepare-e2e-env.sh` | Linux（备） | 同上，Bash 实现 |
| `start-alist.ps1` | Windows | 启动 AList 实例（端口动态分配 FR-014），等待 `/ping` 就绪 |
| `start-danmuji.ps1` | Windows | 启动录播姬实例（可选验证路径），端口动态分配，等待 WebUI 就绪 |
| `stop-e2e-env.ps1` | Windows | 停止 AList + 录播姬（如启动）实例，基于 PID 文件精准停止 |

## prepare-e2e-env 脚本契约

**命令**：
```powershell
.\scripts\e2e\prepare-e2e-env.ps1 [-Force] [-SkipAlist] [-IncludeDanmuji]
```

**参数**：

| 参数 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `-Force` | switch | false | 强制重新下载，覆盖已存在二进制 |
| `-SkipAlist` | switch | false | 跳过 AList 下载 |
| `-IncludeDanmuji` | switch | false | 下载录播姬二进制（默认跳过，仅可选验证路径需要，FR-011） |

**环境变量（跳过下载）**：

| 变量 | 说明 |
|------|------|
| `ALIST_LOCAL_PATH` | 指定本地已有 AList 二进制路径，跳过下载（对齐 005 的 `-Djre.local.path` 模式） |
| `DANMUJI_LOCAL_PATH` | 指定本地已有录播姬二进制路径，跳过下载（仅 `-IncludeDanmuji` 时生效） |

**行为**：
1. 检测目标路径 `scripts/e2e/bin/alist/` 是否已存在可执行文件；录播姬仅当 `-IncludeDanmuji` 时检测 `scripts/e2e/bin/danmuji/`
2. 已存在且未指定 `-Force`：跳过下载，输出"已存在，跳过"（幂等，FR-009）
3. 不存在或 `-Force`：从 GitHub Releases 下载（版本锁定于脚本常量）
4. SHA256 校验：哈希值锁定于脚本常量，不符则删除文件并报错退出
5. 解压到目标路径
6. 输出下载摘要：版本、路径、校验结果

**退出码**：

| 码 | 含义 |
|----|------|
| 0 | 成功（下载或跳过） |
| 1 | 下载失败（网络错误、校验失败） |
| 2 | 解压失败 |

**版本锁定**：脚本顶部常量定义：
```powershell
$ALIST_VERSION = "v3.x.x"      # 实现时确定当前稳定版
$ALIST_SHA256_WIN = "..."      # Windows 二进制哈希
$DANMUJI_VERSION = "v2.x.x"    # 实现时确定当前稳定版
$DANMUJI_SHA256_WIN = "..."
```

## start-alist 脚本契约

**命令**：
```powershell
.\scripts\e2e\start-alist.ps1 -Port <动态端口> [-DataDir "scripts/e2e/data/alist"]
```

**参数**：

| 参数 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `-Port` | int | 必填 | AList HTTP 端口，由 `E2ELifecycleManager` 动态探测空闲端口后传入（FR-014，禁止固定端口） |
| `-DataDir` | string | `scripts/e2e/data/alist` | AList 数据目录 |

**行为**：
1. 接收调用方动态分配的 `-Port`（FR-014），不自行假设固定端口
2. 启动 `alist.exe server`（后台进程），绑定到指定端口，写入 PID 到 `alist.pid`
3. 轮询 `http://localhost:{Port}/ping`，30 秒内返回 `pong` 视为就绪
4. 初始化存储挂载（通过 AList API 或配置文件，挂载 `/e2e-test` 到本地目录）
5. 输出就绪摘要：端口、数据目录、PID

**退出码**：0 成功 / 1 启动失败 / 3 就绪超时

## start-danmuji 脚本契约（可选验证路径，FR-011）

> 录播姬实例仅当需验证真实录播姬 webhook 格式时启动（`-Ddanmuji.enabled=true`），默认 E2E 运行不调用此脚本，事件由 `WebhookEventReplayer` 重放 fixtures 驱动。

**命令**：
```powershell
.\scripts\e2e\start-danmuji.ps1 -WebuiPort <动态端口> [-WorkDir "scripts/e2e/data/danmuji"] [-WebhookUrl "http://localhost:{系统动态端口}/api/webhooks/recorder"]
```

**行为**：
1. 接收调用方动态分配的 `-WebuiPort`（FR-014）
2. 生成录播姬配置文件（基于 `scripts/e2e/e2e-config/danmuji.config.toml` 模板，注入 webhookUrl 与 workDir）
3. 启动 `BililiveRecorder.exe run`（后台进程），写入 PID 到 `danmuji.pid`
4. 轮询 WebUI（`http://localhost:{WebuiPort}`），30 秒内响应 200 视为就绪
5. 输出就绪摘要：WebUI 端口、工作目录、webhook URL、PID

**退出码**：0 成功 / 1 启动失败 / 3 就绪超时

## stop-e2e-env 脚本契约

**命令**：
```powershell
.\scripts\e2e\stop-e2e-env.ps1 [-CleanData]
```

**参数**：
| 参数 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `-CleanData` | switch | false | 停止后清理数据目录（`scripts/e2e/data/`），用于完全重置 |

**行为**：
1. 读取 `alist.pid` 停止 AList 进程；读取 `danmuji.pid`（若存在，录播姬可选启动）停止录播姬进程
2. PID 文件不存在：尝试按 `ports.json` 记录的动态端口查找进程并停止（输出警告"PID 文件缺失，按端口停止"）
3. 删除 PID 文件与 `ports.json`
4. `-CleanData` 时：删除 `scripts/e2e/data/` 目录（**不删除** `scripts/e2e/bin/` 二进制）
5. 输出停止摘要

**退出码**：0 成功 / 1 进程停止失败

## 配置模板

`scripts/e2e/e2e-config/` 存放外部依赖配置模板，纳入版本控制：

| 文件 | 说明 |
|------|------|
| `alist.config.json` | AList 数据目录、端口、初始存储挂载配置 |
| `danmuji.config.toml` | 录播姬工作目录、WebUI 端口、webhook URL 配置 |

## .gitignore 规则

`scripts/e2e/bin/` 与 `scripts/e2e/data/` 必须加入 `.gitignore`（二进制与运行时数据不纳入版本控制）；`scripts/e2e/e2e-config/` 与脚本本身纳入版本控制。

## 幂等性保证（FR-009）

- 重复执行 `prepare-e2e-env.ps1`：已存在则跳过，不重复下载
- 重复执行 `start-alist.ps1`：检测到运行中实例则报告"已在运行"，不重复启动
- `stop-e2e-env.ps1` 后再 `start`：干净重启

## 引用

- 技术决策：[research.md R3/R4/R8](../research.md)（R3 下载、R4 重放驱动、R8 动态端口）
- 章程依据：FR-002、FR-009、FR-010、FR-011、FR-014
- 对齐模式：`specs/005-standalone-bootstrap` 的 `download-jre.sh`（幂等下载、本地路径跳过）
- 诊断脚本复用：`scripts/diagnose.{sh,bat}`（009-lightweight-diagnostics）
