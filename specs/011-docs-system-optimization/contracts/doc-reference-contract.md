# 契约：文档间引用与 SSOT 约定

**分支**：`011-docs-system-optimization` | **日期**：2026-07-02 | **关联**：[plan.md](./plan.md)

本契约定义文档体系内部各文档间的引用规则、SSOT 责任边界与链接规范，确保信息不重复、不漂移。

## 1. 引用方向规则

### 1.1 根级 → docs/

- `README.md` 的"导航"章节 MUST 链接到 `docs/01` ~ `docs/06` 与 `docs/operations/`、`docs/architecture/`
- `AGENTS.md` 的"项目结构"章节 MAY 引用 `docs/03-架构设计.md` 作为架构详述入口，但 MUST NOT 复制架构内容
- `CHANGELOG.md` 不引用 docs/（变更日志为独立时间线）

### 1.2 docs/ 主题间

- 主题文档间采用"就近引用"原则：`docs/02-开发环境搭建.md` 引用 `docs/04-配置说明.md` 的配置项，而非重复列举
- `docs/06-运维部署.md` 引用 `docs/operations/环境变量清单.md`，而非在正文中重复全量变量表
- `docs/03-架构设计.md` 引用 `docs/architecture/模块-*.md` 作为模块细化入口

### 1.3 docs/ → specs/

- docs/ 描述"系统当前状态"，specs/ 为"历史设计档案"
- docs/ MAY 链接到对应 specs/ 的 `spec.md` 作为"设计背景"，但 MUST 标注"历史档案，可能已演进"
- specs/ 中的内容 MUST NOT 被复制到 docs/，仅引用

### 1.4 docs/ → md/

- `docs/05-API接口文档.md` 的"外部对接系统"章节 MAY 链接到 `md/alist/` 与 `md/danmuji/` 作为外部协议参考
- md/ 内容 MUST NOT 被复制到 docs/

## 2. SSOT 责任边界

每类信息的"权威写入位置"与"只读引用位置"如下：

| 信息 | 写入位置（权威） | 只读引用位置 |
|------|----------------|------------|
| `app.*` 配置项默认值与含义 | `docs/04-配置说明.md` | README.md（仅链接）、docs/02（仅必填项摘要） |
| 环境变量全量清单 | `docs/operations/环境变量清单.md` | docs/06（仅链接）、.env 模板（仅生产必填项） |
| API 端点契约 | Controller/DTO 注解（源真值） | docs/05（派生，不手写） |
| 架构总览 | `docs/03-架构设计.md` | AGENTS.md（仅链接）、README.md（一句话） |
| 模块职责 | `docs/architecture/模块-*.md` | docs/03（仅链接） |
| 变更历史 | `CHANGELOG.md` | — |

### 规则

- **R-001**：只读引用位置 MUST NOT 重复权威位置的完整内容，仅 MAY 摘要关键项（如"必填环境变量"列表）
- **R-002**：当权威位置更新时，引用位置无需同步（因不复制内容），仅需在实现阶段确认链接有效
- **R-003**：违反 SSOT 的重复内容 MUST 在代码审查中被指出

## 3. 链接规范

### 3.1 相对路径

- 文档间链接 MUST 使用相对路径（如 `docs/04-配置说明.md`、`../architecture/模块-sync.md`）
- MUST NOT 使用绝对路径或带域名的 URL（除非指向外部站点如 springdoc.org）

### 3.2 链接锚点

- 跨文档引用特定章节时，MAY 使用锚点（如 `docs/04-配置说明.md#转码配置`）
- 锚点目标 MUST 存在，由 VR-004 死链检查保证

### 3.3 中文文件名

- docs/ 下文件名使用中文（如 `模块-sync.md`、`环境变量清单.md`），遵循章程原则 IV
- 链接中的中文 MUST URL 编码（Markdown 渲染器自动处理，源文件保持中文原文）

## 4. 派生文档标注

`docs/05-API接口文档.md` 作为派生产物，文件头 MUST 包含以下声明：

```markdown
<!-- 派生产物声明 -->
> 本文档由 `scripts/gen-api-doc` 自动生成，请勿手工编辑。
> 源真值位于 `src/main/java/top/lldwb/alistmediasync/**/controller/*.java`
> 与 `**/dto/*.java` 的 OpenAPI 注解中。
> 最后生成时间：[由脚本填充]
```

## 5. 冻结边界

- `specs/001..010/` 与 `md/` 为冻结区域，本功能 MUST NOT 修改、删除、重命名其中任何文件
- 冻结区域的完整性由 VR-003 校验（文件数与内容哈希不变）
- docs/ 引用 specs/ 时，链接目标 MUST 指向已存在的 spec 文件，不假设其内容会更新
