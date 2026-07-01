# 快速入门：文档体系优化验证

**分支**：`011-docs-system-optimization` | **日期**：2026-07-02 | **关联**：[plan.md](./plan.md)

本指南提供可运行的验证场景，证明文档体系优化功能端到端可用。实现完成后按以下步骤验证。

## 验证场景 1：API 文档自动生成流水线

**目的**：验证 docs/05-API接口文档.md 可通过注解自动生成，且与代码一致（SC-004）。

### 前提条件

- JDK 21、Maven Wrapper（`./mvnw`）可用
- Node 22 可用（若使用 `openapi-to-md` 的 npm 安装方式）
- 项目已按 plan.md 完成 SpringDoc 集成与注解增强

### 步骤

1. 执行生成命令：
   ```bash
   ./scripts/gen-api-doc.sh
   # Windows: scripts\gen-api-doc.bat
   ```
2. 确认 `docs/05-API接口文档.md` 生成成功，文件头包含派生产物声明
3. 一致性验证：
   ```bash
   git diff --exit-code docs/05-API接口文档.md
   ```
4. 注解覆盖率校验：
   ```bash
   ./scripts/check-annotation-coverage.sh
   ```

### 预期结果

- 步骤 1：退出码 0，无 stderr 输出
- 步骤 2：`docs/05-API接口文档.md` 非空，文件头有"派生产物声明"与生成时间
- 步骤 3：退出码 0（diff 为空，除生成时间戳外）
- 步骤 4：退出码 0，输出"所有 Controller/DTO 注解覆盖率 100%"

## 验证场景 2：新开发者本地启动链路

**目的**：验证 README.md → docs/01 → docs/02 链路可支撑新开发者 30 分钟内完成本地启动（SC-001）。

### 步骤

1. 模拟新开发者：仅阅读 `README.md` 的导航，点击进入 `docs/01-项目概述.md` 与 `docs/02-开发环境搭建.md`
2. 按 docs/02 步骤配置本地环境：
   - 设置 `ALIST_BASE_URL`、`ALIST_TOKEN`、`ALIST_CRYPTO_KEY` 环境变量
   - 设置 `app.data-dir` 路径
3. 启动应用：
   ```bash
   ./mvnw spring-boot:run
   ```
4. 访问 Web 管理界面：`http://localhost:8080`

### 预期结果

- 30 分钟内完成启动并访问 Web 管理界面
- 无需查阅任何 spec 或源码即可完成配置
- 启动过程中遇到的问题可通过 docs/02 常见问题章节解决

## 验证场景 3：Docker 部署链路

**目的**：验证 docs/06 与 docs/operations/ 可支撑运维人员 15 分钟内完成 Docker 部署（SC-002）。

### 步骤

1. 模拟运维人员：仅阅读 `docs/06-运维部署.md` 与 `docs/operations/环境变量清单.md`
2. 按文档配置 `.env` 文件（区分必填与选填）
3. 部署：
   ```bash
   docker compose up -d
   ```
4. 健康检查：
   ```bash
   curl http://localhost:8080/actuator/health
   ```

### 预期结果

- 15 分钟内服务启动并通过健康检查
- 环境变量清单覆盖所有必填项，无遗漏

## 验证场景 4：配置 SSOT 一致性

**目的**：验证 docs/04-配置说明.md 与 AppProperties.java、application.yaml 一致（VR-001）。

### 步骤

1. 对照 `docs/04-配置说明.md` 的映射表与 `AppProperties.java` 的字段
2. 对照 `docs/operations/环境变量清单.md` 与 `application.yaml`、`.env` 模板
3. 运行配置一致性校验（若已实现）：
   ```bash
   ./scripts/check-config-consistency.sh
   ```

### 预期结果

- 19 个 `app.*` 配置项在 docs/04、AppProperties.java、application.yaml 三处一致
- 非 `app.*` 环境变量在 docs/operations/环境变量清单、Dockerfile、.env 模板三处一致

## 验证场景 5：冻结文件完整性

**目的**：验证 specs/ 与 md/ 冻结区域未受影响（SC-007）。

### 步骤

1. 获取变更前快照（实现开始时记录 specs/ 与 md/ 的文件列表与内容哈希）
2. 实现完成后，对比当前文件列表与哈希：
   ```bash
   git diff --name-only specs/001-alist-media-sync specs/002-transcode-temp-suffix-config ... md/
   ```

### 预期结果

- specs/001..010/ 与 md/ 下所有文件无修改、删除、重命名
- 文件数与内容哈希完全一致

## 验证场景 6：文档链接有效性

**目的**：验证 docs/ 间引用与 specs/ 引用无死链（VR-004）。

### 步骤

1. 运行死链检查（若已实现）：
   ```bash
   ./scripts/check-doc-links.sh
   ```
2. 或人工抽查：从 README.md 出发，点击所有导航链接，确认目标存在

### 预期结果

- 所有相对链接目标存在
- 无指向已删除文件的悬空链接

## 前提条件汇总

执行上述验证前，确保以下任务（由 `/speckit-tasks` 生成并实现）已完成：

- SpringDoc 依赖与 OpenApiConfig 集成
- 7 个 Controller + 约 20 个 DTO 注解增强
- gen-api-doc 脚本与 Maven profile
- docs/ 全部主题文档与 architecture/、operations/ 子目录文档
- README.md 与 AGENTS.md 精简
- CHANGELOG.md 创建

详细的任务清单与依赖排序见 `tasks.md`（由 `/speckit-tasks` 命令生成）。
