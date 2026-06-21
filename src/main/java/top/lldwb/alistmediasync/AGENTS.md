# 后端 AGENTS.md

> **文件权重**：三级文件（行政法规级），低于 `AGENTS.md`（根级·法律）和 `constitution.md`（宪法），高于各模块 AGENTS.md（地方性法规）。适用于涉及后端整体的修改，或下级模块 AGENTS.md 无法解释时。

## 功能

后端基于 Spring Boot 4.1.0 + Java 21，提供文件同步、媒体转码、Webhook 事件处理等核心业务能力。

## 技术栈

| 层 | 技术 |
|---|------|
| 框架 | Spring Boot 4.1.0（Java 21 虚拟线程） |
| 持久层 | Spring Data JPA + Hibernate + H2（文件模式） |
| 转码引擎 | JAVE2 (ws.schild) 封装 FFmpeg |
| 加密 | AES-256-GCM（Token 加密）+ BCrypt（密码哈希） |
| HTTP 客户端 | Spring RestClient |
| 构建工具 | Maven Wrapper（`./mvnw`） |

## 模块结构

```
src/main/java/top/lldwb/alistmediasync/
├── common/         # 通用模块（配置、认证、加密、工具）
├── storage/        # 存储引擎模块（策略模式：AList 远程 + 本地）
├── sync/           # 同步任务模块（三模式 + 三阶段）
├── transcode/      # 转码模块（三步流程 + 8 状态模型）
└── webhook/        # Webhook 模块（事件接收 + 规则匹配）
```

## 模块 AGENTS.md 索引

| 模块 | AGENTS.md 路径 | 一句话说明 |
|------|---------------|-----------|
| common | `src/main/java/…/common/AGENTS.md` | 共享基础设施（配置、认证、加密、工具） |
| storage | `src/main/java/…/storage/AGENTS.md` | 策略模式存储引擎（AList 远程 + 本地） |
| sync | `src/main/java/…/sync/AGENTS.md` | 文件同步引擎（三模式+三阶段） |
| transcode | `src/main/java/…/transcode/AGENTS.md` | 媒体转码引擎（三步流程+8状态） |
| webhook | `src/main/java/…/webhook/AGENTS.md` | Webhook 事件接收+规则匹配 |

## 章程合规要点

- 严格遵守三层架构（Controller → Service → Repository），禁止跨层调用（原则 I）
- 所有实体 MUST 有 `@Version`，写操作 MUST 有 `@Transactional`（原则 II）
- 统一 `ApiResult<T>` 封装，DTO 不暴露 Entity（原则 III）
- 修改 Java 类 MUST 同步更新单元测试（原则 V）
- 日志按 DEBUG/INFO/WARN/ERROR 四级输出（原则 VII）
