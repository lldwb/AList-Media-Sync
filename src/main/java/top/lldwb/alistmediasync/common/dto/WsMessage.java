package top.lldwb.alistmediasync.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * WebSocket 消息 DTO
 * <p>
 * 通用 WebSocket 消息结构，采用增量变更模式：每次仅推送变更的实体和变更字段，
 * 前端负责将增量合并到本地状态，不推送全量列表。
 * </p>
 *
 * @param type      消息类型（MessageType 枚举值的字符串形式）
 * @param payload   增量数据载荷（仅含变更字段）
 * @param timestamp ISO 8601 时间戳，由 WsSessionManager 在推送时自动填充
 *
 * @author AList-Media-Sync
 */
@Schema(description = "WebSocket 推送消息")
public record WsMessage(
    @Schema(description = "消息类型（MessageType 枚举值的字符串形式）", example = "SYNC_TASK_UPDATED", requiredMode = Schema.RequiredMode.REQUIRED)
    String type,

    @Schema(description = "增量数据载荷（仅含变更字段）", example = "{\"id\":1,\"status\":\"RUNNING\"}", requiredMode = Schema.RequiredMode.REQUIRED)
    Object payload,

    @Schema(description = "ISO 8601 时间戳，由 WsSessionManager 在推送时自动填充", example = "2026-07-02T10:30:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
    String timestamp
) {}
