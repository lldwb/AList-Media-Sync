# 启动脚本契约

**功能分支**：`005-standalone-bootstrap`
**版本**：1.0.0

## 概述

启动脚本（`start.bat` / `start.sh`）是用户与一体化启动包交互的入口点。本文件定义启动脚本的命令行契约。

---

## start.sh（Linux/Mac）

### 语法

```bash
./start.sh [选项]
sh start.sh [选项]
```

### 支持方式

| 执行方式 | 说明 |
|---------|------|
| `./start.sh` | 依赖文件系统的可执行权限 |
| `sh start.sh` | 不依赖可执行权限，`sh` 解释器直接执行 |
| `bash start.sh` | 使用 bash 解释器执行 |

### 路径解析

启动脚本通过自身位置定位启动包根目录：

```bash
SCRIPT_DIR="$(cd "$(dirname "$(readlink -f "$0")")" && pwd)"
```

`readlink -f` 解析符号链接，`dirname` 获取脚本所在目录，`cd` 后 `pwd` 获取绝对路径。

### 环境变量

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| `SERVER_PORT` | 8080 | 应用监听端口 |
| `DATA_DIR` | `./data`（相对于启动包根目录） | 数据目录路径 |
| `LOGGING_LEVEL` | INFO | 日志级别 |
| `JAVA_OPTS` | `-Xms128m -Xmx256m` | JVM 额外参数 |
| `DISK_SPACE_THRESHOLD` | 100 | 磁盘空间警告阈值（MB） |
| `APP_AUTH_PASSWORD` | （无） | 覆盖配置文件中的认证密码 |

### 启动流程

1. 定位启动包根目录（`$SCRIPT_DIR`）
2. 检查 JRE 可执行文件存在（`$SCRIPT_DIR/runtime/bin/java`）
3. 检查配置文件存在（`$SCRIPT_DIR/config/application.yaml`）
4. 检测端口占用（`ss -tlnp`）
5. 检测磁盘空间（`df`）
6. 检测已有实例（PID 文件 + `kill -0`）
7. 所有检查通过 → 启动 JVM
8. 写入 PID 文件 → 前台运行 → Ctrl+C / `kill` 优雅退出

### 退出码

| 退出码 | 含义 |
|--------|------|
| 0 | 正常退出 |
| 1 | 预检查失败：环境不满足（JRE 缺失、配置文件缺失、端口占用） |
| 2 | 用户拒绝继续（磁盘空间不足时的 [n] 选择） |
| 130 | 收到 SIGINT（Ctrl+C） |
| 143 | 收到 SIGTERM |

---

## start.bat（Windows）

### 语法

```batch
start.bat
:: 或双击执行
```

### 路径解析

```batch
set "SCRIPT_DIR=%~dp0"
:: 去掉尾部反斜杠
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"
```

`%~dp0` 在 Windows Batch 中获取脚本所在驱动器号和路径。

### 环境变量

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| `SERVER_PORT` | 8080 | 应用监听端口 |
| `DATA_DIR` | `.\data`（相对于启动包根目录） | 数据目录路径 |
| `LOGGING_LEVEL` | INFO | 日志级别 |
| `JAVA_OPTS` | `-Xms128m -Xmx256m` | JVM 额外参数 |
| `DISK_SPACE_THRESHOLD` | 100 | 磁盘空间警告阈值（MB） |

### 启动流程

同 `start.sh`，但使用 Windows 对应命令：
- JRE 路径：`%SCRIPT_DIR%\runtime\bin\java.exe`
- 端口检测：`netstat -ano | findstr`
- 磁盘检测：`wmic logicaldisk`
- PID 检测：`tasklist`

### 退出码

| 退出码 | 含义 |
|--------|------|
| 0 | 正常退出 |
| 1 | 预检查失败 |

---

## 预检查错误输出契约

所有预检查失败时向 stderr 输出以下格式：

```
[错误] <中文错误描述>
建议：<修复建议>
```

**示例**：
```
[错误] 端口 8080 已被进程 java (PID: 12345) 占用。
建议：1) 修改 config/application.yaml 中的 server.port 配置项；
      2) 或终止占用进程后重试（Linux: kill 12345，Windows: taskkill /PID 12345）。
```
