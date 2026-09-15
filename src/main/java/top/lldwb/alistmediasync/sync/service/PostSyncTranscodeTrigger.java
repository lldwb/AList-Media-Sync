package top.lldwb.alistmediasync.sync.service;

import top.lldwb.alistmediasync.execution.TaskExecution;
import top.lldwb.alistmediasync.sync.entity.SyncTask;

/**
 * 同步后置转码触发器（依赖倒置：由 transcode 模块实现）
 * <p>
 * 同步任务执行成功后需要按需触发一次「同步后置转码」。若 {@code SyncService} 直接依赖
 * {@code transcode} 模块的 {@code TranscodeService}，会使 {@code sync} 与 {@code transcode}
 * 的服务层互相 import、形成双向依赖（循环依赖）。因此把该调用点抽象为本接口：
 * </p>
 * <ul>
 *   <li><b>定义方</b>：{@code sync} 模块（本接口），只依赖 {@code SyncTask} 与 {@code TaskExecution}；</li>
 *   <li><b>实现方</b>：{@code transcode} 模块的 {@code TranscodeService}（依赖倒置：实现类依赖接口）；</li>
 *   <li><b>调用方</b>：{@code SyncService}，在同步任务成功且该任务启用了转码时注入并调用。</li>
 * </ul>
 * <p>
 * 行为约定：调用方与被实现方之间是<b>同步的直接方法调用</b>（非 Spring 事件、非异步），
 * 事务语义与调用时序保持不变。
 * </p>
 *
 * @author AList-Media-Sync
 */
public interface PostSyncTranscodeTrigger {

    /**
     * 触发同步后置转码。
     *
     * @param task      本次已同步成功的同步任务，提供源/目标引擎、路径、目标格式与冲突策略等转码所需参数
     * @param execution 触发本次后置转码的同步执行记录，用于把后续转码记录与触发它的同步执行关联起来
     */
    void trigger(SyncTask task, TaskExecution execution);
}
