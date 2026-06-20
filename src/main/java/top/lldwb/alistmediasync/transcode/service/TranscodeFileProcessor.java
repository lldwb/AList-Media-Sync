package top.lldwb.alistmediasync.transcode.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import top.lldwb.alistmediasync.common.config.AppProperties;
import top.lldwb.alistmediasync.storage.entity.StorageEngine;
import top.lldwb.alistmediasync.storage.service.StorageEngineService;
import top.lldwb.alistmediasync.storage.service.engine.StorageEngineStrategy;
import top.lldwb.alistmediasync.sync.entity.SyncTask;
import top.lldwb.alistmediasync.sync.entity.TaskExecution;
import top.lldwb.alistmediasync.transcode.entity.TranscodeTask;
import top.lldwb.alistmediasync.transcode.repository.TranscodeTaskRepository;
import top.lldwb.alistmediasync.common.util.DiskSpaceChecker;
import top.lldwb.alistmediasync.common.util.TempFileManager;
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
 * 临时文件管理 → 上传到目标存储引擎。
 * 采用三步独立步骤（downloadStep / transcodeStep / uploadStep），
 * 每步可独立失败和重试。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("deprecation") // JAVE2 3.5.0 Encoder/Attributes API
public class TranscodeFileProcessor {

    private final TranscodeTaskRepository repository;
    private final StorageEngineService storageEngineService;
    private final AppProperties appProperties;

    /** 并发转码信号量（由配置 maxConcurrentTranscode 控制上限） */
    private Semaphore semaphore;

    @PostConstruct
    void init() {
        int maxConcurrent = appProperties.getTranscode().getMaxConcurrentTranscode();
        this.semaphore = new Semaphore(maxConcurrent);
        log.info("转码文件处理器已初始化，最大并发数：{}", maxConcurrent);
    }

    /**
     * 异步转码单个文件（由 TranscodeService 编排层调用）
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

    /**
     * 执行 FFmpeg 转码（由 TranscodeService 三步流程中的 transcodeStep 调用）
     *
     * @param sourceFile   源文件路径
     * @param outputFile   输出文件路径
     * @param targetFormat 目标格式
     * @param bitrate      音频比特率（bps）
     * @param task         转码任务（用于进度持久化）
     */
    public void doTranscode(Path sourceFile, Path outputFile,
                             TranscodeTask.TargetFormat targetFormat, int bitrate,
                             TranscodeTask task) {
        // 磁盘空间检查
        long durationMs = 0;
        try {
            MultimediaObject mmObj = new MultimediaObject(sourceFile.toFile());
            durationMs = mmObj.getInfo().getDuration();
        } catch (Exception e) {
            log.warn("无法获取源文件时长：{}，跳过空间预估", sourceFile);
        }
        long estimatedSize = DiskSpaceChecker.estimateOutputSize(durationMs, bitrate);
        DiskSpaceChecker.checkSufficient(outputFile.getParent(), estimatedSize);

        // 构建转码参数（codec=null 让 FFmpeg 自动选择编解码器）
        EncodingAttributes attrs = buildEncodingAttributes(targetFormat, bitrate);

        // 执行转码
        log.info("开始转码：{} -> {} (格式: {}, 码率: {}bps)",
            sourceFile.getFileName(), outputFile.getFileName(), targetFormat, bitrate);
        try {
            Encoder encoder = new Encoder();
            encoder.encode(new MultimediaObject(sourceFile.toFile()),
                outputFile.toFile(), attrs,
                new TranscodeProgressListener(task));
        } catch (Exception e) {
            throw new RuntimeException("FFmpeg 转码失败：" + e.getMessage(), e);
        }
    }

    // ================================================================
    // 核心转码逻辑（三步流程）
    // ================================================================

    private TranscodeResult doProcess(TranscodeCandidate candidate,
                                       TranscodeTask.TargetFormat targetFormat,
                                       String tempSuffix,
                                       Path tempDir,
                                       StorageEngine targetEngine,
                                       SyncTask syncTask,
                                       TaskExecution execution) {

        Path sourceTempFile = null;
        Path outputTempFile = null;
        TranscodeTask transcodeTask = null;

        try {
            // 1. 创建 TranscodeTask 记录
            transcodeTask = new TranscodeTask();
            transcodeTask.setSyncTask(syncTask);
            transcodeTask.setSourceFilePath(candidate.fullPath());
            transcodeTask.setTargetFilePath(candidate.targetPath());
            transcodeTask.setTargetFormat(targetFormat);
            transcodeTask.setStatus(TranscodeTask.TranscodeStatus.DOWNLOADING);
            if (targetEngine != null) {
                transcodeTask.setTargetEngineId(targetEngine.getId());
            }
            transcodeTask = repository.save(transcodeTask);

            // 步骤 1：下载源文件
            sourceTempFile = downloadStep(candidate, transcodeTask);

            // 步骤 2：转码
            String outputExt = targetFormat.name().toLowerCase();
            outputTempFile = TempFileManager.createTempFile(tempDir, candidate.name(), tempSuffix);
            transcodeTask.setStatus(TranscodeTask.TranscodeStatus.TRANSCODING);
            transcodeTask.setTempSourcePath(sourceTempFile.toString());
            repository.save(transcodeTask);

            int bitrate = appProperties.getTranscode().getDefaultBitrate();
            doTranscode(sourceTempFile, outputTempFile, targetFormat, bitrate, transcodeTask);

            // 重命名去掉临时后缀
            Path finalFile = TempFileManager.renameToFinal(outputTempFile, outputExt);
            transcodeTask.setTempFilePath(finalFile.toString());
            transcodeTask.setStatus(TranscodeTask.TranscodeStatus.UPLOADING);
            repository.save(transcodeTask);

            // 步骤 3：上传
            uploadStep(candidate, targetFormat, targetEngine, finalFile, transcodeTask);

            // 成功 — 清理临时文件
            TempFileManager.deleteQuietly(finalFile);
            TempFileManager.deleteQuietly(sourceTempFile);
            transcodeTask.setStatus(TranscodeTask.TranscodeStatus.COMPLETED);
            transcodeTask.setProgress(1000);
            repository.save(transcodeTask);

            log.info("转码完成：{}", candidate.name());
            return new TranscodeResult(candidate.name(), true, null);

        } catch (Exception e) {
            log.error("转码失败：{} — {}", candidate.name(), e.getMessage(), e);

            if (transcodeTask != null) {
                // 保持当前失败状态（已在各步骤中设置）
                transcodeTask.setErrorMessage(e.getMessage());
                if (outputTempFile != null && Files.exists(outputTempFile)) {
                    transcodeTask.setTempFilePath(outputTempFile.toString());
                }
                repository.save(transcodeTask);
            }

            return new TranscodeResult(candidate.name(), false, e.getMessage());
        }
    }

    /**
     * 步骤 1：下载源文件到临时位置
     */
    private Path downloadStep(TranscodeCandidate candidate, TranscodeTask transcodeTask) throws IOException {
        String format = candidate.format() != null ? candidate.format().toLowerCase() : "tmp";
        Path sourceTempFile = Files.createTempFile("alist-src-", "." + format);

        StorageEngine sourceEngine = candidate.sourceEngine();
        if (sourceEngine == null) {
            throw new IllegalStateException(
                "源存储引擎未设置，无法下载文件：" + candidate.fullPath());
        }

        StorageEngineStrategy sourceStrategy = storageEngineService.resolve(sourceEngine);
        try (InputStream in = sourceStrategy.downloadFile(sourceEngine, candidate.fullPath())) {
            if (in == null) throw new IOException("下载源文件失败：" + candidate.fullPath());
            Files.copy(in, sourceTempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        transcodeTask.setTempSourcePath(sourceTempFile.toString());
        repository.save(transcodeTask);
        return sourceTempFile;
    }

    /**
     * 步骤 3：上传转码输出到目标存储引擎
     */
    private void uploadStep(TranscodeCandidate candidate, TranscodeTask.TargetFormat targetFormat,
                             StorageEngine targetEngine, Path finalFile,
                             TranscodeTask transcodeTask) throws IOException {
        String targetFileName = getOutputName(candidate.name(), targetFormat);
        String remotePath = targetFileName.contains("/")
            ? candidate.targetPath().replace(candidate.name(), targetFileName)
            : concatDirAndName(getDirPath(candidate.targetPath()), targetFileName);

        StorageEngineStrategy targetStrategy = storageEngineService.resolve(targetEngine);
        try (InputStream fileIn = Files.newInputStream(finalFile)) {
            targetStrategy.uploadFile(targetEngine, remotePath, fileIn, Files.size(finalFile));
        }

        log.info("转码文件已上传：{} -> {}", finalFile, remotePath);
    }

    // ================================================================
    // 转码参数构建（codec=null 让 FFmpeg 自动选择编解码器）
    // ================================================================

    private EncodingAttributes buildEncodingAttributes(TranscodeTask.TargetFormat targetFormat, int bitrate) {
        EncodingAttributes attrs = new EncodingAttributes();

        switch (targetFormat) {
            case MP3 -> {
                attrs.setOutputFormat("mp3");
                AudioAttributes audio = new AudioAttributes();
                audio.setCodec(null); // FFmpeg 自动选择（libmp3lame）
                audio.setBitRate(bitrate);
                audio.setChannels(2);
                audio.setSamplingRate(44100);
                attrs.setAudioAttributes(audio);
            }
            case MP4 -> {
                attrs.setOutputFormat("mp4");
                VideoAttributes video = new VideoAttributes();
                video.setCodec(null); // FFmpeg 自动选择（libx264）
                attrs.setVideoAttributes(video);
                AudioAttributes audio = new AudioAttributes();
                audio.setCodec(null); // FFmpeg 自动选择（aac）
                audio.setBitRate(bitrate);
                audio.setChannels(2);
                audio.setSamplingRate(44100);
                attrs.setAudioAttributes(audio);
            }
            case FLV -> {
                attrs.setOutputFormat("flv");
                VideoAttributes video = new VideoAttributes();
                video.setCodec(null); // FFmpeg 自动选择（flv）
                attrs.setVideoAttributes(video);
                AudioAttributes audio = new AudioAttributes();
                audio.setCodec(null); // FFmpeg 自动选择（libmp3lame）
                audio.setBitRate(bitrate);
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

    private class TranscodeProgressListener implements EncoderProgressListener {

        private final TranscodeTask task;
        private int lastSavedProgress = 0;

        TranscodeProgressListener(TranscodeTask task) {
            this.task = task;
        }

        @Override
        public void progress(int permil) {
            task.setProgress(permil);
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
        }

        @Override
        public void message(String message) {
        }
    }

    // ================================================================
    // 辅助方法
    // ================================================================

    private String getOutputName(String sourceName, TranscodeTask.TargetFormat targetFormat) {
        int dotIdx = sourceName.lastIndexOf('.');
        String baseName = dotIdx > 0 ? sourceName.substring(0, dotIdx) : sourceName;
        return baseName + "." + targetFormat.name().toLowerCase();
    }

    private String getDirPath(String fullPath) {
        int idx = fullPath.lastIndexOf('/');
        return idx > 0 ? fullPath.substring(0, idx) : "";
    }

    private String concatDirAndName(String dir, String name) {
        if (dir == null || dir.isEmpty() || dir.equals("/")) return "/" + name;
        return dir.endsWith("/") ? dir + name : dir + "/" + name;
    }
}
