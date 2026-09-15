package top.lldwb.alistmediasync.transcode.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import top.lldwb.alistmediasync.transcode.entity.TranscodeTask;
import top.lldwb.alistmediasync.transcode.entity.TranscodeTask.TranscodeStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static top.lldwb.alistmediasync.transcode.entity.TranscodeTask.TranscodeStatus.*;

/**
 * 转码状态机单元测试
 * <p>
 * 8 状态模型的合法性规则是三步流程（下载 → 转码 → 上传）的正确性基础：
 * 合法转移必须放行且真正改变状态，非法转移必须被拦截且不产生任何副作用。
 * 本测试以「独立声明的期望转移表 + 9×9 全量组合」双向校验，既覆盖 10 条合法转移，
 * 也证明实现中不存在多余的放行（转移集合完全一致）。
 * </p>
 *
 * @author AList-Media-Sync
 */
@DisplayName("转码状态机测试")
class TranscodeStateMachineTest {

    /** 期望的 10 条合法转移（独立于实现声明，作为测试判据基准） */
    private static final List<Map.Entry<TranscodeStatus, TranscodeStatus>> EXPECTED_LEGAL = List.of(
        Map.entry(PENDING, DOWNLOADING),
        Map.entry(DOWNLOADING, TRANSCODING),
        Map.entry(DOWNLOADING, DOWNLOAD_FAILED),
        Map.entry(DOWNLOAD_FAILED, DOWNLOADING),    // 重试
        Map.entry(TRANSCODING, UPLOADING),
        Map.entry(TRANSCODING, TRANSCODE_FAILED),
        Map.entry(TRANSCODE_FAILED, TRANSCODING),   // 重试
        Map.entry(UPLOADING, COMPLETED),
        Map.entry(UPLOADING, UPLOAD_FAILED),
        Map.entry(UPLOAD_FAILED, UPLOADING)         // 重试
    );

    /** 期望的失败状态集合（三个步骤级失败，不含编排级 FAILED） */
    private static final List<TranscodeStatus> EXPECTED_FAILURE_STATUSES =
        List.of(DOWNLOAD_FAILED, TRANSCODE_FAILED, UPLOAD_FAILED);

    static Stream<Arguments> allStatusPairs() {
        List<Arguments> pairs = new ArrayList<>();
        for (TranscodeStatus from : TranscodeStatus.values()) {
            for (TranscodeStatus to : TranscodeStatus.values()) {
                pairs.add(Arguments.of(from, to, EXPECTED_LEGAL.contains(Map.entry(from, to))));
            }
        }
        return pairs.stream();
    }

    // ================================================================
    // 合法 / 非法转移（全量组合）
    // ================================================================

    @ParameterizedTest(name = "[{index}] {0} → {1}（期望合法={2}）")
    @MethodSource("allStatusPairs")
    @DisplayName("9×9 全量组合：合法转移放行并改状态，非法转移拦截且不动状态")
    void shouldMatchExpectedTransitionMatrix(TranscodeStatus from, TranscodeStatus to, boolean expectedLegal) {
        TranscodeTask task = new TranscodeTask();
        task.setStatus(from);

        if (expectedLegal) {
            assertDoesNotThrow(() -> TranscodeStateMachine.validateTransition(from, to));
            TranscodeStateMachine.transition(task, to);
            assertEquals(to, task.getStatus(), "合法转移必须真正改变任务状态");
        } else {
            IllegalStateException onValidate = assertThrows(IllegalStateException.class,
                () -> TranscodeStateMachine.validateTransition(from, to));
            assertEquals("非法的转码状态转换：" + from + " → " + to, onValidate.getMessage());
            assertThrows(IllegalStateException.class,
                () -> TranscodeStateMachine.transition(task, to));
            assertEquals(from, task.getStatus(), "非法转移不得改变任务状态");
        }
    }

    @ParameterizedTest(name = "[{index}] 非法转移 {0} → {1}")
    @CsvSource({"PENDING, COMPLETED", "COMPLETED, DOWNLOADING", "DOWNLOAD_FAILED, UPLOADING"})
    @DisplayName("典型非法转移应抛出 IllegalStateException 且文案与实现一致")
    void shouldRejectTypicalIllegalTransitions(TranscodeStatus from, TranscodeStatus to) {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> TranscodeStateMachine.validateTransition(from, to));

        assertEquals("非法的转码状态转换：" + from + " → " + to, ex.getMessage());
    }

    // ================================================================
    // 失败状态判定
    // ================================================================

    @ParameterizedTest
    @EnumSource(TranscodeStatus.class)
    @DisplayName("isFailure 仅对三个步骤级失败状态返回 true（编排级 FAILED 不算）")
    void isFailureShouldOnlyCoverStepFailures(TranscodeStatus status) {
        boolean expected = EXPECTED_FAILURE_STATUSES.contains(status);

        assertEquals(expected, status.isFailure(), status + " 的 isFailure() 取值与失败状态集合不一致");
        assertEquals(expected, TranscodeStateMachine.isFailureStatus(status));
    }

    @ParameterizedTest
    @EnumSource(TranscodeStatus.class)
    @DisplayName("isRetryable 比 isFailure 多覆盖编排级 FAILED")
    void isRetryableShouldCoverOrchestrationFailure(TranscodeStatus status) {
        assertEquals(status.isFailure() || status == FAILED, status.isRetryable(),
            status + " 的 isRetryable() 应为 isFailure() 或编排级 FAILED");
    }

    @Test
    @DisplayName("FAILED_STATUSES / RETRYABLE_STATUSES 常量取值与语义一致")
    void shouldExposeExpectedStatusSets() {
        assertEquals(EXPECTED_FAILURE_STATUSES, TranscodeStatus.FAILED_STATUSES);
        assertEquals(List.of(DOWNLOAD_FAILED, TRANSCODE_FAILED, UPLOAD_FAILED, FAILED),
            TranscodeStatus.RETRYABLE_STATUSES);
        assertEquals(3, TranscodeStatus.FAILED_STATUSES.size());
        assertEquals(4, TranscodeStatus.RETRYABLE_STATUSES.size());
    }
}
