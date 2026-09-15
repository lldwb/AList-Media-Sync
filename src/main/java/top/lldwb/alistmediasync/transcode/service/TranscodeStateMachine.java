package top.lldwb.alistmediasync.transcode.service;

import top.lldwb.alistmediasync.transcode.entity.TranscodeTask;
import top.lldwb.alistmediasync.transcode.entity.TranscodeTask.TranscodeStatus;

import java.util.Map;
import java.util.Set;

/**
 * 转码状态机（自 {@code TranscodeService} 按职责切出）
 * <p>
 * 承载 8 状态模型的合法性规则与失败状态判定，供编排层（{@code TranscodeService}）与
 * 单文件处理器复用。三步流程的合法转换：
 * </p>
 * <ul>
 *   <li>正常链路：PENDING → DOWNLOADING → TRANSCODING → UPLOADING → COMPLETED</li>
 *   <li>失败链路：每步可独立失败（DOWNLOAD_FAILED / TRANSCODE_FAILED / UPLOAD_FAILED），
 *       重试从失败步骤继续</li>
 * </ul>
 * <p>
 * 本类为无状态的规则集：方法均为静态，不持有依赖、不持久化、不涉及事务语义；
 * 状态变更由调用方统一持久化。
 * </p>
 *
 * @author AList-Media-Sync
 */
public final class TranscodeStateMachine {

    /**
     * 合法状态转换集合（8 状态模型）
     * <p>
     * 三步流程：PENDING → DOWNLOADING → TRANSCODING → UPLOADING → COMPLETED，
     * 每步可独立失败，重试从失败步骤继续。
     * </p>
     */
    private static final Set<Map.Entry<TranscodeStatus, TranscodeStatus>> VALID_TRANSITIONS = Set.of(
        Map.entry(TranscodeStatus.PENDING, TranscodeStatus.DOWNLOADING),
        Map.entry(TranscodeStatus.DOWNLOADING, TranscodeStatus.TRANSCODING),
        Map.entry(TranscodeStatus.DOWNLOADING, TranscodeStatus.DOWNLOAD_FAILED),
        Map.entry(TranscodeStatus.DOWNLOAD_FAILED, TranscodeStatus.DOWNLOADING),    // 重试
        Map.entry(TranscodeStatus.TRANSCODING, TranscodeStatus.UPLOADING),
        Map.entry(TranscodeStatus.TRANSCODING, TranscodeStatus.TRANSCODE_FAILED),
        Map.entry(TranscodeStatus.TRANSCODE_FAILED, TranscodeStatus.TRANSCODING),   // 重试
        Map.entry(TranscodeStatus.UPLOADING, TranscodeStatus.COMPLETED),
        Map.entry(TranscodeStatus.UPLOADING, TranscodeStatus.UPLOAD_FAILED),
        Map.entry(TranscodeStatus.UPLOAD_FAILED, TranscodeStatus.UPLOADING)         // 重试
    );

    private TranscodeStateMachine() {
    }

    /**
     * 验证状态转换是否合法
     *
     * @param from 当前状态
     * @param to   目标状态
     * @throws IllegalStateException 如果转换非法
     */
    public static void validateTransition(TranscodeStatus from, TranscodeStatus to) {
        if (!VALID_TRANSITIONS.contains(Map.entry(from, to))) {
            throw new IllegalStateException(
                String.format("非法的转码状态转换：%s → %s", from, to));
        }
    }

    /**
     * 执行状态转换并校验（仅更新内存状态，不单独持久化）
     * <p>
     * 状态变更由调用方统一持久化，避免事务内多次 save 引发乐观锁冲突。
     * </p>
     *
     * @param task         待转换的转码任务
     * @param targetStatus 目标状态
     * @throws IllegalStateException 如果转换非法
     */
    public static void transition(TranscodeTask task, TranscodeStatus targetStatus) {
        validateTransition(task.getStatus(), targetStatus);
        task.setStatus(targetStatus);
    }

    /**
     * 检查是否为失败状态（下载/转码/上传三个失败阶段，不含编排级 FAILED）
     *
     * @param status 待判定的状态
     * @return 命中失败状态集合时为 {@code true}
     */
    public static boolean isFailureStatus(TranscodeStatus status) {
        return status.isFailure();
    }
}
