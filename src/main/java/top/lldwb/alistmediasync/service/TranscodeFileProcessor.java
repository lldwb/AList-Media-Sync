package top.lldwb.alistmediasync.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import top.lldwb.alistmediasync.client.AListClient;
import top.lldwb.alistmediasync.config.AppProperties;
import top.lldwb.alistmediasync.entity.*;
import top.lldwb.alistmediasync.repository.TranscodeTaskRepository;
import top.lldwb.alistmediasync.util.DiskSpaceChecker;
import top.lldwb.alistmediasync.util.TempFileManager;
import ws.schild.jave.Encoder;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;
import ws.schild.jave.encode.VideoAttributes;
import ws.schild.jave.progress.EncoderProgressListener;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

/**
 * 转码文件处理器
 * <p>
 * 负责单个文件的异步转码执行：下载源文件 → FFmpeg 转码 → 磁盘检查 →
 * 临时文件管理 → 上传到目标 AList。
 * </p>
 * <p>
 * 从 {@link TranscodeService} 提取（方案 B），彻底消除
 * SyncService ↔ TranscodeService 的循环依赖。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranscodeFileProcessor {

    private final TranscodeTaskRepository repository;
    private final AListClient alistClient;
    private final AppProperties appProperties;

    /** 并发转码信号量（由配置 maxConcurrentTranscode 控制上限） */
    private Semaphore semaphore;

    /**
     * 初始化信号量
     */
    @PostConstruct
    void init() {
        int maxConcurrent = appProperties.getTranscode().getMaxConcurrentTranscode();
        this.semaphore = new Semaphore(maxConcurrent);
        log.info("转码文件处理器已初始化，最大并发数：{}", maxConcurrent);
    }

    /**
     * 异步转码单个文件（由 TranscodeService 编排层调用）
     *
     * @param candidate     转码候选文件
     * @param targetFormat  目标格式
     * @param tempSuffix    临时文件后缀
     * @param tempDir       临时文件目录
     * @param targetEngine  目标存储引擎
     * @param syncTask      关联同步任务（可为 null）
     * @param execution     任务执行记录
     * @return 异步转码结果 Future
     */
    @Async("transcodeExecutor")
    public CompletableFuture<TranscodeResult> process(
            TranscodeCandidate candidate,
            TranscodeTask.TargetFormat targetFormat,
            String tempSuffix,
            Path tempDir,
            StorageEngine targetEngine,
            SyncTask syncTask,
            TaskExecution execution) {

        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CompletableFuture.completedFuture(
                    new TranscodeResult(candidate.name(), false, "线程被中断"));
        }

        try {
            TranscodeResult result = doProcess(candidate, targetFormat, tempSuffix, tempDir,
                    targetEngine, syncTask, execution);
            return CompletableFuture.completedFuture(result);
        } finally {
            semaphore.release();
        }
    }

    // ================================================================
    // 核心转码逻辑
    // ================================================================

    private TranscodeResult doProcess(TranscodeCandidate candidate,
                                       TranscodeTask.TargetFormat targetFormat,
                                       String tempSuffix,
                                       Path tempDir,
                                       StorageEngine targetEngine,
                                       SyncTask syncTask,
                                       TaskExecution execution) {

        Path tempFile = null;
        Path finalFile = null;
        Path sourceTempFile = null;
        TranscodeTask transcodeTask = null;

        try {
            // 1. 创建 TranscodeTask 记录
            transcodeTask = new TranscodeTask();
            transcodeTask.setSyncTask(syncTask);
            transcodeTask.setSourceFilePath(candidate.fullPath());
            transcodeTask.setTargetFilePath(candidate.targetPath());
            transcodeTask.setTargetFormat(targetFormat);
            transcodeTask.setStatus(TranscodeTask.TranscodeStatus.TRANSCODING);
            if (targetEngine != null) {
                transcodeTask.setTargetEngineId(targetEngine.getId());
            }
            transcodeTask = repository.save(transcodeTask);

            // 2. 下载源文件到临时位置
            sourceTempFile = Files.createTempFile("alist-src-",
                    "." + candidate.format().toLowerCase());
            StorageEngine sourceEngine = TranscodeCandidate.sourceEngine != null
                    ? TranscodeCandidate.sourceEngine : targetEngine;
            try (InputStream in = alistClient.downloadFile(
                    sourceEngine.getBaseUrl(), sourceEngine.getEncryptedToken(),
                    candidate.fullPath())) {
                if (in == null) throw new IOException("下载源文件失败：" + candidate.fullPath());
                Files.copy(in, sourceTempFile,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            // 3. 获取源文件时长用于磁盘空间预估
            long durationMs = 0;
            try {
                MultimediaObject mmObj = new MultimediaObject(sourceTempFile.toFile());
                durationMs = mmObj.getInfo().getDuration();
            } catch (Exception e) {
                log.warn("无法获取源文件时长：{}，跳过空间预估", candidate.name());
            }

            // 4. 磁盘空间检查
            long estimatedSize = DiskSpaceChecker.estimateOutputSize(durationMs, 128000);
            DiskSpaceChecker.checkSufficient(tempDir, estimatedSize);

            // 5. 创建临时输出文件
            String outputExt = targetFormat.name().toLowerCase();
            tempFile = TempFileManager.createTempFile(tempDir, candidate.name(), tempSuffix);
            transcodeTask.setTempFilePath(tempFile.toString());
            repository.save(transcodeTask);

            // 6. 构建 JAVE2 转码参数
            EncodingAttributes attrs = buildEncodingAttributes(targetFormat);

            // 7. 执行转码（带进度监听）
            log.info("开始转码：{} ({} -> {})", candidate.name(), candidate.format(), outputExt);
            Encoder encoder = new Encoder();
            encoder.encode(new MultimediaObject(sourceTempFile.toFile()),
                    tempFile.toFile(), attrs,
                    new TranscodeProgressListener(transcodeTask));

            // 8. 转码成功 — 重命名去掉临时后缀
            finalFile = TempFileManager.renameToFinal(tempFile, outputExt);
            transcodeTask.setTempFilePath(finalFile.toString());
            transcodeTask.setStatus(TranscodeTask.TranscodeStatus.UPLOADING);
            repository.save(transcodeTask);

            // 9. 上传到目标 AList
            String targetFileName = getOutputName(candidate.name());
            String remotePath = targetFileName.contains("/")
                    ? candidate.targetPath().replace(candidate.name(), targetFileName)
                    : concatDirAndName(getDirPath(candidate.targetPath()), targetFileName);

            try (InputStream fileIn = Files.newInputStream(finalFile)) {
                alistClient.uploadFile(targetEngine.getBaseUrl(),
                        targetEngine.getEncryptedToken(),
                        remotePath, fileIn, Files.size(finalFile));
            }

            // 10. 上传成功 → 删除本地文件
            TempFileManager.deleteQuietly(finalFile);
            transcodeTask.setStatus(TranscodeTask.TranscodeStatus.COMPLETED);
            transcodeTask.setProgress(1000);
            repository.save(transcodeTask);

            // 清理源临时文件
            TempFileManager.deleteQuietly(sourceTempFile);

            log.info("转码完成：{} -> {}", candidate.name(), remotePath);
            return new TranscodeResult(candidate.name(), true, null);

        } catch (Exception e) {
            log.error("转码失败：{} — {}", candidate.name(), e.getMessage(), e);

            if (transcodeTask != null) {
                transcodeTask.setStatus(TranscodeTask.TranscodeStatus.FAILED);
                transcodeTask.setErrorMessage(e.getMessage());
                // 保留 finalFile 路径用于手动重试
                if (finalFile != null) {
                    transcodeTask.setTempFilePath(finalFile.toString());
                }
                repository.save(transcodeTask);
            }

            return new TranscodeResult(candidate.name(), false, e.getMessage());
        }
    }

    // ================================================================
    // 转码参数构建
    // ================================================================

    private EncodingAttributes buildEncodingAttributes(TranscodeTask.TargetFormat targetFormat) {
        EncodingAttributes attrs = new EncodingAttributes();

        switch (targetFormat) {
            case MP3 -> {
                attrs.setOutputFormat("mp3");
                AudioAttributes audio = new AudioAttributes();
                audio.setCodec("libmp3lame");
                audio.setBitRate(128000);
                audio.setChannels(2);
                audio.setSamplingRate(44100);
                attrs.setAudioAttributes(audio);
            }
            case MP4 -> {
                attrs.setOutputFormat("mp4");
                VideoAttributes video = new VideoAttributes();
                video.setCodec("libx264");
                attrs.setVideoAttributes(video);
                AudioAttributes audio = new AudioAttributes();
                audio.setCodec("aac");
                audio.setBitRate(128000);
                audio.setChannels(2);
                audio.setSamplingRate(44100);
                attrs.setAudioAttributes(audio);
            }
            case FLV -> {
                attrs.setOutputFormat("flv");
                VideoAttributes video = new VideoAttributes();
                video.setCodec("flv");
                attrs.setVideoAttributes(video);
                AudioAttributes audio = new AudioAttributes();
                audio.setCodec("libmp3lame");
                audio.setBitRate(128000);
                audio.setChannels(2);
                audio.setSamplingRate(44100);
                attrs.setAudioAttributes(audio);
            }
        }

        return attrs;
    }

    // ================================================================
    // 进度持久化
    // ================================================================

    /**
     * 转码进度监听器
     */
    private class TranscodeProgressListener implements EncoderProgressListener {

        private final TranscodeTask task;
        private int lastSavedProgress = 0;

        TranscodeProgressListener(TranscodeTask task) {
            this.task = task;
        }

        @Override
        public void progress(int permil) {
            task.setProgress(permil);
            // 每 5% 步进一次，避免频繁写库
            if (permil - lastSavedProgress >= 50) {
                lastSavedProgress = permil;
                try {
                    repository.save(task);
                } catch (Exception e) {
                    log.debug("进度持久化失败（非关键）：{}", e.getMessage());
                }
            }
        }

        @Override
        public void sourceInfo(ws.schild.jave.info.MultimediaInfo info) {
            // 不实现额外的源信息处理
        }

        @Override
        public void message(String message) {
            // 不实现额外的消息处理
        }
    }

    // ================================================================
    // 辅助方法
    // ================================================================

    /** 获取转码后输出文件名 */
    private String getOutputName(String sourceName) {
        int dotIdx = sourceName.lastIndexOf('.');
        String baseName = dotIdx > 0 ? sourceName.substring(0, dotIdx) : sourceName;
        return baseName + ".mp3";
    }

    /** 获取目标目录路径 */
    private String getDirPath(String fullPath) {
        int idx = fullPath.lastIndexOf('/');
        return idx > 0 ? fullPath.substring(0, idx) : "";
    }

    /** 拼接目录和文件名 */
    private String concatDirAndName(String dir, String name) {
        if (dir == null || dir.isEmpty() || dir.equals("/")) return "/" + name;
        return dir.endsWith("/") ? dir + name : dir + "/" + name;
    }
}
