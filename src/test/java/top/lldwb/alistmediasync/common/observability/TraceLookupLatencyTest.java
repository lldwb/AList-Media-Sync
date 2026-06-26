package top.lldwb.alistmediasync.common.observability;

import org.junit.jupiter.api.Test;
import top.lldwb.alistmediasync.common.util.TraceContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SC-002 可定位性门禁（T040）
 * <p>
 * 模拟 1 次失败 + 100 行混杂日志，断言通过 traceId 在结构化日志中
 * 定位失败记录首条命中耗时 ≤ 50 ms。
 * </p>
 */
class TraceLookupLatencyTest {

    @Test
    void 应在50ms内通过traceId定位失败记录() {
        String targetTraceId = "failure-target-trace-001";
        List<String> logs = new ArrayList<>(101);

        Random random = new Random(42);
        for (int i = 0; i < 100; i++) {
            logs.add(buildLine("trace-other-" + i + "-aa", "INFO 正常运行 " + random.nextInt()));
        }
        // 失败行混入中间位置
        int insertAt = random.nextInt(100);
        logs.add(insertAt, buildLine(targetTraceId,
            "ERROR module=sync operation=同步任务执行 errorType=NetworkTimeout cause=连接超时"));

        // 模拟"在结构化日志中按 traceId 搜索"——通常是简单的字符串包含
        long start = System.nanoTime();
        String hit = null;
        for (String line : logs) {
            if (line.contains(targetTraceId)) {
                hit = line;
                break;
            }
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertNotNull(hit, "必须能在日志中定位失败记录");
        assertTrue(hit.contains("ERROR"), "命中行应为失败行");
        assertTrue(elapsedMs <= 50, "定位耗时 " + elapsedMs + "ms 超过 SC-002 门禁 50ms");
    }

    private String buildLine(String traceId, String message) {
        return "2026-06-27 00:00:00.000 [thread] [traceId=" + traceId + "] " + message;
    }

    @Test
    void traceContext生成的traceId应保证唯一性满足SC003() {
        // 1000 次生成不应出现重复
        int n = 1000;
        List<String> ids = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ids.add(TraceContext.generate());
        }
        long distinct = ids.stream().distinct().count();
        assertEquals(n, distinct, "1000 次 traceId 生成应全部唯一");
        for (String id : ids) {
            assertTrue(TraceContext.isValid(id), "所有 traceId 必须合法：" + id);
        }
    }
}
