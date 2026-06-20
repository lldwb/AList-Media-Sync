# 密码加密 EnvironmentPostProcessor 契约

**功能分支**：`007-password-encryption-and-code-organization`
**版本**：2.0.0

## 概述

`PasswordEncryptionPostProcessor` 实现 `EnvironmentPostProcessor`，在 Spring Boot 环境准备阶段自动将配置中的明文密码使用 BCrypt 加密到内存。加密后的密码仅保存在内存中的 Spring Environment 内，**绝不回写到 YAML 文件**。每次启动均重新执行加密流程，使用随机盐值。

**v2.0 变更**：配置文件仅支持明文密码，不再识别 `{bcrypt}` 前缀。所有配置值均视为明文。

---

## SPI 注册

**文件路径**：`src/main/resources/META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor`

**内容**：
```
top.lldwb.alistmediasync.common.config.PasswordEncryptionPostProcessor
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
    │     └─ 其他值（包括含 {bcrypt} 前缀的旧格式）→ 全部视为明文 → 继续步骤 3
    │
    ├─ 3. BCrypt 加密
    │     ├─ 使用 org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder（默认构造，随机盐值）
    │     ├─ 生成 BCrypt 哈希
    │     └─ 拼接 "{bcrypt}" + 哈希
    │
    └─ 4. 更新 Environment（仅内存，不回写文件）
          └─ 将加密后的值设置到 app.auth.password 属性
          └─ 配置文件中的原始值保持不变
```

**关键设计决策**：加密值仅保存在内存中的 Spring Environment，绝不回写到 YAML 文件。原因：
1. 环境变量值并不存在于 YAML 文件中，统一行为避免二义性
2. 每次启动均重新加密，保证流程一致性和可预测性
3. 配置文件始终保持人类可读的明文形式，运维人员可随时修改

---

## 日志输出契约

### 密码为空

```
WARN  top.lldwb.alistmediasync.common.config.PasswordEncryptionPostProcessor
      认证密码未设置，管理后台将无法登录。请在 application.yaml 中配置 app.auth.password。
```

### 加密过程（静默）

无日志输出 — 加密过程完全静默，不输出任何 INFO 日志。

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

- BCrypt 使用默认构造（随机盐值），每次启动生成不同的哈希值
- 加密后的密码以 `{bcrypt}` 前缀标识，与 `AuthInterceptor` 中解析逻辑一致
- `EnvironmentPostProcessor` 在 `ConfigDataEnvironment` 之后执行，此时配置文件已解析完毕
- 加密值仅存在于内存中，不会持久化到任何文件
- 每次启动均重新执行加密流程，确保运行时态密码始终为 BCrypt 格式
- `{bcrypt}` 预加密格式已废弃，升级后需将密码改为明文
