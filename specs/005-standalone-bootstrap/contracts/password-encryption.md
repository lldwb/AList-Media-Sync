# 密码加密 EnvironmentPostProcessor 契约

**功能分支**：`005-standalone-bootstrap`
**版本**：1.1.0

## 概述

`PasswordEncryptionPostProcessor` 实现 `EnvironmentPostProcessor`，在 Spring Boot 环境准备阶段自动检测配置中的明文密码并使用 BCrypt 加密到内存。加密后的密码仅保存在内存中的 Spring Environment 内，**绝不回写到 YAML 文件**。每次启动均重新执行检测与加密流程。

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
    └─ 4. 更新 Environment（仅内存，不回写文件）
          └─ 将加密后的值设置到 app.auth.password 属性
          └─ 配置文件中的原始值保持不变
```

**关键设计决策**：加密值仅保存在内存中的 Spring Environment，绝不回写到 YAML 文件。原因：
1. 环境变量值并不存在于 YAML 文件中，统一行为避免二义性
2. 每次启动均重新检测并加密，保证流程一致性和可预测性
3. 配置文件始终保持人类可读的明文形式，运维人员可随时修改

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

### 已加密（无日志输出）

无日志输出 — 静默跳过。

---

## 配置文件定位

| 环境 | 配置文件路径 |
|------|-------------|
| 启动包 | `$SCRIPT_DIR/config/application.yaml` |
| 开发环境 | `classpath:application.yaml` |
| Docker | 容器内 `classpath:application.yaml`（JAR 内嵌） |

**注意**：所有环境下加密值均仅保存在内存中的 Spring Environment，绝不回写配置文件。Docker 环境下同样适用——若通过环境变量 `APP_AUTH_PASSWORD` 传入明文密码，每次启动均自动加密到内存。

---

## 安全考虑

- BCrypt 使用 10 轮哈希（与 Spring Security 默认一致）
- 加密后的密码以 `{bcrypt}` 前缀标识，与 `AuthInterceptor` 中解析逻辑一致
- `EnvironmentPostProcessor` 在 `ConfigDataEnvironment` 之后执行，此时配置文件已解析完毕
- 加密值仅存在于内存中，不会持久化到任何文件
- 每次启动均重新执行检测与加密流程，确保运行时态密码始终为 BCrypt 格式
