# 密码加密 EnvironmentPostProcessor 契约

**功能分支**：`005-standalone-bootstrap`
**版本**：1.0.0

## 概述

`PasswordEncryptionPostProcessor` 实现 `EnvironmentPostProcessor`，在 Spring Boot 环境准备阶段自动检测配置文件中的明文密码并使用 BCrypt 加密。

---

## SPI 注册

**文件路径**：`src/main/resources/META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor`

**内容**：
```
top.lldwb.alistmediasync.config.PasswordEncryptionPostProcessor
```

---

## 处理流程

```
postProcessEnvironment(environment, application)
    │
    ├─ 1. 读取 app.auth.password 属性
    │     ├─ 来源：application.yaml → Environment
    │     └─ 若环境变量 APP_AUTH_PASSWORD 存在，以环境变量值为准
    │
    ├─ 2. 判断值类型
    │     ├─ null / 空字符串 → 记录 WARN → 结束
    │     ├─ 以 "{bcrypt}" 开头 → 已加密 → 结束
    │     └─ 其他值 → 明文 → 继续步骤 3
    │
    ├─ 3. BCrypt 加密
    │     ├─ 使用 org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
    │     ├─ 生成 BCrypt 哈希
    │     └─ 拼接 "{bcrypt}" + 哈希
    │
    ├─ 4. 写回配置文件
    │     ├─ 读取 application.yaml 原始内容
    │     ├─ 替换 app.auth.password 行
    │     └─ 写回文件
    │     └─ 写入失败 → 记录 ERROR → 继续（使用内存中加密值）
    │
    └─ 5. 更新 Environment
          └─ 将加密后的值设置到 app.auth.password 属性
```

---

## 日志输出契约

### 明文加密成功

```
INFO  top.lldwb.alistmediasync.config.PasswordEncryptionPostProcessor
      检测到明文密码，已自动加密为 BCrypt 格式。
```

### 密码为空

```
WARN  top.lldwb.alistmediasync.config.PasswordEncryptionPostProcessor
      认证密码未设置，管理后台将无法登录。请在 config/application.yaml 中配置 app.auth.password。
```

### 写回失败

```
ERROR top.lldwb.alistmediasync.config.PasswordEncryptionPostProcessor
      密码加密失败：无法写入配置文件 [具体异常原因]。请手动生成 BCrypt 哈希值替换明文密码。
      临时加密值已加载到内存，但重启后将失效。
```

### 已加密（无日志输出）

无日志输出 — 静默跳过。

---

## 配置文件定位

| 环境 | 配置文件路径 |
|------|-------------|
| 启动包 | `$SCRIPT_DIR/config/application.yaml` |
| 开发环境 | `classpath:application.yaml` |
| Docker | 容器内 `classpath:application.yaml`（JAR 内嵌） |

**注意**：Docker 环境下配置文件在 JAR 内部，`EnvironmentPostProcessor` 无法写回（只读文件系统）。此时：
1. 内存中的值仍被加密并更新到 `Environment`
2. 日志输出："检测到明文密码，已自动加密。注意：容器环境中无法回写配置文件，请通过环境变量 APP_AUTH_PASSWORD 注入加密后的值。"
3. 应用正常启动

---

## 安全考虑

- BCrypt 使用 10 轮哈希（与 Spring Security 默认一致）
- 加密后的密码以 `{bcrypt}` 前缀标识，与 `AuthInterceptor` 中解析逻辑一致
- `EnvironmentPostProcessor` 在 `ConfigDataEnvironment` 之后执行，此时配置文件已解析完毕
- 写回操作使用 `java.nio.file.Files.write`，原子性由文件系统保证
- 若写回过程中 JVM 崩溃，`application.yaml` 可能部分写入。启动脚本检查 YAML 格式可发现损坏
