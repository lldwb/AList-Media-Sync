package top.lldwb.alistmediasync.util;

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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 服务地址日志输出器
 * <p>
 * 在应用启动完成后自动收集并输出当前服务的所有可访问地址，
 * 包括本地回环地址（localhost、127.0.0.1）和所有活跃网络接口的地址，
 * 附带服务端口号和上下文路径，便于开发者快速定位服务入口和调试。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Slf4j
@Component
public class ServerAddressLogger {

    /**
     * 监听 ApplicationReadyEvent，确保在 Web 服务器完全启动后输出地址信息。
     * <p>
     * 输出示例：
     * <pre>
     * ========================================
     *   服务可访问地址列表
     * ========================================
     *   [1] http://localhost:8080
     *   [2] http://127.0.0.1:8080
     *   [3] http://192.168.1.100:8080
     * ========================================
     * </pre>
     * </p>
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

        List<String> urls = collectAddresses(port, ctxPath);
        logServerUrls(urls);
    }

    /**
     * 解析上下文路径。
     * 当未设置或设置为 "/" 时视为根路径。
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
     * 收集所有可访问地址：固定包含 localhost 和 127.0.0.1，再枚举所有活跃非回环网络接口地址。
     */
    private List<String> collectAddresses(int port, String ctxPath) {
        Set<String> uniqueUrls = new LinkedHashSet<>(); // 保持插入顺序，避免重复

        // 固定输出 localhost 和 127.0.0.1（IPv4 回环）
        uniqueUrls.add(formatUrl("localhost", port, ctxPath));
        uniqueUrls.add(formatUrl("127.0.0.1", port, ctxPath));

        // 枚举所有网络接口
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    NetworkInterface ni = interfaces.nextElement();
                    if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) {
                        continue;
                    }
                    Enumeration<InetAddress> inetAddresses = ni.getInetAddresses();
                    while (inetAddresses.hasMoreElements()) {
                        InetAddress addr = inetAddresses.nextElement();
                        if (addr.isLoopbackAddress()) {
                            continue;
                        }
                        String host = addr instanceof Inet4Address
                            ? addr.getHostAddress()
                            : "[" + addr.getHostAddress() + "]";
                        uniqueUrls.add(formatUrl(host, port, ctxPath));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("枚举网络接口时发生异常：{}", e.getMessage());
        }

        return new ArrayList<>(uniqueUrls);
    }

    /**
     * 拼接完整访问 URL。
     */
    private String formatUrl(String host, int port, String ctxPath) {
        return "http://" + host + ":" + port + ctxPath;
    }

    /**
     * 以醒目的格式输出地址列表到控制台日志。
     */
    private void logServerUrls(List<String> urls) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("\n");
        sb.append("========================================\n");
        sb.append("  服务可访问地址列表\n");
        sb.append("========================================\n");
        int idx = 1;
        for (String url : urls) {
            sb.append("  [").append(idx++).append("] ").append(url).append("\n");
        }
        sb.append("========================================");
        log.info(sb.toString());
    }
}
