package top.lldwb.alistmediasync.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.lldwb.alistmediasync.config.AppProperties;
import top.lldwb.alistmediasync.repository.TaskExecutionRepository;
import top.lldwb.alistmediasync.repository.WebhookEventRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 清理服务
 * <p>
 * 负责：
 * <ul>
 *   <li>定时清理过期记录（每天凌晨 3 点）</li>
 *   <li>启动时清理残留临时文件</li>
 *   <li>手动清理接口</li>
 * </ul>
 * </p>
 *
 * @author AList-Media-Sync
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CleanupService {

    private final TaskExecutionRepository taskExecutionRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final AppProperties appProperties;

    /**
     * 定时清理过期记录
     * 每天凌晨 3 点执行
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanExpiredRecords() {
        int retentionDays = appProperties.getRetentionDays();
        var cutoff = java.time.LocalDateTime.now().minusDays(retentionDays);
        log.info("开始清理过期记录（保留 {} 天，截止 {}）...", retentionDays, cutoff);

        int deletedExecutions = taskExecutionRepository.deleteByCreatedAtBefore(cutoff);
        int deletedEvents = webhookEventRepository.deleteByCreatedAtBefore(cutoff);

        log.info("过期记录清理完成：删除 {} 条执行记录，{} 条 Webhook 事件", deletedExecutions, deletedEvents);
    }

    /**
     * 应用启动后清理残留临时文件
     * 无条件删除临时目录中所有带配置后缀的文件。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void startupCleanup() {
        String tempDirPath = appProperties.getTranscode().getTempDir();
        String suffix = appProperties.getTranscode().getTempSuffix();
        Path tempDir = Path.of(tempDirPath);

        try {
            if (!Files.exists(tempDir)) {
                Files.createDirectories(tempDir);
                top.lldwb.alistmediasync.util.TempFileManager.setDirectoryPermissions(tempDir);
                log.info("临时文件目录已创建：{}", tempDir);
                return;
            }

            long deletedCount = 0;
            try (var stream = Files.walk(tempDir)) {
                deletedCount = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(suffix))
                    .peek(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            log.warn("清理残留临时文件失败：{}，原因：{}", path, e.getMessage());
                        }
                    })
                    .count();
            }

            log.info("启动时清理残留临时文件完成，共清理 {} 个文件", deletedCount);
        } catch (IOException e) {
            log.error("临时文件目录访问失败：{}，原因：{}", tempDir, e.getMessage());
            throw new RuntimeException("无法创建或访问临时文件目录：" + tempDir, e);
        }
    }

    /**
     * 手动清理残留临时文件（供 Controller 调用）
     *
     * @return 清理的文件数
     */
    public long manualCleanup() {
        String tempDirPath = appProperties.getTranscode().getTempDir();
        String suffix = appProperties.getTranscode().getTempSuffix();
        Path tempDir = Path.of(tempDirPath);

        if (!Files.exists(tempDir)) {
            log.info("临时目录不存在，无需清理：{}", tempDir);
            return 0;
        }

        long deletedCount = 0;
        try (var stream = Files.walk(tempDir)) {
            deletedCount = stream
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(suffix))
                .peek(path -> {
                    try {
                        Files.delete(path);
                        log.debug("手动清理残留文件：{}", path);
                    } catch (IOException e) {
                        log.warn("手动清理残留文件失败：{}，原因：{}", path, e.getMessage());
                    }
                })
                .count();
        } catch (IOException e) {
            log.error("手动清理时扫描目录失败：{}", e.getMessage());
            throw new RuntimeException("清理临时文件失败：" + e.getMessage(), e);
        }

        log.info("手动清理残留临时文件完成，共清理 {} 个文件", deletedCount);
        return deletedCount;
    }
}
