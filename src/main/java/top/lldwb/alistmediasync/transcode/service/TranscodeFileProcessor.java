package top.lldwb.alistmediasync.transcode.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import top.lldwb.alistmediasync.common.config.AppProperties;
import top.lldwb.alistmediasync.common.exception.RetryableException;
import top.lldwb.alistmediasync.common.service.RetryService;
import top.lldwb.alistmediasync.common.service.WsSessionManager;
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
import java.util.Map;
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
    private final WsSessionManager wsSessionManager;
    private final RetryService retryService;

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

        // MdcTaskDecorator 已传递提交线程的 MDC（含 traceId、module、operation）
        // 这里仅细化 operation 标识，便于排查具体文件处理路径
        top.lldwb.alistmediasync.common.util.TraceContext.setModuleOperation(
            "transcode", "单文件转码：" + candidate.name());

        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            top.lldwb.alistmediasync.common.util.TraceContext.setErrorType("InterruptedException");
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

        log.debug("开始处理转码候选：name={}, format={}, size={}bytes", candidate.name(), candidate.format(), candidate.size());

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

            // WebSocket 推送状态变更
            pushProgress(transcodeTask);

            // 步骤 1：下载源文件
            sourceTempFile = downloadStep(candidate, transcodeTask);

            // 步骤 2：转码
            String outputExt = targetFormat.name().toLowerCase();
            outputTempFile = TempFileManager.createTempFile(tempDir, candidate.name(), tempSuffix);
            // 重新加载实体以获取最新版本号（downloadStep 内部已 save，版本号已递增）
            transcodeTask = reloadTask(transcodeTask.getId());
            transcodeTask.setStatus(TranscodeTask.TranscodeStatus.TRANSCODING);
            transcodeTask.setTempSourcePath(sourceTempFile.toString());
            transcodeTask = repository.save(transcodeTask);

            pushProgress(transcodeTask);

            int bitrate = appProperties.getTranscode().getDefaultBitrate();
            doTranscode(sourceTempFile, outputTempFile, targetFormat, bitrate, transcodeTask);

            // 重命名去掉临时后缀
            Path finalFile = TempFileManager.renameToFinal(outputTempFile, outputExt);
            // 转码完成后重新加载实体（TranscodeProgressListener 可能已修改版本号）
            transcodeTask = reloadTask(transcodeTask.getId());
            transcodeTask.setTempFilePath(finalFile.toString());
            transcodeTask.setStatus(TranscodeTask.TranscodeStatus.UPLOADING);
            transcodeTask = repository.save(transcodeTask);

            pushProgress(transcodeTask);

            // 步骤 3：上传
            uploadStep(candidate, targetFormat, targetEngine, finalFile, transcodeTask);

            // 成功 — 清理临时文件
            TempFileManager.deleteQuietly(finalFile);
            TempFileManager.deleteQuietly(sourceTempFile);
            transcodeTask = reloadTask(transcodeTask.getId());
            transcodeTask.setStatus(TranscodeTask.TranscodeStatus.COMPLETED);
            transcodeTask.setProgress(1000);
            repository.save(transcodeTask);

            pushProgress(transcodeTask);

            log.info("转码完成：{}", candidate.name());
            return new TranscodeResult(candidate.name(), true, null);

        } catch (Exception e) {
            log.error("转码失败：{} — {}", candidate.name(), e.getMessage(), e);

            if (transcodeTask != null) {
                // 重新加载以获取最新版本号，避免乐观锁冲突
                transcodeTask = reloadTask(transcodeTask.getId());
                transcodeTask.setErrorMessage(e.getMessage());
                if (outputTempFile != null && Files.exists(outputTempFile)) {
                    transcodeTask.setTempFilePath(outputTempFile.toString());
                }

                // 判断是否可重试
                if (retryService.isRetryable(e)
                    && transcodeTask.getRetryCount() < retryService.getMaxAutoRetries()) {
                    int nextAttempt = transcodeTask.getRetryCount() + 1;
                    transcodeTask.setRetryCount(nextAttempt);
                    log.info("调度自动重试：{}, 第 {}/{} 次", candidate.name(),
                        nextAttempt, retryService.getMaxAutoRetries());

                    final TranscodeTask finalTask = transcodeTask;
                    retryService.scheduleRetry(nextAttempt, candidate.name(),
                        () -> doProcess(candidate, targetFormat, tempSuffix, tempDir,
                            targetEngine, syncTask, execution),
                        () -> {
                            // 重试用尽：标记为最终失败
                            var exhausted = reloadTask(finalTask.getId());
                            exhausted.setErrorMessage(e.getMessage() + "（自动重试用尽）");
                            repository.save(exhausted);
                            pushProgress(exhausted);
                        }
                    );
                } else if (!retryService.isRetryable(e)) {
                    log.info("业务错误，不进行自动重试：{} — {}", candidate.name(), e.getMessage());
                }

                // 按步骤精确设置失败状态
                if (transcodeTask.getStatus() == TranscodeTask.TranscodeStatus.DOWNLOADING) {
                    transcodeTask.setStatus(TranscodeTask.TranscodeStatus.DOWNLOAD_FAILED);
                } else if (transcodeTask.getStatus() == TranscodeTask.TranscodeStatus.TRANSCODING) {
                    transcodeTask.setStatus(TranscodeTask.TranscodeStatus.TRANSCODE_FAILED);
                } else if (transcodeTask.getStatus() == TranscodeTask.TranscodeStatus.UPLOADING) {
                    transcodeTask.setStatus(TranscodeTask.TranscodeStatus.UPLOAD_FAILED);
                }

                repository.save(transcodeTask);
                pushProgress(transcodeTask);
            }

            return new TranscodeResult(candidate.name(), false, e.getMessage());
        }
    }

    /**
     * 步骤 1：下载源文件到临时位置
     */
    private Path downloadStep(TranscodeCandidate candidate, TranscodeTask transcodeTask) throws IOException {
        log.debug("下载源文件：path={}", candidate.fullPath());
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

        long downloadedSize = Files.size(sourceTempFile);
        log.debug("源文件下载完成：path={}, size={}bytes, tempFile={}", candidate.fullPath(), downloadedSize, sourceTempFile);

        // 注意：调用方在 save 后应使用返回的 managed entity，此处仅更新引用
        // downloadStep 不负责持久化（由 doProcess 统一管理）
        transcodeTask.setTempSourcePath(sourceTempFile.toString());
        transcodeTask = repository.save(transcodeTask);
        return sourceTempFile;
    }

    /**
     * 重新加载 TranscodeTask 实体以获取最新版本号
     * <p>
     * 在每次 save() 之后、下一次修改之前调用，避免 detached entity
     * merge 时因版本号过期导致 ObjectOptimisticLockingFailureException。
     * </p>
     */
    private TranscodeTask reloadTask(Long taskId) {
        return repository.findById(taskId)
            .orElseThrow(() -> new IllegalStateException("转码任务不存在：id=" + taskId));
    }

    /**
     * 步骤 3：上传转码输出到目标存储引擎
     * <p>
     * 输出路径规则：目标文件所在目录 / 源文件名（不含原扩展名）.目标格式扩展名。
     * 对于源目录转码（targetPath 与 fullPath 目录相同），输出文件与源文件在同一目录下。
     * 对于目录扫描模式，每个文件的输出路径独立计算。
     * </p>
     */
    private void uploadStep(TranscodeCandidate candidate, TranscodeTask.TargetFormat targetFormat,
                             StorageEngine targetEngine, Path finalFile,
                             TranscodeTask transcodeTask) throws IOException {
        // 构建输出文件名：源文件名（去扩展名）+ "." + 目标扩展名
        String targetFileName = getOutputName(candidate.name(), targetFormat);

        // 输出目录 = 目标文件所在目录（使用 targetPath 计算，确保转码结果写入目标引擎的正确路径）
        String targetDir = getDirPath(candidate.targetPath());

        // 拼接完整目标路径：目标文件所在目录 / 输出文件名
        String remotePath = concatDirAndName(targetDir, targetFileName);

        long fileSize = Files.size(finalFile);
        log.debug("开始上传转码文件：localPath={}, remotePath={}, size={}bytes", finalFile, remotePath, fileSize);

        StorageEngineStrategy targetStrategy = storageEngineService.resolve(targetEngine);
        try (InputStream fileIn = Files.newInputStream(finalFile)) {
            targetStrategy.uploadFile(targetEngine, remotePath, fileIn, fileSize);
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

        private final Long taskId;
        private int lastSavedProgress = 0;

        TranscodeProgressListener(TranscodeTask task) {
            this.taskId = task.getId();
        }

        @Override
        public void progress(int permil) {
            if (permil - lastSavedProgress >= 50) {
                lastSavedProgress = permil;
                try {
                    TranscodeTask managed = repository.findById(taskId).orElse(null);
                    if (managed != null) {
                        managed.setProgress(permil);
                        repository.save(managed);
                    }
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

    /**
     * 通过 WebSocket 推送转码任务进度
     */
    private void pushProgress(TranscodeTask task) {
        wsSessionManager.broadcast("TRANSCODE_PROGRESS", Map.of(
            "taskId", task.getId(),
            "status", task.getStatus().name(),
            "progressPercent", task.getProgress() / 10,
            "retryCount", task.getRetryCount(),
            "errorMessage", task.getErrorMessage() != null ? task.getErrorMessage() : ""
        ));
    }
}
