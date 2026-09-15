package top.lldwb.alistmediasync.transcode.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import top.lldwb.alistmediasync.common.enums.TargetFormat;
import top.lldwb.alistmediasync.common.mcp.McpToolResult;
import top.lldwb.alistmediasync.common.service.TempFileCleanupTrigger;
import top.lldwb.alistmediasync.transcode.dto.TranscodeTaskVO;
import top.lldwb.alistmediasync.transcode.entity.TranscodeTask;
import top.lldwb.alistmediasync.transcode.service.TranscodeService;

import java.util.List;
import java.util.Map;

/**
 * 转码任务模块 MCP 工具（FR-005）
 * <p>
 * 提供转码任务的查询/创建/重试/清理临时文件/批量删除/重试全部工具。
 * 工具层仅做参数适配与结果封装，业务逻辑复用 {@link TranscodeService} 与 {@link TempFileCleanupTrigger}（FR-009）。
 * </p>
 * <p>
 * {@code transcode_task_create} 采用异步提交模式（FR-014）：创建任务后经
 * {@code executeAsync}（{@code @Async}）立即返回任务 VO，进度通过 {@code transcode_task_get} 轮询获取。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Component
@ConditionalOnProperty(name = "app.mcp.enabled", havingValue = "true")
public class TranscodeTaskMcpTools {

    private final TranscodeService transcodeService;
    private final TempFileCleanupTrigger cleanupService;
    private final McpToolResult result;

    public TranscodeTaskMcpTools(TranscodeService transcodeService, TempFileCleanupTrigger cleanupService,
                                 McpToolResult result) {
        this.transcodeService = transcodeService;
        this.cleanupService = cleanupService;
        this.result = result;
    }

    @McpTool(name = "transcode_task_list", description = "列出所有转码任务")
    public McpSchema.CallToolResult transcodeTaskList() {
        return result.run("transcode_task_list", transcodeService::listAll);
    }

    @McpTool(name = "transcode_task_get", description = "按 ID 查询转码任务详情（含实时进度）")
    public McpSchema.CallToolResult transcodeTaskGet(
            @McpToolParam(description = "转码任务 ID") Long id) {
        return result.run("transcode_task_get", () -> transcodeService.getById(id));
    }

    @McpTool(name = "transcode_task_create", description = "创建转码任务并异步开始执行（下载→转码→上传，立即返回任务，进度通过 transcode_task_get 查询）")
    public McpSchema.CallToolResult transcodeTaskCreate(
            @McpToolParam(description = "源文件在 AList 中的完整路径") String sourceFilePath,
            @McpToolParam(description = "目标文件完整路径（sourceDirectoryTranscode=true 时可选，否则必填）", required = false) String targetFilePath,
            @McpToolParam(description = "目标格式：MP3 / MP4 / FLV") String targetFormat,
            @McpToolParam(description = "音频比特率（bps，默认 128000）", required = false) Integer bitrate,
            @McpToolParam(description = "源存储引擎 ID（从 AList 下载）", required = false) Long sourceEngineId,
            @McpToolParam(description = "目标存储引擎 ID（上传转码结果，sourceDirectoryTranscode=true 时可为空）", required = false) Long targetEngineId,
            @McpToolParam(description = "源目录转码选项（默认 false，启用时输出文件自动放置在源文件所在目录）", required = false) Boolean sourceDirectoryTranscode) {
        return result.run("transcode_task_create", () -> {
            TranscodeTask task = transcodeService.createTask(
                sourceEngineId, targetEngineId, sourceFilePath, targetFilePath,
                TargetFormat.valueOf(targetFormat), bitrate,
                Boolean.TRUE.equals(sourceDirectoryTranscode));
            transcodeService.executeAsync(task);
            return TranscodeTaskVO.from(task);
        });
    }

    @McpTool(name = "transcode_task_retry", description = "重试失败的转码任务（按失败阶段重新执行对应步骤）")
    public McpSchema.CallToolResult transcodeTaskRetry(
            @McpToolParam(description = "转码任务 ID") Long id) {
        return result.run("transcode_task_retry", () -> {
            transcodeService.retry(id);
            return Map.of("taskId", id, "success", true);
        });
    }

    @McpTool(name = "transcode_task_cleanup_temp", description = "清理残留的转码临时文件，返回删除数量")
    public McpSchema.CallToolResult transcodeTaskCleanupTemp() {
        return result.run("transcode_task_cleanup_temp",
            () -> Map.of("deletedCount", cleanupService.manualCleanup()));
    }

    @McpTool(name = "transcode_task_delete_failed", description = "批量删除所有失败状态的转码任务，返回删除数量")
    public McpSchema.CallToolResult transcodeTaskDeleteFailed() {
        return result.run("transcode_task_delete_failed",
            () -> Map.of("deletedCount", transcodeService.deleteByStatusIn(
                TranscodeTask.TranscodeStatus.FAILED_STATUSES)));
    }

    @McpTool(name = "transcode_task_delete_completed", description = "批量删除所有已完成状态的转码任务，返回删除数量")
    public McpSchema.CallToolResult transcodeTaskDeleteCompleted() {
        return result.run("transcode_task_delete_completed",
            () -> Map.of("deletedCount", transcodeService.deleteByStatusIn(
                List.of(TranscodeTask.TranscodeStatus.COMPLETED))));
    }

    @McpTool(name = "transcode_task_retry_all", description = "批量重试所有失败状态的转码任务（异步提交），返回提交数量")
    public McpSchema.CallToolResult transcodeTaskRetryAll() {
        return result.run("transcode_task_retry_all", () -> {
            List<TranscodeTask> failed = transcodeService.findByStatusIn(
                TranscodeTask.TranscodeStatus.FAILED_STATUSES);
            failed.forEach(task -> transcodeService.retry(task.getId()));
            return Map.of("submittedCount", failed.size());
        });
    }
}
