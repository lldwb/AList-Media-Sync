package top.lldwb.alistmediasync.support;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * 端到端测试动态端口分配管理器
 * <p>
 * 使用 {@link ServerSocket#ServerSocket(int)} 探测空闲端口，供 AList 实例和系统实例使用。
 * 探测失败/端口耗尽时抛 {@link IllegalStateException} 并输出排查建议。
 * 分配结果写入 {@code scripts/e2e/data/ports.json}（容忍目录不存在）。
 * </p>
 * <p>
 * 注意：本类是纯 Java 工具类，不启动真实 AList 二进制（二进制启动由 scripts/e2e/*.ps1 完成）。
 * E2E 测试中通过系统属性注入动态端口到 application-e2e.yaml 的 server.port 与 alist.base-url。
 * </p>
 *
 * @author AList-Media-Sync
 */
public class E2ELifecycleManager {

    /** 最大端口探测重试次数 */
    private static final int MAX_RETRIES = 100;

    private final int alistPort;
    private final int systemPort;

    /**
     * 构造管理器，立即探测两个空闲端口并写入 ports.json
     */
    public E2ELifecycleManager() {
        this.alistPort = probeFreePort();
        this.systemPort = probeFreePort();
        writePortsFile();
    }

    /**
     * 获取 AList 实例端口
     *
     * @return 空闲端口号
     */
    public int getAlistPort() {
        return alistPort;
    }

    /**
     * 获取系统实例端口
     *
     * @return 空闲端口号
     */
    public int getSystemPort() {
        return systemPort;
    }

    /**
     * 探测空闲端口
     * <p>
     * 使用 {@link ServerSocket#ServerSocket(int)} 绑定端口 0 让系统自动分配空闲端口，
     * 立即关闭后返回端口号。最多重试 {@link #MAX_RETRIES} 次。
     * </p>
     *
     * @return 空闲端口号
     * @throws IllegalStateException 端口耗尽时抛出，附排查建议
     */
    private int probeFreePort() {
        for (int i = 0; i < MAX_RETRIES; i++) {
            try (ServerSocket ss = new ServerSocket(0)) {
                return ss.getLocalPort();
            } catch (IOException e) {
                // 重试下一次
            }
        }
        throw new IllegalStateException(
            "无法分配空闲端口：已尝试 " + MAX_RETRIES + " 次均失败。\n" +
            "请排查：\n" +
            "  1. 系统端口是否被大量占用（netstat -ano | find /c \"LISTEN\"）\n" +
            "  2. 防火墙/安全软件是否拦截了临时端口分配\n" +
            "  3. 是否达到操作系统临时端口上限（Windows: netsh int ipv4 show dynamicport tcp）\n" +
            "非零退出，测试终止。");
    }

    /**
     * 将端口信息写入 {@code scripts/e2e/data/ports.json}
     * <p>
     * 目录不存在时自动创建；写入失败时静默容忍（不影响测试流程）。
     * </p>
     */
    private void writePortsFile() {
        try {
            Path dataDir = Paths.get("scripts", "e2e", "data");
            Files.createDirectories(dataDir);
            Path portsFile = dataDir.resolve("ports.json");
            String json = String.format(
                "{\"alistPort\":%d,\"systemPort\":%d}",
                alistPort, systemPort);
            Files.writeString(portsFile, json);
        } catch (IOException e) {
            // 容忍目录不存在或写入失败，不影响测试
        }
    }

    /**
     * 判断是否启用录播姬
     * <p>
     * 通过 {@code -Ddanmuji.enabled=true} 系统属性控制。
     * </p>
     *
     * @return true 表示启用录播姬
     */
    public static boolean isDanmujiEnabled() {
        return Boolean.getBoolean("danmuji.enabled");
    }
}