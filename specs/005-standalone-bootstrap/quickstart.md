# 快速入门验证指南：一体化启动包

**功能分支**：`005-standalone-bootstrap`
**日期**：2026-06-20

## 概述

本指南列出可运行的验证场景，证明一体化启动包端到端可用。

---

## 前提条件

### 开发者环境（打包构建）

- Java 21 + Maven Wrapper（项目已自带）
- Node.js 22+（仅前端构建需要，若无则 `-Dskip.frontend=true`）
- 网络连接（首次构建需下载 JRE，约 40MB）

### 用户环境（运行启动包）

- **Windows 10/11 x86-64** 或 **Linux x86-64 (kernel 5.x+)**
- 无需预装 Java、Node.js、Maven 等任何开发工具
- 启动包所在磁盘有 ≥ 500MB 空闲空间
- 端口 8080 未被占用（或修改配置使用其他端口）

---

## 场景 1：开发者打包构建

### 步骤

```bash
# 1. 进入项目根目录
cd AList-Media-Sync

# 2. 执行完整打包（含前端构建 + JRE 下载）
./mvnw package -P bootstrap

# 3. 验证产物
ls -lh target/alist-media-sync-*-standalone-*.zip
ls -lh target/alist-media-sync-*-standalone-*.tar.gz
```

### 预期结果

- 构建成功，退出码 0
- 生成两个归档文件（.zip 和 .tar.gz）
- 控制台输出打包摘要，包含文件大小和完整性校验结果
- 压缩后每个文件 ≤ 150MB

---

## 场景 2：开箱即用首次启动（Linux）

### 步骤

```bash
# 1. 解压启动包
tar xzf alist-media-sync-*-linux-x64.tar.gz
cd alist-media-sync

# 2. 执行启动脚本
sh start.sh

# 3. 等待启动完成（观察控制台输出）
```

### 预期结果

- 预检查全部通过（无错误输出）
- 约 15-30 秒后输出启动成功横幅
- 横幅包含：
  - 应用名称和版本
  - `http://localhost:8080/app/` 和 `http://127.0.0.1:8080/app/`
  - 网络接口地址（如有）
  - 主要功能路径（`/app/`、`/api/`、`/actuator/health`、`/h2-console`）
- `curl http://localhost:8080/actuator/health` 返回 `{"status":"UP"}`
- 浏览器访问 `http://localhost:8080/app/` 显示管理界面

---

## 场景 3：自定义端口启动

### 步骤

```bash
# 方式 1：通过环境变量
SERVER_PORT=9090 sh start.sh

# 方式 2：修改配置文件
# 编辑 config/application.yaml，将 server.port 改为 9090，然后执行 sh start.sh
```

### 预期结果

- 服务监听在 9090 端口
- 启动横幅显示 `http://localhost:9090/app/`
- `curl http://localhost:9090/actuator/health` 返回 UP

---

## 场景 4：启动错误检测 — 端口占用

### 步骤

```bash
# 1. 首先启动一个占用 8080 端口的进程（另一个终端）
python3 -m http.server 8080 &

# 2. 尝试启动 AList-Media-Sync
sh start.sh
```

### 预期结果

- 启动脚本检测到端口占用
- 输出中文错误：
  ```
  [错误] 端口 8080 已被进程 python3 (PID: xxxx) 占用。
  建议：1) 修改 config/application.yaml 中的 server.port 配置项；
        2) 或终止占用进程后重试。
  ```
- 应用不启动（退出码 1）

---

## 场景 5：启动错误检测 — 配置文件缺失

### 步骤

```bash
# 1. 删除/重命名配置文件
mv config/application.yaml config/application.yaml.bak

# 2. 尝试启动
sh start.sh
```

### 预期结果

- 启动脚本检测到配置文件缺失
- 输出中文错误：
  ```
  [错误] 配置文件 config/application.yaml 不存在或格式错误。
  建议：请检查 config/ 目录下的配置文件是否完整，可从模板文件 config/application.template.yaml 复制一份并修改。
  ```
- 应用不启动

---

## 场景 6：密码明文自动加密

### 步骤

```bash
# 1. 修改配置文件，设置明文密码
# 在 config/application.yaml 中修改 app.auth.password 为 "test123"

# 2. 启动应用
sh start.sh

# 3. 观察日志输出

# 4. 再次查看配置文件
cat config/application.yaml | grep password

# 5. 重启应用，确认不再输出加密日志
sh start.sh
```

### 预期结果

- 首次启动日志输出："检测到明文密码，已自动加密为 BCrypt 格式"
- 配置文件中的 `password` 值变为 `"{bcrypt}$2a$10$..."`
- 使用 `test123` 可通过 HTTP Basic 认证登录管理界面
- 第二次启动不再输出加密相关日志

---

## 场景 7：密码未设置警告

### 步骤

```bash
# 1. 将 app.auth.password 设为空值或删除该行
# 2. 启动应用
sh start.sh
```

### 预期结果

- 日志输出警告："认证密码未设置，管理后台将无法登录。请在 config/application.yaml 中配置 app.auth.password。"
- 服务仍正常启动，但管理 API 因无有效凭据而不可访问
- `curl -u admin:test http://localhost:8080/api/engines` 返回 401

---

## 场景 8：Docker 容器地址输出

### 步骤

```bash
# 1. 构建 Docker 镜像
docker build -t alist-media-sync:local .

# 2. 启动容器
docker run --rm -p 9090:8080 -e ALIST_BASE_URL=https://example.com -e ALIST_TOKEN=test alist-media-sync:local

# 3. 查看日志
docker logs <container_name>
```

### 预期结果

- 日志输出包含容器环境标识
- 地址列表注明"容器内部端口"
- 末尾提示"容器内部端口 8080 可能映射到宿主机不同端口"

---

## 场景 9：Windows 启动（start.bat）

### 步骤

```cmd
# 1. 解压 alist-media-sync-*-windows-x64.zip
# 2. 双击 start.bat 或命令行执行
start.bat
```

### 预期结果

- 预检查通过（JRE 检测、配置检测、端口检测、磁盘检测）
- 在命令行窗口中显示启动日志
- 启动成功横幅输出与 Linux 版本一致

---

## 场景 10：任意目录执行

### 步骤

```bash
# 从启动包目录以外的位置执行
/tmp$ /path/to/alist-media-sync/start.sh
```

### 预期结果

- 脚本正确解析自身位置，定位启动包根目录
- 服务正常启动，数据文件写入启动包内的 `data/` 目录（而非当前工作目录 `/tmp/data/`）
- 验证：`ls alist-media-sync/data/` 确认数据库文件和 PID 文件均在该目录下

---

## 已知限制

- 不支持 macOS（v1 范围外）
- 不支持 ARM 架构（v1 范围外）
- Windows Batch 脚本对路径中 `)` 等特殊字符有限制（建议安装路径不含特殊字符）
- 密码加密在 Docker 容器内无法回写配置文件（容器只读文件系统），需通过环境变量注入
