package top.lldwb.alistmediasync.transcode.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.lldwb.alistmediasync.common.service.WsSessionManager;
import top.lldwb.alistmediasync.transcode.entity.TranscodeTask;
import top.lldwb.alistmediasync.transcode.entity.TranscodeTask.TranscodeStatus;
import top.lldwb.alistmediasync.transcode.repository.TranscodeTaskRepository;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 转码任务状态写入器单元测试
 * <p>
 * 覆盖实体重载 / 保存 / 进度落库 / WebSocket 进度广播四类写操作。
 * 广播 payload 是前端进度条的唯一数据来源，字段名、字段数、千分比换算与空值兜底都必须逐一断言；
 * 进度落库失败必须不中断转码主流程。
 * </p>
 *
 * @author AList-Media-Sync
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("转码任务状态写入器测试")
class TranscodeTaskStateWriterTest {

    @Mock
    private TranscodeTaskRepository repository;

    @Mock
    private WsSessionManager wsSessionManager;

    private TranscodeTaskStateWriter writer;

    @BeforeEach
    void setUp() {
        writer = new TranscodeTaskStateWriter(repository, wsSessionManager);
    }

    // ================================================================
    // pushProgress：WebSocket 进度广播
    // ================================================================

    @Test
    @DisplayName("pushProgress — 广播类型为 TRANSCODE_PROGRESS 且 payload 恰为 5 个约定字段")
    void shouldBroadcastProgressWithExpectedPayload() {
        TranscodeTask task = task(7L, TranscodeStatus.UPLOADING, 755, 2, "上传超时");

        writer.pushProgress(task);

        Map<?, ?> payload = captureBroadcastPayload();
        assertEquals(Map.of(
            "taskId", 7L,
            "status", "UPLOADING",
            "progressPercent", 75,
            "retryCount", 2,
            "errorMessage", "上传超时"), payload);
        assertEquals(5, payload.size(), "payload 字段数变化说明前后端契约被破坏");
    }

    @Test
    @DisplayName("pushProgress — errorMessage 为 null 时兜底为空串")
    void shouldFallbackToEmptyStringWhenErrorMessageNull() {
        TranscodeTask task = task(9L, TranscodeStatus.TRANSCODING, 0, 0, null);

        writer.pushProgress(task);

        Map<?, ?> payload = captureBroadcastPayload();
        assertEquals("", payload.get("errorMessage"));
        assertFalse(payload.containsKey("null"));
    }

    @ParameterizedTest(name = "[{index}] 千分比 {0} → 百分比 {1}")
    @CsvSource({"0, 0", "5, 0", "9, 0", "50, 5", "755, 75", "999, 99", "1000, 100"})
    @DisplayName("pushProgress — progressPercent 为千分比整数除法（向下取整）")
    void shouldConvertPermilToIntegerPercent(int permil, int expectedPercent) {
        writer.pushProgress(task(1L, TranscodeStatus.TRANSCODING, permil, 0, null));

        assertEquals(expectedPercent, captureBroadcastPayload().get("progressPercent"));
    }

    // ================================================================
    // reloadTask / save / saveAndReload
    // ================================================================

    @Test
    @DisplayName("reloadTask — 返回仓储查询到的实体")
    void shouldReloadTaskFromRepository() {
        TranscodeTask managed = task(3L, TranscodeStatus.DOWNLOADING, 100, 0, null);
        when(repository.findById(3L)).thenReturn(Optional.of(managed));

        assertSame(managed, writer.reloadTask(3L));
    }

    @Test
    @DisplayName("reloadTask — 任务不存在时抛出 IllegalStateException 且文案含 id")
    void shouldThrowWhenReloadMissingTask() {
        when(repository.findById(404L)).thenReturn(Optional.empty());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> writer.reloadTask(404L));

        assertEquals("转码任务不存在：id=404", ex.getMessage());
    }

    @Test
    @DisplayName("save — 委托仓储保存")
    void shouldSaveThroughRepository() {
        TranscodeTask task = task(5L, TranscodeStatus.PENDING, 0, 0, null);
        when(repository.save(task)).thenReturn(task);

        assertSame(task, writer.save(task));
        verify(repository).save(task);
    }

    @Test
    @DisplayName("saveAndReload — 先保存再按 id 重新加载（返回的是重载后的实体）")
    void shouldSaveThenReload() {
        TranscodeTask detached = task(6L, TranscodeStatus.DOWNLOADING, 0, 0, null);
        TranscodeTask managed = task(6L, TranscodeStatus.DOWNLOADING, 0, 0, null);
        when(repository.save(detached)).thenReturn(detached);
        when(repository.findById(6L)).thenReturn(Optional.of(managed));

        assertSame(managed, writer.saveAndReload(detached));
        verify(repository).save(detached);
        verify(repository).findById(6L);
    }

    // ================================================================
    // persistProgress：FFmpeg 进度落库
    // ================================================================

    @Test
    @DisplayName("persistProgress — 更新进度并保存")
    void shouldPersistProgress() {
        TranscodeTask managed = task(8L, TranscodeStatus.TRANSCODING, 0, 0, null);
        when(repository.findById(8L)).thenReturn(Optional.of(managed));

        writer.persistProgress(8L, 500);

        assertEquals(500, managed.getProgress().intValue());
        verify(repository).save(managed);
    }

    @Test
    @DisplayName("persistProgress — 任务不存在时不保存且不抛异常")
    void shouldSkipPersistWhenTaskMissing() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> writer.persistProgress(99L, 500));

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("persistProgress — 仓储异常被吞掉，不中断转码主流程")
    void shouldSwallowRepositoryFailure() {
        when(repository.findById(anyLong())).thenThrow(new RuntimeException("数据库连接失败"));

        assertDoesNotThrow(() -> writer.persistProgress(10L, 300));

        verify(repository, never()).save(any());
    }

    // ================================================================
    // 测试辅助
    // ================================================================

    private Map<?, ?> captureBroadcastPayload() {
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(wsSessionManager).broadcast(eq("TRANSCODE_PROGRESS"), payload.capture());
        assertInstanceOf(Map.class, payload.getValue(), "广播 payload 必须是 Map 结构");
        return (Map<?, ?>) payload.getValue();
    }

    private static TranscodeTask task(Long id, TranscodeStatus status, int progress,
                                      int retryCount, String errorMessage) {
        TranscodeTask task = new TranscodeTask();
        task.setId(id);
        task.setStatus(status);
        task.setProgress(progress);
        task.setRetryCount(retryCount);
        task.setErrorMessage(errorMessage);
        return task;
    }
}
