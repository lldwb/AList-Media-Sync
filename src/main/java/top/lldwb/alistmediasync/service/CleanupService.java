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
import java.time.Instant;
import java.util.stream.Stream;

/**
 * 清理服务
 * <p>
 * 负责：
 * <ul>
 *   <li>定时清理过期记录（每天凌晨 3 点）</li>
 *   <li>启动时清理残留临时文件（含孤立转码临时文件）</li>
 *   <li>定时清理超过 24 小时的孤立临时文件</li>
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

    /** 孤立临时文件最大保存时间（24 小时） */
    private static final long ORPHAN_FILE_MAX_AGE_MS = 24 * 60 * 60 * 1000L;

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
     * 定时清理孤立临时文件（每 4 小时执行）
     * 清理超过 24 小时的转码源文件和输出文件
     */
    @Scheduled(cron = "0 0 */4 * * ?")
    public void cleanOrphanedTempFiles() {
        String tempDirPath = appProperties.getTranscode().getTempDir();
        Path tempDir = Path.of(tempDirPath);

        if (!Files.exists(tempDir)) {
            return;
        }

        long cutoffTime = System.currentTimeMillis() - ORPHAN_FILE_MAX_AGE_MS;
        long deletedCount = 0;

        try (Stream<Path> stream = Files.walk(tempDir)) {
            deletedCount = stream
                .filter(Files::isRegularFile)
                .filter(path -> {
                    try {
                        return Files.getLastModifiedTime(path).toMillis() < cutoffTime;
                    } catch (IOException e) {
                        return false;
                    }
                })
                .peek(path -> {
                    try {
                        Files.delete(path);
                        log.debug("清理孤立临时文件：{}", path);
                    } catch (IOException e) {
                        log.warn("清理孤立临时文件失败：{}，原因：{}", path, e.getMessage());
                    }
                })
                .count();
        } catch (IOException e) {
            log.error("扫描孤立临时文件目录失败：{}", e.getMessage());
        }

        if (deletedCount > 0) {
            log.info("孤立临时文件清理完成，共清理 {} 个超过 24 小时的文件", deletedCount);
        }
    }

    /**
     * 应用启动后清理残留临时文件
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
                    .filter(path -> path.getFileName().toString().endsWith(suffix)
                        || path.getFileName().toString().startsWith("src-")
                        || path.getFileName().toString().startsWith("out-"))
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
                .filter(path -> path.getFileName().toString().endsWith(suffix)
                    || path.getFileName().toString().startsWith("src-")
                    || path.getFileName().toString().startsWith("out-"))
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
