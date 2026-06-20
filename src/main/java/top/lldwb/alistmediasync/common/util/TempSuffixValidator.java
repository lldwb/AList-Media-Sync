package top.lldwb.alistmediasync.common.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import top.lldwb.alistmediasync.common.config.AppProperties;

/**
 * 临时文件后缀配置校验器
 * <p>
 * 在应用启动阶段校验 app.transcode.temp-suffix 配置的合法性。
 * 校验失败（包含路径分隔符等非法字符）则抛出异常阻止 Spring 容器启动。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Slf4j
@Component
@Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public class TempSuffixValidator implements ApplicationRunner {

    private final AppProperties appProperties;

    @Override
    public void run(ApplicationArguments args) {
        AppProperties.Transcode transcode = appProperties.getTranscode();
        String rawSuffix = transcode.getTempSuffix();
        int maxLength = transcode.getMaxSuffixLength();

        // 1. null 或空字符串 → 回退默认值
        if (rawSuffix == null || rawSuffix.isEmpty()) {
            log.warn("临时文件后缀未配置，使用默认值：{}", ".tmp");
            transcode.setTempSuffix(".tmp");
            return;
        }

        // 2. 仅点号 → 回退默认值
        if (".".equals(rawSuffix.trim())) {
            log.warn("临时文件后缀仅为点号，使用默认值：{}", ".tmp");
            transcode.setTempSuffix(".tmp");
            return;
        }

        // 3. 仅空白字符 → 回退默认值
        if (rawSuffix.trim().isEmpty()) {
            log.warn("临时文件后缀仅包含空白字符，使用默认值：{}", ".tmp");
            transcode.setTempSuffix(".tmp");
            return;
        }

        // 4. 包含路径分隔符 → 拒绝启动
        if (rawSuffix.contains("/") || rawSuffix.contains("\\")) {
            throw new IllegalArgumentException(
                "临时文件后缀包含非法字符：[/, \\]。请在 application.yaml 中修改 app.transcode.temp-suffix 配置项。");
        }

        // 5. 包含上级目录遍历 → 拒绝启动
        if (rawSuffix.contains("..")) {
            throw new IllegalArgumentException(
                "临时文件后缀包含非法字符：[..]。请在 application.yaml 中修改 app.transcode.temp-suffix 配置项。");
        }

        // 6. 包含空字符 → 拒绝启动
        if (rawSuffix.contains("\0")) {
            throw new IllegalArgumentException(
                "临时文件后缀包含空字符。请在 application.yaml 中修改 app.transcode.temp-suffix 配置项。");
        }

        // 7. 长度截断
        if (rawSuffix.length() > maxLength) {
            String truncated = rawSuffix.substring(0, maxLength);
            log.warn("临时文件后缀超过最大长度 {}，已截断为：{}", maxLength, truncated);
            rawSuffix = truncated;
        }

        // 8. 补充点号前缀
        if (!rawSuffix.startsWith(".")) {
            rawSuffix = "." + rawSuffix;
        }

        transcode.setTempSuffix(rawSuffix);
        log.info("临时文件后缀配置有效：{}（最大长度：{}）", transcode.getTempSuffix(), maxLength);
    }
}
