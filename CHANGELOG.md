# 更新日志

本文件记录 AList-Media-Sync 项目的所有显著变更。

格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)，
日期采用 ISO 8601 格式（YYYY-MM-DD）。

## 分类说明

- **Added** 新增的功能
- **Changed** 对已有功能的变更
- **Deprecated** 即将移除的功能
- **Removed** 已移除的功能
- **Fixed** 错误修复
- **Security** 安全相关的修复

## [0.1.0] - 2026-07-25

### Added

- 文档体系优化功能（specs/011）：规范文档系统以支持手工引导区与生成区分界，新增 CONTRIBUTING.md 贡献者入口文档
- 端到端测试基础设施（specs/012）：补全测试金字塔的集成测试层与端到端测试层，新增 6 个 Repository 集成测试（`@DataJpaTest`）、AList 客户端 WireMock 契约测试、3 条核心业务链路 E2E 测试 + traceId 全链路验证
- Maven profile 隔离机制（specs/012）：通过 `maven-failsafe-plugin` 与 3 个 profile（默认/`-Pintegration`/`-Pe2e`）实现单元/集成/端到端测试物理隔离，E2E 串行执行 + 15 分钟超时兜底
- E2E 环境准备脚本（specs/012）：一键下载 AList 二进制（录播姬可选），支持 SHA256 校验、幂等跳过、本地路径跳过（`ALIST_LOCAL_PATH`）、动态端口分配（FR-014）与失败现场保留（FR-009）
- nightly CI 定时运行 E2E 测试（specs/012）：GitHub Actions `schedule.cron` 定时触发 `mvn verify -Pe2e`，PR 流水线不触发 E2E（FR-013）

## [0.0.1-SNAPSHOT] - 2026-07-02

### Added

- AList 媒体同步与转码工具核心功能（specs/001）：实现 AList 媒体文件的自动同步与转码，支持录播姬 Webhook 触发、转码任务管理、文件同步策略等核心能力
- 转码临时文件可配置后缀（specs/002）：支持自定义转码临时文件后缀与清理策略，避免临时文件污染媒体库
- Docker 容器化部署支持（specs/003）：提供 Dockerfile 与容器化部署方案，支持一键容器化运行
- Web 管理前端界面（specs/004）：基于 React 19 + TypeScript + Vite + Tailwind CSS 实现完整 Web 管理界面，包含转码任务管理、同步任务配置、存储策略管理、目录树选择器等组件
- 一体化启动包（specs/005）：支持前后端一体化打包启动，简化部署流程
- 存储引擎重构与体验优化（specs/006）：将存储引擎重构为策略模式，支持 AList 与本地双类型存储引擎，优化转码任务状态模型为 8 状态统一模型
- 密码加密优化与代码目录重组（specs/007）：引入 BCrypt 密码加密、加密密钥环境后处理器，管理员密码配置改为环境变量支持，重组代码目录结构
- 转码与同步模块优化及实时通信改造（specs/008）：优化转码同步模块并集成 WebSocket 实时通信，实现自动重试与实时推送功能，重构 WebSocket 消息类型定义
- 轻量诊断系统（specs/009）：添加轻量级诊断系统和 traceId 追踪功能，实施 API 调用与本地目录操作日志规范，优化 traceId 生成格式
- Service 层重复代码与超长方法重构（specs/010）：重构服务层重复代码和样板代码，优化同步服务中的目标目录扫描逻辑，添加文件移动功能支持
- 项目构建与环境检查脚本
- 自定义状态码响应方法与存储引擎状态管理
- 文件选择模式支持

### Changed

- 将 Jackson ObjectMapper 替换为 JsonMapper
- 将 AListApiClient 替换为 ApiUtil 工具类
- 重构 AList API 工具类和存储引擎实现
- 重构转码服务支持目录模式和文件模式统一处理
- 优化转码服务的实体管理和状态更新逻辑
- 重构 WebSocket 消息类型定义并优化组件功能
- 优化文件同步逻辑以支持相对路径匹配
- 将 sameDirectoryTranscode 重命名为 sourceDirectoryTranscode
- 重构测试代码以改进依赖注入和模拟配置
- 配置项目编码为 UTF-8
- 改进项目根目录查找逻辑和命令格式化

### Fixed

- 修复 AList 存储引擎连接测试问题
- 修复转码文件输出目录计算逻辑
- 修复 AList 文件下载功能
- 修复 AList 存储引擎文件下载逻辑
- 修复本地存储策略空目录和分页逻辑问题
- 修复转码任务创建 DTO 中布尔字段反序列化问题
- 修复 AList 存储策略中路径解析问题
- 修复 AList 存储引擎分页获取文件问题
- 修复目录树选择器中的重复路径和无限递归问题
- 解决转码过程中乐观锁冲突问题
- 修复 BCrypt 密码加密验证逻辑
- 修复加密转换器密钥管理和 Chrome 开发者工具探测问题
- 修复转码操作的目标存储引擎验证问题
- 修复前端静态资源配置问题
- 修复服务器地址日志输出格式
- 解决覆盖模式下文件同步问题

### Security

- 修复管理员账户密码哈希与验证逻辑
- 将管理员密码配置改为环境变量支持，避免硬编码敏感信息
