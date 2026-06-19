# 打包构建契约

**功能分支**：`005-standalone-bootstrap`
**版本**：1.0.0

## 概述

定义一体化启动包的自动化打包构建流程和产物规范。

---

## 构建命令

### 完整打包（含前端构建 + JRE 下载）

```bash
./mvnw package -P bootstrap
```

### 仅后端打包（跳过前端构建，使用已有前端产物）

```bash
./mvnw package -P bootstrap -Dskip.frontend=true
```

### 仅打包不下载 JRE（使用本地 JRE）

```bash
./mvnw package -P bootstrap -Djre.local.path=/path/to/jre
```

---

## Maven Profile：`bootstrap`

### 激活条件

- 显式指定 `-P bootstrap`
- 或属性 `bootstrap.build=true`

### 生命周期绑定

| 阶段 | 插件/目标 | 说明 |
|------|----------|------|
| `validate` | `exec-maven-plugin:exec` | 检查 Node.js 可用性（前端构建前置条件） |
| `compile` | `exec-maven-plugin:exec` | 执行 `npm run build`（若 `skip.frontend=false`） |
| `package` | `spring-boot-maven-plugin:repackage` | 生成可执行 JAR |
| `package` | `exec-maven-plugin:exec` | 下载 JRE（若 `jre.local.path` 未设置） |
| `package` | `maven-assembly-plugin:single` | 组装启动包目录结构并打包为 zip/tar.gz |
| `verify` | `exec-maven-plugin:exec` | 完整性校验（解压产物，验证关键文件存在） |

---

## JRE 下载契约

### Adoptium API 调用

```
GET https://api.adoptium.net/v3/binary/latest/21/ga/{os}/{arch}/jre/hotspot/normal/eclipse
```

| 参数 | Windows 值 | Linux 值 |
|------|-----------|----------|
| `{os}` | `windows` | `linux` |
| `{arch}` | `x64` | `x64` |

### 下载后处理

1. 解压到 `${project.build.directory}/runtime/`
2. 校验 SHA-256（通过 `.sha256.txt` 文件）
3. 移除不必要的文件（`src.zip`、`man/` 目录等）以减小体积

---

## Assembly Descriptor 契约

### 文件：`assembly/bootstrap.xml`

### 输出格式

| 格式 | 文件名 | 目标平台 |
|------|--------|---------|
| `zip` | `alist-media-sync-{version}-windows-x64.zip` | Windows |
| `tar.gz` | `alist-media-sync-{version}-linux-x64.tar.gz` | Linux |

### 目录结构映射

| 源路径 | 目标路径 | 说明 |
|--------|---------|------|
| `scripts/start.sh` | `start.sh` | Linux 启动脚本（fileMode=0755） |
| `scripts/start.bat` | `start.bat` | Windows 启动脚本 |
| `${project.build.directory}/runtime/` | `runtime/` | JRE 运行时 |
| `${project.build.directory}/*.jar` | `lib/` | 可执行 JAR |
| `src/main/resources/application.yaml` | `config/application.yaml` | 默认配置 |
| `src/main/resources/application.template.yaml` | `config/application.template.yaml` | 配置模板 |
| （空目录） | `data/` | 数据目录（运行时创建） |
| （空目录） | `logs/` | 日志目录（运行时创建） |

---

## 完整性校验契约

打包完成后执行以下校验：

1. 解压产物到临时目录
2. 验证以下文件存在：
   - `start.sh` / `start.bat`
   - `runtime/bin/java`（Linux）/ `runtime/bin/java.exe`（Windows）
   - `lib/*.jar`（至少一个 JAR 文件）
   - `config/application.yaml`
3. 验证 JAR 文件可被 JRE 识别：`runtime/bin/java -jar lib/*.jar --version`（预期输出 Spring Boot 版本信息后退出）
4. 验证通过 → 输出打包摘要
5. 验证失败 → 构建失败，输出缺失文件列表

---

## 打包摘要输出

```
========================================
  打包完成
========================================
  产物：
    alist-media-sync-0.0.1-SNAPSHOT-windows-x64.zip  (XX MB)
    alist-media-sync-0.0.1-SNAPSHOT-linux-x64.tar.gz  (XX MB)
  包含组件：
    JRE: Eclipse Temurin 21.x (JRE)
    应用: AList-Media-Sync 0.0.1-SNAPSHOT
    前端: React 19 + Vite 6
  完整性校验：通过 ✓
========================================
```
