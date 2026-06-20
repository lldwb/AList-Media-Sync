package top.lldwb.alistmediasync.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 服务地址日志输出器
 * <p>
 * 在应用启动完成后自动收集并输出当前服务的所有可访问地址，
 * 附带应用名称、版本号、功能路径列表和醒目的启动成功横幅格式。
 * 支持容器环境检测（Docker/Podman 等 OCI 运行时），在容器环境下
 * 输出差异化的地址信息并提示端口映射关系。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Slf4j
@Component
public class ServerAddressLogger {

    /** 功能路径映射：路径说明 → URL 路径 */
    private static final Map<String, String> FUNCTIONAL_PATHS = new LinkedHashMap<>();
    static {
        FUNCTIONAL_PATHS.put("管理界面", "/app/");
        FUNCTIONAL_PATHS.put("API 根路径", "/api/");
        FUNCTIONAL_PATHS.put("健康检查", "/actuator/health");
        FUNCTIONAL_PATHS.put("H2 控制台", "/h2-console");
    }

    /**
     * 监听 ApplicationReadyEvent，确保在 Web 服务器完全启动后输出地址信息。
     *
     * @param event 应用就绪事件
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        ApplicationContext ctx = event.getApplicationContext();
        if (!(ctx instanceof WebServerApplicationContext wsCtx)) {
            return;
        }

        int port = wsCtx.getWebServer().getPort();
        ServerProperties serverProps = ctx.getBeanProvider(ServerProperties.class).getIfAvailable();
        String ctxPath = resolveContextPath(serverProps);

        // 获取应用名称和版本
        String appName = ctx.getEnvironment().getProperty("app.name", "AList-Media-Sync");
        String appVersion = ctx.getEnvironment().getProperty("app.version",
                getManifestVersion() != null ? getManifestVersion() : "未知版本");

        boolean isContainer = isRunningInContainer();

        // 收集地址（含网络接口名称）
        Map<String, String> addresses = collectAddressesWithName(port, ctxPath, isContainer);
        List<String> urls = new ArrayList<>(addresses.keySet());

        logBanner(appName, appVersion, urls, addresses, isContainer);
    }

    /**
     * 解析上下文路径。
     */
    private String resolveContextPath(ServerProperties props) {
        if (props == null || props.getServlet() == null) {
            return "";
        }
        String path = props.getServlet().getContextPath();
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return "";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path;
    }

    /**
     * 收集所有可访问地址及其网络接口名称。
     */
    private Map<String, String> collectAddressesWithName(int port, String ctxPath, boolean isContainer) {
        Map<String, String> urlsWithName = new LinkedHashMap<>();

        // 固定输出 localhost 和 127.0.0.1（IPv4 回环）
        String containerNote = isContainer ? "（容器内部端口）" : "";
        urlsWithName.put(formatUrl("localhost", port, ctxPath) + containerNote, "回环接口");
        urlsWithName.put(formatUrl("127.0.0.1", port, ctxPath) + containerNote, "回环接口");

        // 枚举所有网络接口
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    NetworkInterface ni = interfaces.nextElement();
                    // 跳过未启用、回环、虚拟接口，以及名称包含 docker/veth/tun/tap 的虚拟隧道接口
                    if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) {
                        continue;
                    }
                    String niName = ni.getName().toLowerCase();
                    if (niName.startsWith("docker") || niName.startsWith("veth")
                            || niName.startsWith("tun") || niName.startsWith("tap")
                            || niName.startsWith("br-")) {
                        continue;
                    }

                    Enumeration<InetAddress> inetAddresses = ni.getInetAddresses();
                    while (inetAddresses.hasMoreElements()) {
                        InetAddress addr = inetAddresses.nextElement();
                        if (addr.isLoopbackAddress()) {
                            continue;
                        }
                        // 仅收集 IPv4 地址
                        if (addr instanceof Inet4Address) {
                            String host = addr.getHostAddress();
                            String displayName = ni.getDisplayName() != null ? ni.getDisplayName() : ni.getName();
                            urlsWithName.put(formatUrl(host, port, ctxPath) + containerNote, displayName);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("枚举网络接口时发生异常：{}，将仅显示回环地址", e.getMessage());
        }

        return urlsWithName;
    }

    /**
     * 拼接完整访问 URL。
     */
    private String formatUrl(String host, int port, String ctxPath) {
        return "http://" + host + ":" + port + ctxPath;
    }

    /**
     * 从 Manifest 获取应用版本号。
     */
    private String getManifestVersion() {
        Package pkg = getClass().getPackage();
        if (pkg != null && pkg.getImplementationVersion() != null) {
            return pkg.getImplementationVersion();
        }
        return null;
    }

    /**
     * 检测是否运行在容器环境。
     * <p>
     * 使用多条件叠加判断策略：
     * ① 检查 /.dockerenv 文件存在性（Docker 标记文件）；
     * ② 检查 DOCKER_CONTAINER 环境变量；
     * ③ 检查 /proc/1/cgroup 中是否包含 docker/containerd 关键字。
     * </p>
     */
    public static boolean isRunningInContainer() {
        // 方法1：检查 .dockerenv 标记文件
        if (Files.exists(Path.of("/.dockerenv"))) {
            return true;
        }

        // 方法2：检查环境变量
        if (System.getenv("DOCKER_CONTAINER") != null) {
            return true;
        }

        // 方法3：检查 /proc/1/cgroup
        try {
            String cgroup = Files.readString(Path.of("/proc/1/cgroup"));
            return cgroup.contains("docker") || cgroup.contains("containerd");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 以醒目的横幅格式输出地址列表到控制台日志。
     */
    private void logBanner(String appName, String appVersion, List<String> urls,
                           Map<String, String> urlsWithName, boolean isContainer) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("\n");
        sb.append("========================================\n");

        // 应用名称和版本
        sb.append("  ").append(appName).append(" v").append(appVersion).append("\n");

        // 启动成功状态
        if (isContainer) {
            sb.append("  服务启动成功！（容器环境）\n");
        } else {
            sb.append("  服务启动成功！\n");
        }

        sb.append("========================================\n");

        // 地址列表标题
        if (isContainer) {
            sb.append("  容器内部地址：\n");
        } else {
            sb.append("  可访问地址：\n");
        }

        // 输出地址（去重后的地址列表）
        Set<String> seenUrls = new LinkedHashSet<>();
        int idx = 1;
        for (String url : urls) {
            String baseUrl = url.replace("（容器内部端口）", "");
            if (seenUrls.add(baseUrl)) {
                String name = urlsWithName.getOrDefault(url, "");
                if (!name.isEmpty() && !name.equals("回环接口")) {
                    sb.append("  [").append(idx++).append("] ").append(url).append(" (").append(name).append(")\n");
                } else {
                    sb.append("  [").append(idx++).append("] ").append(url).append("\n");
                }
            }
        }

        sb.append("========================================\n");

        // 主要功能路径
        sb.append("  主要功能路径：\n");
        // 使用 localhost 地址作为基准
        String baseUrl = urls.isEmpty() ? "http://localhost:8080" : urls.get(0).replace("（容器内部端口）", "");
        for (Map.Entry<String, String> entry : FUNCTIONAL_PATHS.entrySet()) {
            String pathLabel = entry.getKey();
            String path = entry.getValue();
            sb.append("  ").append(padRight(pathLabel, 12))
              .append(baseUrl.replace("（容器内部端口）", "")).append(path).append("\n");
        }

        sb.append("========================================");

        // 容器环境额外提示
        if (isContainer) {
            sb.append("\n");
            sb.append("  注意：容器内部端口可能映射到宿主机不同端口，\n");
            sb.append("  请参阅 docker-compose.yml 中的 ports 配置。\n");
            sb.append("========================================");
        }

        log.info(sb.toString());
    }

    /**
     * 右侧填充空格以达到固定宽度。
     */
    private String padRight(String str, int width) {
        if (str.length() >= width) {
            return str;
        }
        StringBuilder padded = new StringBuilder(str);
        while (padded.length() < width) {
            padded.append(' ');
        }
        return padded.toString();
    }
}
