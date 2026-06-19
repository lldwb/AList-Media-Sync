# 数据模型：一体化启动包

**功能分支**：`005-standalone-bootstrap`
**日期**：2026-06-20

## 概述

本功能主要涉及基础设施/部署层面的变更，不引入新的数据库实体。以下数据模型聚焦于配置结构、启动包目录结构和启动脚本的状态模型。

---

## 实体 1：启动包目录结构

启动包解压后的文件系统布局。

| 路径 | 类型 | 说明 |
|------|------|------|
| `start.bat` | 文件 | Windows 启动脚本（CRLF 换行） |
| `start.sh` | 文件 | Linux/Mac 启动脚本（LF 换行，可执行权限） |
| `runtime/` | 目录 | JRE 运行时（Adoptium Temurin 21） |
| `runtime/bin/java` (Linux) / `runtime/bin/java.exe` (Windows) | 文件 | JRE 可执行文件 |
| `lib/` | 目录 | Spring Boot 可执行 JAR |
| `lib/alist-media-sync-*.jar` | 文件 | 应用 JAR（含内嵌 Tomcat 和所有依赖） |
| `config/` | 目录 | 配置文件 |
| `config/application.yaml` | 文件 | 应用配置（含 `app.auth.password`） |
| `config/application.template.yaml` | 文件 | 配置模板（注释完整，用于恢复） |
| `data/` | 目录 | 运行时数据（H2 数据库文件、PID 文件） |
| `logs/` | 目录 | 应用日志输出 |

**约束**：
- 所有路径相对于启动包根目录
- `data/` 和 `logs/` 目录由启动脚本在首次启动时自动创建
- 启动脚本通过自身路径定位根目录（`$SCRIPT_DIR`）

---

## 实体 2：启动脚本状态机

启动脚本的执行状态转换。

```
[用户执行 start.bat/start.sh]
         │
         ▼
   ┌─────────────┐
   │ 环境检测     │ ◄── JRE 存在？配置文件存在？
   │ (PRE_CHECK)  │
   └──────┬──────┘
          │
    ┌─────┴─────┐
    │ 全部通过   │ 任一失败 ──► 输出中文错误 → 退出 (exit 1)
    └─────┬─────┘
          ▼
   ┌─────────────┐
   │ 端口检测     │ ◄── 端口被占用？
   │ (PORT_CHECK) │
   └──────┬──────┘
          │
    ┌─────┴─────┐
    │ 端口空闲   │ 被占用 ──► 输出占用信息 → 退出 (exit 1)
    └─────┬─────┘
          ▼
   ┌─────────────┐
   │ 磁盘检测     │ ◄── 剩余空间 < 阈值？
   │ (DISK_CHECK) │
   └──────┬──────┘
          │
    ┌─────┴─────┐
    │ 空间充足   │ 不足 ──► 交互模式：询问 [y/N]
    └─────┬─────┘        非交互模式：退出 (exit 1)
          ▼
   ┌─────────────┐
   │ 实例检测     │ ◄── PID 文件存在且进程存活？
   │ (INST_CHECK) │
   └──────┬──────┘
          │
    ┌─────┴─────┐
    │ 无已有实例 │ 已有实例 ──► 询问是否强制重启
    └─────┬─────┘
          ▼
   ┌─────────────┐
   │ 启动 JVM     │
   │ (LAUNCH)     │
   └──────┬──────┘
          ▼
   ┌─────────────┐
   │ 运行中       │ ◄── Java 进程前台运行
   │ (RUNNING)    │     Ctrl+C / SIGTERM → 优雅关闭
   └─────────────┘
```

---

## 实体 3：密码加密状态转换

`app.auth.password` 配置值的状态转换（加密仅作用于内存中的 Spring Environment，配置文件中的值保持不变）。

```
[配置文件中的 app.auth.password 值]（始终为用户写入的原始值，不会被修改）
         │
         ▼
   ┌──────────────────┐
   │ 空值 / null       │ ──► 日志警告 → 跳过加密 → 应用启动
   └──────────────────┘     （认证拦截器生效，管理 API 不可访问）

   ┌──────────────────┐
   │ 明文值             │ ──► BCrypt 加密 → 更新内存 Environment
   │ (如 "admin123")   │     日志："检测到明文密码，已自动加密为 BCrypt 格式"
   └──────────────────┘     → 应用启动（配置文件中的值不变）

   ┌──────────────────┐
   │ {bcrypt} 前缀值    │ ──► 已加密 → 跳过 → 应用启动
   │ (如 "{bcrypt}$2a$ │     （无额外日志，配置文件中的值不变）
   │  10$...")         │
   └──────────────────┘
```

**核心原则**：每次启动均重新执行检测与加密流程。加密后的密码仅保存在内存中的 Spring Environment 内，**绝不回写到 YAML 文件**。配置文件始终保持用户写入的原始值（明文或已加密格式均可）。

**边界情况**：
- 环境变量 `APP_AUTH_PASSWORD` 覆盖 → 加密逻辑同样对覆盖后的值生效，加密到内存 Environment
- 配置文件 YAML 格式损坏 → `EnvironmentPostProcessor` 中捕获 `YAMLException` → 日志错误 → 应用继续启动（使用默认值或环境变量值）

---

## 实体 4：地址输出数据模型

`ServerAddressLogger` 输出的地址信息结构。

| 字段 | 类型 | 说明 |
|------|------|------|
| `urls` | `List<String>` | 有序 URL 列表（localhost → 127.0.0.1 → 网络接口地址） |
| `port` | `int` | 服务监听端口（从 `WebServerApplicationContext` 获取） |
| `contextPath` | `String` | 上下文路径（默认空字符串） |
| `appName` | `String` | 应用名称（从 `spring.application.name` 获取） |
| `appVersion` | `String` | 应用版本（从 `Implementation-Version` manifest 获取） |
| `isContainer` | `boolean` | 是否运行在容器环境 |
| `functionalPaths` | `Map<String, String>` | 功能路径映射（管理界面 `/app/`、API `/api/`、健康检查 `/actuator/health`、H2 控制台 `/h2-console`） |

**输出格式示例**（非容器环境）：
```
========================================
  AList-Media-Sync v0.0.1-SNAPSHOT
  服务启动成功！
========================================
  可访问地址：
  [1] http://localhost:8080/app/
  [2] http://127.0.0.1:8080/app/
  [3] http://192.168.1.100:8080/app/ (eth0)
========================================
  主要功能路径：
  管理界面    http://localhost:8080/app/
  API 根路径  http://localhost:8080/api/
  健康检查    http://localhost:8080/actuator/health
  H2 控制台   http://localhost:8080/h2-console
========================================
```

**输出格式示例**（容器环境）：
```
========================================
  AList-Media-Sync v0.0.1-SNAPSHOT
  服务启动成功！（容器环境）
========================================
  容器内部地址：
  [1] http://localhost:8080/app/（容器内部端口）
========================================
  主要功能路径：
  管理界面    http://localhost:8080/app/
  API 根路径  http://localhost:8080/api/
  健康检查    http://localhost:8080/actuator/health
  H2 控制台   http://localhost:8080/h2-console
========================================
  注意：容器内部端口 8080 可能映射到宿主机不同端口，
  请参阅 docker-compose.yml 中的 ports 配置。
========================================
```

---

## 实体 5：打包构建产物

打包脚本生成的最终产物结构。

| 属性 | 值 |
|------|-----|
| Windows 产物 | `alist-media-sync-{version}-windows-x64.zip` |
| Linux 产物 | `alist-media-sync-{version}-linux-x64.tar.gz` |
| 压缩后大小 | ≤ 150MB |
| 解压后大小 | ≤ 400MB |
| 包含 JRE 版本 | Eclipse Temurin 21.x (JRE) |
| 包含 JAR | Spring Boot 可执行 JAR（含前端静态资源） |
| 配置文件 | `application.yaml`（含注释） |
| 模板文件 | `application.template.yaml` |

---

## 总结

本功能不引入新的数据库实体。核心数据模型围绕文件系统布局、状态转换和配置结构展开。所有模型均与现有项目结构兼容，不破坏已有功能。
