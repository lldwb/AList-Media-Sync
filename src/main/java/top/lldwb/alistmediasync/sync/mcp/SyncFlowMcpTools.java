package top.lldwb.alistmediasync.sync.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import top.lldwb.alistmediasync.common.enums.ConflictStrategy;
import top.lldwb.alistmediasync.common.enums.TargetFormat;
import top.lldwb.alistmediasync.common.mcp.McpToolResult;
import top.lldwb.alistmediasync.sync.dto.SyncTaskCreateDTO;
import top.lldwb.alistmediasync.sync.entity.SyncTask;
import top.lldwb.alistmediasync.sync.service.SyncService;
import top.lldwb.alistmediasync.sync.service.SyncTaskManageService;

import java.util.Map;

/**
 * 同步流程级快捷工具（FR-013 混合模式的流程级补充）
 * <p>
 * 提供"创建同步任务 → 立即手动触发执行"的一次性快捷工具，减少 AI 多步编排的 token 消耗。
 * 业务逻辑复用 {@link SyncTaskManageService} 与 {@link SyncService}（FR-009），
 * 长任务采用异步提交（FR-014），工具立即返回任务 ID。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Component
@ConditionalOnProperty(name = "app.mcp.enabled", havingValue = "true")
public class SyncFlowMcpTools {

    private final SyncTaskManageService manageService;
    private final SyncService syncService;
    private final McpToolResult result;

    public SyncFlowMcpTools(SyncTaskManageService manageService, SyncService syncService, McpToolResult result) {
        this.manageService = manageService;
        this.syncService = syncService;
        this.result = result;
    }

    @McpTool(name = "sync_flow_create_and_execute",
        description = "一次性完成「创建同步任务 → 立即手动触发执行」，返回任务 ID（结果通过 sync_task_get_executions 查询）")
    public McpSchema.CallToolResult syncFlowCreateAndExecute(
            @McpToolParam(description = "任务名称") String name,
            @McpToolParam(description = "源存储引擎 ID") Long sourceEngineId,
            @McpToolParam(description = "目标存储引擎 ID") Long targetEngineId,
            @McpToolParam(description = "源目录路径") String sourcePath,
            @McpToolParam(description = "目标目录路径") String targetPath,
            @McpToolParam(description = "同步模式：NEW_ONLY / FULL / MOVE（默认 NEW_ONLY）", required = false) String syncMode,
            @McpToolParam(description = "是否启用同步后转码（默认 false）", required = false) Boolean transcodeEnabled,
            @McpToolParam(description = "目标转码格式：MP3 / MP4 / FLV（默认 MP3）", required = false) String targetFormat,
            @McpToolParam(description = "冲突处理策略：OVERWRITE / SKIP / RENAME（默认 SKIP）", required = false) String conflictStrategy,
            @McpToolParam(description = "排除模式（换行分隔），如 *.tmp", required = false) String excludePatterns,
            @McpToolParam(description = "调度类型：MANUAL / CRON / INTERVAL（默认 MANUAL）", required = false) String scheduleType,
            @McpToolParam(description = "Cron 表达式（scheduleType=CRON 时使用）", required = false) String cronExpression,
            @McpToolParam(description = "间隔秒数（scheduleType=INTERVAL 时使用）", required = false) Integer intervalSeconds) {
        return result.run("sync_flow_create_and_execute", () -> {
            SyncTaskCreateDTO dto = new SyncTaskCreateDTO();
            dto.setName(name);
            dto.setSourceEngineId(sourceEngineId);
            dto.setTargetEngineId(targetEngineId);
            dto.setSourcePath(sourcePath);
            dto.setTargetPath(targetPath);
            if (syncMode != null) dto.setSyncMode(SyncTask.SyncMode.valueOf(syncMode));
            if (transcodeEnabled != null) dto.setTranscodeEnabled(transcodeEnabled);
            if (targetFormat != null) dto.setTargetFormat(TargetFormat.valueOf(targetFormat));
            if (conflictStrategy != null) dto.setConflictStrategy(ConflictStrategy.valueOf(conflictStrategy));
            if (excludePatterns != null) dto.setExcludePatterns(excludePatterns);
            if (scheduleType != null) dto.setScheduleType(SyncTask.ScheduleType.valueOf(scheduleType));
            if (cronExpression != null) dto.setCronExpression(cronExpression);
            if (intervalSeconds != null) dto.setIntervalSeconds(intervalSeconds);

            Long taskId = manageService.create(dto).getId();
            manageService.executeManually(taskId);
            syncService.executeSyncTask(manageService.getEntity(taskId));
            return Map.of("taskId", taskId);
        });
    }
}
