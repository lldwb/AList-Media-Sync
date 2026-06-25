# 快速入门验证：轻量诊断系统

## 前提条件

- 已完成后端构建并可启动应用。
- 当前环境至少支持一种部署形态：本地开发、Docker 或一体化启动包。
- 使用测试配置，不使用真实生产凭据。

## 场景 1：验证 HTTP 响应头 traceId

1. 启动应用。
2. 请求任意受支持的后端接口，例如仪表板统计接口。
3. 检查响应头包含 `X-Trace-Id`。
4. 使用同一个 `X-Trace-Id` 搜索应用日志。

**预期结果**：
- 响应头存在 `X-Trace-Id`。
- 日志中能找到同一 traceId 的请求记录。
- 异常响应同样返回 `X-Trace-Id`。

## 场景 2：验证请求头 traceId 继承

1. 发送请求时携带 `X-Trace-Id: manual-test-001`。
2. 检查响应头。
3. 搜索日志。

**预期结果**：
- 响应头返回 `manual-test-001`。
- 日志中出现 `manual-test-001`。
- 请求结束后不会污染下一次未携带 traceId 的请求。

## 场景 3：验证 ERROR 日志分流

1. 触发一个可控失败，例如使用错误配置或无效参数导致后端记录 ERROR。
2. 检查 `logs/error.log`。

**预期结果**：
- `logs/error.log` 存在。
- 错误记录包含 traceId、模块、操作和错误原因。
- 普通日志仍保留正常业务流程记录。

## 场景 4：验证一键诊断包

1. 在本地开发环境运行诊断命令。
2. 如果使用 Docker，进入容器或使用 compose 包装命令运行诊断。
3. 如果使用一体化启动包，在解压目录运行诊断命令。
4. 打开 `diagnostics/latest/summary.md`。

**预期结果**：
- 30 秒内生成 `diagnostics/latest`。
- `summary.md` 存在并包含基本信息、最近失败、关键证据、缺失信息和建议下一步。
- 诊断命令输出诊断包路径和摘要路径。

## 场景 5：验证脱敏

1. 在测试环境中配置包含 `password`、`token`、`authorization`、`cookie`、`key` 等字段的值。
2. 生成诊断包。
3. 搜索诊断包内容。

**预期结果**：
- 原始敏感值不出现在诊断包中。
- 对应字段值显示为 `***REDACTED***`。
- 空配置显示为空或 `EMPTY`，不误标记为已脱敏。

## 场景 6：验证部分失败仍生成摘要

1. 临时移动或限制某个非关键日志文件读取权限。
2. 运行诊断命令。
3. 查看 `summary.md`。

**预期结果**：
- 诊断包仍生成。
- `summary.md` 的“缺失信息”列出不可读取项和原因。
- 命令退出信息明确说明诊断信息不完整。

## 建议测试命令

```bash
./mvnw test
```

实现阶段应至少覆盖：

- `TraceIdFilterTest`
- `SensitiveDataMaskerTest`
- `DiagnosticServiceTest`
- 涉及诊断端点时的 `DiagnosticControllerTest`
- 脚本入口 smoke test
