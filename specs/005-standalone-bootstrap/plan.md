# 实现计划：一体化启动包

**分支**：`005-standalone-bootstrap` | **日期**：2026-06-20 | **规格**：[spec.md](./spec.md)

**输入**：来自 `/specs/005-standalone-bootstrap/spec.md` 的功能规格

## 摘要

为 AList-Media-Sync 项目设计并实现一体化自包含启动包方案（Docker 之外的独立部署方式）。核心交付物包括：自包含启动包目录结构设计、统一入口启动脚本（`start.bat` / `start.sh`）、启动前预检查模块（Shell/PowerShell 实现）、启动后地址输出横幅（Java 端 `ApplicationReadyEvent` 增强）、配置文件明文密码自动加密（`EnvironmentPostProcessor` 实现）、一键打包构建脚本（Maven Profile + Assembly 插件）。启动包内置 JRE、可执行 JAR、前端静态资源，目标用户解压后零配置即可运行。

## 技术上下文

**语言/版本**：Java 21（LTS，虚拟线程）、Shell（POSIX sh + Windows Batch）、PowerShell（Windows 预检查）

**主要依赖**：
- Spring Boot 4.1.0（Spring Framework 7.x）— 已有
- Spring Security Crypto（BCrypt 密码哈希）— 已有
- Lombok、Jackson、Jakarta Validation — 已有
- Maven Wrapper（`.mvn/wrapper/`）— 已有
- Adoptium JRE（Temurin 21）— 打包时下载

**存储**：H2 内嵌数据库（文件模式，默认路径 `data/`，通过 `DATA_DIR` 环境变量配置）

**测试**：Spring Boot Test（`@WebMvcTest`、`@DataJpaTest`）+ Shell 脚本手动测试

**目标平台**：Windows 10/11（x86-64）+ Linux（x86-64, kernel 5.x+）；启动包分发格式 `.zip`（Windows）和 `.tar.gz`（Linux）

**项目类型**：单体 Web 服务（自包含独立部署包）

**性能目标**：
- 启动包压缩后大小 ≤ 150MB，解压后 ≤ 400MB
- 从解压到服务启动完成（`ApplicationReadyEvent` 触发）≤ 3 分钟
- 密码加密在启动后 5 秒内完成
- 打包构建时间 ≤ 5 分钟（依赖缓存有效的前提下）

**约束**：
- 启动后不写入系统目录、注册表或用户目录
- 所有路径使用引号包裹以支持含空格的路径
- 不做后台运行/服务化（v1 聚焦前台交互式启动）
- 预检查逻辑用 Shell/PowerShell 实现，不引入额外脚本语言运行时
- JRE 通过 Adoptium API 获取（允许重新分发）
- 打包复用现有 Maven Wrapper，不强制安装特定版本 Maven

**规模/范围**：
- 新增文件：`start.bat`、`start.sh`、`mvnw-bootstrap`（原型打包脚本）、Maven Assembly 描述符、`src/main/java/` 下 ~5 个新 Java 类
- 修改文件：`pom.xml`（添加 bootstrap profile）、`application.yaml`（注释微调）、`ServerAddressLogger.java`（增强输出）
- 修改文件（Docker 体验增强）：`ServerAddressLogger.java` 增加容器环境检测及提示

## 章程检查

*门禁：必须在阶段 0 研究之前通过。在阶段 1 设计后重新检查。*

| 章程原则 | 适用性 | 状态 |
|---------|--------|------|
| I. 分层架构（不可协商） | 适用 — 密码加密逻辑放在 `EnvironmentPostProcessor`（配置层），地址输出增强 `ServerAddressLogger`（Util 组件），符合分层惯例 | ✅ 通过 |
| II. 数据完整性优先 | 间接相关 — 密码加密回写操作需处理写入失败（文件锁定、权限等边界情况） | ✅ 通过 |
| III. RESTful API 契约优先 | 不适用 — 本功能不引入新 API 端点 | ✅ 通过 |
| IV. 中文优先 | 适用 — 启动脚本注释、预检查提示信息、密码加密日志、地址横幅输出均使用简体中文 | ✅ 通过 |
| V. 测试不可省略 | 适用 — 密码加密逻辑需要单元测试（`EnvironmentPostProcessor`），地址输出增强需要单元测试（`ServerAddressLogger`） | ✅ 通过 |
| VI. 简洁至上（YAGNI） | 适用 — 密码加密复用现有 `spring-security-crypto` 依赖（不做重复引入），启动脚本仅用 Shell/PowerShell（不引入 Python 等额外运行时），地址输出复用 `ServerAddressLogger` 组件 | ✅ 通过 |

**门禁结果**：全部通过，无违规项，无需记录复杂性追踪。

## 项目结构

### 文档（本功能）

```text
specs/005-standalone-bootstrap/
├── plan.md              # 本文件（/speckit-plan 命令输出）
├── research.md          # 阶段 0 输出
├── data-model.md        # 阶段 1 输出
├── quickstart.md        # 阶段 1 输出
├── contracts/           # 阶段 1 输出
└── tasks.md             # 阶段 2 输出（/speckit-tasks 命令创建）
```

### 源代码（仓库根目录）

```text
.
├── scripts/
│   ├── start.bat                  # 新增：Windows 启动脚本（源码，打包后复制到根目录）
│   └── start.sh                   # 新增：Linux/Mac 启动脚本（源码，打包后复制到根目录）
├── src/
│   └── main/
│       ├── java/top/lldwb/alistmediasync/
│       │   ├── AListMediaSyncApplication.java   # 可能修改（无需大改）
│       │   ├── config/
│       │   │   ├── AppProperties.java            # 修改：支持明文密码检测
│       │   │   └── PasswordEncryptionPostProcessor.java  # 新增：密码自动加密（实现 EnvironmentPostProcessor 接口）
│       │   └── util/
│       │       └── ServerAddressLogger.java      # 修改：增强横幅输出、容器环境检测
│       └── resources/
│           └── application.yaml                 # 修改：注释完善
├── assembly/
│   └── bootstrap.xml              # 新增：Maven Assembly 描述符（定义启动包目录结构）
├── pom.xml                        # 修改：添加 bootstrap profile、Maven Assembly 插件
└── Dockerfile                     # 修改：容器环境变量标记
```

**结构决策**：启动脚本放置在项目根目录（标准惯例，与 `mvnw` 同级）。打包描述符放在 `assembly/` 子目录以保持根目录整洁。遵循"选项 1：单项目"布局，无需多层目录结构。

## 复杂性追踪

> 无违规项，无需记录。

