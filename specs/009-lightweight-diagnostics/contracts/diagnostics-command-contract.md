# 诊断命令契约

## 目标

本地开发、Docker、一体化启动包都必须提供一键诊断入口，生成 `diagnostics/latest` 诊断包。

## 支持入口

| 部署形态 | 入口 | 预期结果 |
|----------|------|----------|
| 本地开发 | `scripts/diagnose.sh` 或 `scripts/diagnose.bat` | 在项目根目录生成 `diagnostics/latest` |
| Docker | 容器内执行诊断脚本，或通过 compose 包装命令执行 | 在容器可访问的数据目录生成诊断包，并可从宿主机读取 |
| 一体化启动包 | 解压目录中的 `diagnose.sh` 或 `diagnose.bat` | 在启动包目录生成 `diagnostics/latest` |

## 输入参数

| 参数 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| `--output` | 否 | `diagnostics/latest` | 诊断包输出目录 |
| `--trace-id` | 否 | 最近失败 traceId | 指定优先收集的 traceId |
| `--max-lines` | 否 | 合理默认值 | 每个日志摘录最多保留行数 |

## 输出

命令完成后必须输出：

```text
诊断包已生成：diagnostics/latest
摘要文件：diagnostics/latest/summary.md
```

部分成功时必须输出：

```text
诊断包已生成但信息不完整：diagnostics/latest
摘要文件：diagnostics/latest/summary.md
缺失信息：<原因列表>
```

失败时必须输出明确原因，不得静默失败。

## 行为规则

1. 命令只读取日志、配置摘要、环境信息和最近任务上下文。
2. 命令不得触发同步、转码、Webhook 或数据库写业务数据。
3. 命令必须在输出前完成敏感信息脱敏。
4. 命令重复执行时，`diagnostics/latest` 代表最新结果。
5. 日志目录或配置文件不可读时，命令仍尽量生成摘要并列出缺失项。
