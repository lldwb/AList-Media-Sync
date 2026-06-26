# SC 指标汇总：轻量诊断系统

**汇总日期**：2026-06-27
**汇总来源**：speckit-implement 实现阶段量化测试结果

## 量化结果

| 指标 | 测量任务 | 实测结果 | 目标 | 是否达标 |
|------|----------|----------|------|----------|
| **SC-001**：诊断包生成耗时 | T021 `DiagnosticServicePerformanceTest` | 典型场景（5000 行日志 + 完整环境采集）单次 < 50 ms；`durationMs` 字段已正确填充 | ≤ 30 000 ms | ✓ |
| **SC-002**：traceId 定位失败记录 | T040 `TraceLookupLatencyTest` | 100 行混杂日志中通过 traceId 命中失败行耗时 < 50 ms | ≤ 2 min | ✓ |
| **SC-003**：任务级 traceId 非空唯一 | T040 `TraceLookupLatencyTest.traceContext生成的traceId应保证唯一性满足SC003` + 全量后端测试 | 1000 次生成全部唯一且合法；279 个全量测试运行期间 MDC traceId 全部非空 | 100% | ✓ |
| **SC-004**：诊断包零原始敏感值 | T053 `DiagnosticPackageRedactionScanTest` | 5 类敏感样本（密码、Token、密钥、Bearer、Cookie）递归扫描诊断包均无明文 | 0 个原始敏感值 | ✓ |
| **SC-005**：错误事件结构化字段完整率 | T039 `StructuredErrorCoverageTest` | sync/transcode/webhook 三类失败路径 10 条 ERROR 事件，六字段（module/operation/traceId/errorType/message/cause）完整率 100% | ≥ 90% | ✓ |
| **SC-006**：部分失败仍生成摘要 | T017 `DiagnosticServiceTest.缺失日志时应生成PARTIAL状态并列出缺失项` + T068 quickstart 场景 6 | `status=PARTIAL`，`missingItems` 列出缺失项与原因 | 生成可读摘要并列出缺失项 | ✓ |

## 测量任务来源

- **T021** — `src/test/java/top/lldwb/alistmediasync/common/service/DiagnosticServicePerformanceTest.java`
- **T040** — `src/test/java/top/lldwb/alistmediasync/common/observability/TraceLookupLatencyTest.java`
- **T049** — `./mvnw -Dtest=SyncServiceTest,TranscodeServiceTest,WebhookServiceTest,GlobalExceptionHandlerTest,StructuredErrorCoverageTest,TraceLookupLatencyTest test`
- **T053** — `src/test/java/top/lldwb/alistmediasync/common/service/DiagnosticPackageRedactionScanTest.java`
- **T060** — `./mvnw -Dtest=SensitiveDataMaskerTest,DiagnosticServiceTest,RestClientConfigTest,DiagnosticPackageRedactionScanTest test`
- **T065** — `./mvnw -Dskip.frontend=true test`（全量后端测试，结果：279 个通过 / 0 失败 / 0 跳过）
- **T068** — `specs/009-lightweight-diagnostics/quickstart-results.md`

## 全量门禁状态

- ✅ 所有 SC 指标均达标
- ✅ 后端全量测试 279/279 通过
- ✅ 章程原则 V（测试覆盖）：每个新增 Java 类均有同步测试
- ✅ 章程原则 VI（YAGNI）：未引入新第三方依赖
- ✅ 章程原则 VII（日志规范）：ERROR 日志包含 traceId、errorType、message、可定位原因；诊断包零原始敏感值
- ✅ 章程原则 IX（文档同步）：`README.md` 已更新诊断系统使用说明
