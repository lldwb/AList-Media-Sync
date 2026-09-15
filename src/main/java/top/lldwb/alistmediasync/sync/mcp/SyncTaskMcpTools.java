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
import top.lldwb.alistmediasync.sync.dto.SyncTaskUpdateDTO;
import top.lldwb.alistmediasync.sync.entity.SyncTask;
import top.lldwb.alistmediasync.sync.service.SyncService;
import top.lldwb.alistmediasync.sync.service.SyncTaskManageService;

import java.util.Map;

/**
 * 同步任务模块 MCP 工具（FR-004）
 * <p>
 * 提供同步任务的查询/创建/更新/删除/手动触发/启停调度/执行历史工具。
 * 工具层仅做参数适配与结果封装，业务逻辑复用 {@link SyncTaskManageService} 与 {@link SyncService}（FR-009）。
 * </p>
 * <p>
 * {@code sync_task_execute} 采用异步提交模式（FR-014）：复用 {@code executeManually}（校验）+
 * {@code executeSyncTask}（{@code @Async} 执行），工具立即返回任务 ID，结果通过
 * {@code sync_task_get_executions} 轮询获取。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Component
@ConditionalOnProperty(name = "app.mcp.enabled", havingValue = "true")
public class SyncTaskMcpTools {

    private final SyncTaskManageService manageService;
    private final SyncService syncService;
    private final McpToolResult result;

    public SyncTaskMcpTools(SyncTaskManageService manageService, SyncService syncService, McpToolResult result) {
        this.manageService = manageService;
        this.syncService = syncService;
        this.result = result;
    }

    @McpTool(name = "sync_task_list", description = "列出所有同步任务")
    public McpSchema.CallToolResult syncTaskList() {
        return result.run("sync_task_list", manageService::listAll);
    }

    @McpTool(name = "sync_task_get", description = "按 ID 查询同步任务详情")
    public McpSchema.CallToolResult syncTaskGet(
            @McpToolParam(description = "同步任务 ID") Long id) {
        return result.run("sync_task_get", () -> manageService.getById(id));
    }

    @McpTool(name = "sync_task_create", description = "创建同步任务（创建后默认禁用，需通过 sync_task_enable 启用调度）")
    public McpSchema.CallToolResult syncTaskCreate(
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
        return result.run("sync_task_create", () -> {
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
            return manageService.create(dto);
        });
    }

    @McpTool(name = "sync_task_update", description = "更新同步任务（仅更新提供的字段）")
    public McpSchema.CallToolResult syncTaskUpdate(
            @McpToolParam(description = "同步任务 ID") Long id,
            @McpToolParam(description = "任务名称", required = false) String name,
            @McpToolParam(description = "源存储引擎 ID", required = false) Long sourceEngineId,
            @McpToolParam(description = "目标存储引擎 ID", required = false) Long targetEngineId,
            @McpToolParam(description = "源目录路径", required = false) String sourcePath,
            @McpToolParam(description = "目标目录路径", required = false) String targetPath,
            @McpToolParam(description = "同步模式：NEW_ONLY / FULL / MOVE", required = false) String syncMode,
            @McpToolParam(description = "是否启用同步后转码", required = false) Boolean transcodeEnabled,
            @McpToolParam(description = "目标转码格式：MP3 / MP4 / FLV", required = false) String targetFormat,
            @McpToolParam(description = "冲突处理策略：OVERWRITE / SKIP / RENAME", required = false) String conflictStrategy,
            @McpToolParam(description = "排除模式（换行分隔）", required = false) String excludePatterns,
            @McpToolParam(description = "调度类型：MANUAL / CRON / INTERVAL", required = false) String scheduleType,
            @McpToolParam(description = "Cron 表达式", required = false) String cronExpression,
            @McpToolParam(description = "间隔秒数", required = false) Integer intervalSeconds) {
        return result.run("sync_task_update", () -> {
            SyncTaskUpdateDTO dto = new SyncTaskUpdateDTO();
            dto.setName(name);
            dto.setSourceEngineId(sourceEngineId);
            dto.setTargetEngineId(targetEngineId);
            dto.setSourcePath(sourcePath);
            dto.setTargetPath(targetPath);
            if (syncMode != null) dto.setSyncMode(SyncTask.SyncMode.valueOf(syncMode));
            dto.setTranscodeEnabled(transcodeEnabled);
            if (targetFormat != null) dto.setTargetFormat(TargetFormat.valueOf(targetFormat));
            if (conflictStrategy != null) dto.setConflictStrategy(ConflictStrategy.valueOf(conflictStrategy));
            dto.setExcludePatterns(excludePatterns);
            if (scheduleType != null) dto.setScheduleType(SyncTask.ScheduleType.valueOf(scheduleType));
            dto.setCronExpression(cronExpression);
            dto.setIntervalSeconds(intervalSeconds);
            return manageService.update(id, dto);
        });
    }

    @McpTool(name = "sync_task_delete", description = "删除同步任务（同时取消定时调度）")
    public McpSchema.CallToolResult syncTaskDelete(
            @McpToolParam(description = "同步任务 ID") Long id) {
        return result.run("sync_task_delete", () -> {
            manageService.delete(id);
            return Map.of("deleted", true);
        });
    }

    @McpTool(name = "sync_task_execute", description = "手动触发同步任务执行（异步提交，立即返回任务 ID，结果通过 sync_task_get_executions 查询）")
    public McpSchema.CallToolResult syncTaskExecute(
            @McpToolParam(description = "同步任务 ID") Long id) {
        return result.run("sync_task_execute", () -> {
            manageService.executeManually(id);
            syncService.executeSyncTask(manageService.getEntity(id));
            return Map.of("taskId", id);
        });
    }

    @McpTool(name = "sync_task_enable", description = "启用同步任务的定时调度")
    public McpSchema.CallToolResult syncTaskEnable(
            @McpToolParam(description = "同步任务 ID") Long id) {
        return result.run("sync_task_enable", () -> manageService.enable(id));
    }

    @McpTool(name = "sync_task_disable", description = "禁用同步任务的定时调度")
    public McpSchema.CallToolResult syncTaskDisable(
            @McpToolParam(description = "同步任务 ID") Long id) {
        return result.run("sync_task_disable", () -> manageService.disable(id));
    }

    @McpTool(name = "sync_task_get_executions", description = "查询同步任务的执行历史（按开始时间倒序）")
    public McpSchema.CallToolResult syncTaskGetExecutions(
            @McpToolParam(description = "同步任务 ID") Long id) {
        return result.run("sync_task_get_executions", () -> manageService.getExecutions(id));
    }
}
