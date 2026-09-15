package top.lldwb.alistmediasync.common.service;

/**
 * 手动清理临时文件触发器（依赖倒置：由 ops 模块的 CleanupService 实现）
 * <p>
 * 转码模块的入口（HTTP 与 MCP）需要提供一个「手动清理残留转码临时文件」的能力，而该能力由
 * {@code ops} 模块的 {@code CleanupService} 提供。若入口层直接依赖 {@code ops} 模块的
 * {@code CleanupService}，会使 {@code transcode} 依赖 {@code ops}，而 {@code ops} 是跨模块运维
 * 聚合的顶层模块（已依赖 {@code transcode}），从而形成 {@code ops ↔ transcode} 循环依赖。
 * 因此把该调用点抽象为本接口：
 * </p>
 * <ul>
 *   <li><b>定义方</b>：{@code common} 模块（本接口），不依赖任何业务模块；</li>
 *   <li><b>实现方</b>：{@code ops} 模块的 {@code CleanupService}（依赖倒置：实现类依赖接口）；</li>
 *   <li><b>调用方</b>：{@code transcode} 模块的两个入口——{@code TranscodeTaskController} 的
 *       {@code DELETE /api/transcode-tasks/cleanup-temp} 端点与 {@code TranscodeTaskMcpTools}
 *       的 {@code transcode_task_cleanup_temp} 工具。</li>
 * </ul>
 * <p>
 * 行为约定：调用方与实现方之间是<b>同步的直接方法调用</b>（非 Spring 事件、非异步），
 * 返回值与异常语义保持不变。
 * </p>
 *
 * @author AList-Media-Sync
 */
public interface TempFileCleanupTrigger {

    /**
     * 手动清理残留的转码临时文件，并返回本次清理的文件数量。
     * <p>
     * 清理范围为配置的转码临时目录（{@code app.transcode.temp-dir}）下、文件名以配置的临时后缀
     * （{@code app.transcode.temp-suffix}）结尾或以 {@code src-} / {@code out-} 开头的普通文件；
     * 单个文件删除失败只记录告警日志、不中断遍历。
     * </p>
     *
     * @return 本次匹配并尝试删除的文件数量（单个文件删除失败仍计入该数量）；临时目录不存在时返回 {@code 0}
     * @throws RuntimeException 扫描临时目录失败时抛出
     */
    long manualCleanup();
}
