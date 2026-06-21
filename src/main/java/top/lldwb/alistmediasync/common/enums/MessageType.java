package top.lldwb.alistmediasync.common.enums;

/**
 * WebSocket 消息类型枚举
 * <p>
 * 定义 WebSocket 推送消息的类型标识，前端根据 type 字段路由到不同的状态更新逻辑。
 * </p>
 *
 * @author AList-Media-Sync
 */
public enum MessageType {

    /** 同步任务进度变更 */
    SYNC_PROGRESS,

    /** 转码任务进度变更 */
    TRANSCODE_PROGRESS,

    /** 任务事件（创建/删除/完成） */
    TASK_EVENT,

    /** Webhook 事件接收/处理状态变更 */
    WEBHOOK_EVENT,

    /** 仪表板统计数据变更（2 秒防抖合并） */
    DASHBOARD_UPDATE
}
