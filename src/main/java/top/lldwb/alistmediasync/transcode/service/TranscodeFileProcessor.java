package top.lldwb.alistmediasync.transcode.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import top.lldwb.alistmediasync.common.config.AppProperties;
import top.lldwb.alistmediasync.common.enums.TargetFormat;
import top.lldwb.alistmediasync.common.exception.RetryableException;
import top.lldwb.alistmediasync.common.exception.RetryableIOException;
import top.lldwb.alistmediasync.common.service.RetryService;
import top.lldwb.alistmediasync.storage.entity.StorageEngine;
import top.lldwb.alistmediasync.storage.service.StorageEngineService;
import top.lldwb.alistmediasync.storage.service.engine.StorageEngineStrategy;
import top.lldwb.alistmediasync.sync.entity.SyncTask;
import top.lldwb.alistmediasync.execution.TaskExecution;
import top.lldwb.alistmediasync.transcode.entity.TranscodeTask;
import top.lldwb.alistmediasync.common.util.DiskSpaceChecker;
import top.lldwb.alistmediasync.common.util.TempFileManager;
import top.lldwb.alistmediasync.common.util.PathUtils;
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

    private final StorageEngineService storageEngineService;
    private final AppProperties appProperties;
    private final RetryService retryService;
    private final TranscodeTaskStateWriter stateWriter;

    /** 并发转码信号量（由配置 maxConcurrentTranscode 控制上限） */
    Semaphore semaphore;

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
            TargetFormat targetFormat,
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
                    targetEngine, syncTask, execution, null);
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
                             TargetFormat targetFormat, int bitrate,
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
                                       TargetFormat targetFormat,
                                       String tempSuffix,
                                       Path tempDir,
                                       StorageEngine targetEngine,
                                       SyncTask syncTask,
                                       TaskExecution execution,
                                       TranscodeTask existingTask) {

        log.debug("开始处理转码候选：name={}, format={}, size={}bytes", candidate.name(), candidate.format(), candidate.size());

        Path sourceTempFile = null;
        Path outputTempFile = null;
        Path finalFile = null;
        TranscodeTask transcodeTask = null;

        try {
            if (existingTask != null) {
                // 自动重试：复用同一任务记录（保持 retryCount 递增），
                // 从失败步骤继续——下载失败需重下、转码失败需重转、仅上传失败复用转码产物
                transcodeTask = stateWriter.reloadTask(existingTask.getId());
                if (transcodeTask.getTempSourcePath() != null) {
                    Path p = Path.of(transcodeTask.getTempSourcePath());
                    if (Files.exists(p)) {
                        sourceTempFile = p;
                    }
                }
                if (transcodeTask.getStatus() == TranscodeTask.TranscodeStatus.UPLOAD_FAILED
                    && transcodeTask.getTempFilePath() != null) {
                    Path p = Path.of(transcodeTask.getTempFilePath());
                    if (Files.exists(p)) {
                        finalFile = p;
                    }
                }
            } else {
                // 首次执行：创建独立的文件级任务记录
                transcodeTask = new TranscodeTask();
                transcodeTask.setSyncTaskId(syncTask != null ? syncTask.getId() : null);
                transcodeTask.setSourceFilePath(candidate.fullPath());
                transcodeTask.setTargetFilePath(candidate.targetPath());
                transcodeTask.setTargetFormat(targetFormat);
                transcodeTask.setStatus(TranscodeTask.TranscodeStatus.DOWNLOADING);
                if (targetEngine != null) {
                    transcodeTask.setTargetEngineId(targetEngine.getId());
                }
                transcodeTask = stateWriter.save(transcodeTask);
            }
            stateWriter.pushProgress(transcodeTask);

            // 步骤 1：下载源文件（已有有效临时文件则跳过）
            if (sourceTempFile == null) {
                transcodeTask.setStatus(TranscodeTask.TranscodeStatus.DOWNLOADING);
                transcodeTask = stateWriter.saveAndReload(transcodeTask);
                sourceTempFile = downloadStep(candidate, transcodeTask);
                transcodeTask.setTempSourcePath(sourceTempFile.toString());
                transcodeTask = stateWriter.saveAndReload(transcodeTask);
                stateWriter.pushProgress(transcodeTask);
            }

            // 步骤 2：转码（上传失败重试时已有完整转码产物则跳过）
            if (finalFile == null) {
                String outputExt = targetFormat.name().toLowerCase();
                outputTempFile = TempFileManager.createTempFile(tempDir, candidate.name(), tempSuffix);
                transcodeTask.setStatus(TranscodeTask.TranscodeStatus.TRANSCODING);
                transcodeTask.setTempSourcePath(sourceTempFile.toString());
                transcodeTask = stateWriter.saveAndReload(transcodeTask);
                stateWriter.pushProgress(transcodeTask);

                int bitrate = appProperties.getTranscode().getDefaultBitrate();
                doTranscode(sourceTempFile, outputTempFile, targetFormat, bitrate, transcodeTask);

                finalFile = TempFileManager.renameToFinal(outputTempFile, outputExt);
                transcodeTask = stateWriter.reloadTask(transcodeTask.getId());
                transcodeTask.setTempFilePath(finalFile.toString());
                transcodeTask.setStatus(TranscodeTask.TranscodeStatus.UPLOADING);
                transcodeTask = stateWriter.saveAndReload(transcodeTask);
                stateWriter.pushProgress(transcodeTask);
            }

            // 步骤 3：上传
            uploadStep(candidate, targetFormat, targetEngine, finalFile, transcodeTask);

            // 成功 — 清理临时文件
            TempFileManager.deleteQuietly(finalFile);
            TempFileManager.deleteQuietly(sourceTempFile);
            transcodeTask = stateWriter.reloadTask(transcodeTask.getId());
            transcodeTask.setStatus(TranscodeTask.TranscodeStatus.COMPLETED);
            transcodeTask.setProgress(1000);
            transcodeTask.setErrorMessage(null);
            transcodeTask.setRetryCount(0); // 成功后重置重试计数
            stateWriter.save(transcodeTask);

            stateWriter.pushProgress(transcodeTask);

            log.info("转码完成：{}", candidate.name());
            return new TranscodeResult(candidate.name(), true, null);

        } catch (Exception e) {
            log.error("转码失败：{} — {}", candidate.name(), e.getMessage(), e);

            if (transcodeTask != null) {
                // 重新加载以获取最新版本号，避免乐观锁冲突
                transcodeTask = stateWriter.reloadTask(transcodeTask.getId());
                transcodeTask.setErrorMessage(e.getMessage());
                if (finalFile != null && Files.exists(finalFile)) {
                    transcodeTask.setTempFilePath(finalFile.toString());
                } else if (outputTempFile != null && Files.exists(outputTempFile)) {
                    transcodeTask.setTempFilePath(outputTempFile.toString());
                }
                if (sourceTempFile != null) {
                    transcodeTask.setTempSourcePath(sourceTempFile.toString());
                }

                // 按步骤精确设置失败状态
                if (transcodeTask.getStatus() == TranscodeTask.TranscodeStatus.DOWNLOADING) {
                    transcodeTask.setStatus(TranscodeTask.TranscodeStatus.DOWNLOAD_FAILED);
                } else if (transcodeTask.getStatus() == TranscodeTask.TranscodeStatus.TRANSCODING) {
                    transcodeTask.setStatus(TranscodeTask.TranscodeStatus.TRANSCODE_FAILED);
                } else if (transcodeTask.getStatus() == TranscodeTask.TranscodeStatus.UPLOADING) {
                    transcodeTask.setStatus(TranscodeTask.TranscodeStatus.UPLOAD_FAILED);
                }

                // 瞬时故障（RetryableException）且未达上限：复用同一任务记录调度自动重试
                if (retryService.isRetryable(e)
                    && transcodeTask.getRetryCount() < retryService.getMaxAutoRetries()) {
                    int nextAttempt = transcodeTask.getRetryCount() + 1;
                    transcodeTask.setRetryCount(nextAttempt);
                    stateWriter.save(transcodeTask);
                    stateWriter.pushProgress(transcodeTask);
                    log.info("调度自动重试：{}, 第 {}/{} 次", candidate.name(),
                        nextAttempt, retryService.getMaxAutoRetries());

                    final TranscodeTask retryTask = transcodeTask;
                    retryService.scheduleRetry(nextAttempt, candidate.name(),
                        () -> doProcess(candidate, targetFormat, tempSuffix, tempDir,
                            targetEngine, syncTask, execution, retryTask),
                        () -> log.warn("自动重试用尽：{}", candidate.name()));
                } else {
                    if (retryService.isRetryable(e)) {
                        transcodeTask.setErrorMessage(transcodeTask.getErrorMessage() + "（自动重试用尽）");
                        log.warn("自动重试次数已达上限：{} — {}", candidate.name(), e.getMessage());
                    } else {
                        log.info("业务错误，不进行自动重试：{} — {}", candidate.name(), e.getMessage());
                    }
                    stateWriter.save(transcodeTask);
                    stateWriter.pushProgress(transcodeTask);
                }
            }

            return new TranscodeResult(candidate.name(), false, e.getMessage());
        }
    }

    /**
     * 步骤 1：下载源文件到临时位置
     * <p>
     * 网络/IO 瞬时故障包装为 {@link RetryableIOException}，触发自动重试；
     * 业务错误（如源引擎未配置）保持原异常类型，不重试。
     * </p>
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
            if (in == null) throw new RetryableIOException("下载源文件失败：" + candidate.fullPath());
            try {
                Files.copy(in, sourceTempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RetryableIOException("下载源文件失败：" + candidate.fullPath(), e);
            }
        } catch (IOException e) {
            if (e instanceof RetryableIOException) {
                throw e;
            }
            throw new RetryableIOException("下载源文件失败：" + candidate.fullPath(), e);
        }

        long downloadedSize = Files.size(sourceTempFile);
        log.debug("源文件下载完成：path={}, size={}bytes, tempFile={}", candidate.fullPath(), downloadedSize, sourceTempFile);

        // 仅设置临时路径，不在此处持久化（由 doProcess 统一管理 save/reload 节奏）
        transcodeTask.setTempSourcePath(sourceTempFile.toString());
        return sourceTempFile;
    }

    /**
     * 步骤 3：上传转码输出到目标存储引擎
     * <p>
     * 输出路径规则：目标文件所在目录 / 源文件名（不含原扩展名）.目标格式扩展名。
     * 对于源目录转码（targetPath 与 fullPath 目录相同），输出文件与源文件在同一目录下。
     * 对于目录扫描模式，每个文件的输出路径独立计算。
     * </p>
     * <p>
     * 上传失败统一包装为 {@link RetryableIOException}（上传幂等，重试安全），
     * 由 doProcess 触发自动重试。
     * </p>
     */
    private void uploadStep(TranscodeCandidate candidate, TargetFormat targetFormat,
                             StorageEngine targetEngine, Path finalFile,
                             TranscodeTask transcodeTask) throws IOException {
        // 构建输出文件名：源文件名（去扩展名）+ "." + 目标扩展名
        String targetFileName = PathUtils.swapExtension(candidate.name(), targetFormat.name().toLowerCase());

        // 输出目录 = 目标文件所在目录（使用 targetPath 计算，确保转码结果写入目标引擎的正确路径）
        String targetDir = PathUtils.parentDir(candidate.targetPath());

        // 拼接完整目标路径：目标文件所在目录 / 输出文件名
        String remotePath = PathUtils.join(targetDir, targetFileName);

        long fileSize;
        try {
            fileSize = Files.size(finalFile);
        } catch (IOException e) {
            throw new RetryableIOException("读取转码产物失败：" + finalFile, e);
        }
        log.debug("开始上传转码文件：localPath={}, remotePath={}, size={}bytes", finalFile, remotePath, fileSize);

        StorageEngineStrategy targetStrategy = storageEngineService.resolve(targetEngine);
        try (InputStream fileIn = Files.newInputStream(finalFile)) {
            try {
                targetStrategy.uploadFile(targetEngine, remotePath, fileIn, fileSize);
            } catch (RuntimeException e) {
                throw new RetryableIOException("上传转码文件失败：" + remotePath, e);
            }
        } catch (IOException e) {
            throw new RetryableIOException("上传转码文件失败：" + remotePath, e);
        }

        log.info("转码文件已上传：{} -> {}", finalFile, remotePath);
    }

    // ================================================================
    // 转码参数构建（codec=null 让 FFmpeg 自动选择编解码器）
    // ================================================================

    EncodingAttributes buildEncodingAttributes(TargetFormat targetFormat, int bitrate) {
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
            // 50‰ 节流后交由 stateWriter 落库（持久化失败仅记 DEBUG 日志）
            if (permil - lastSavedProgress >= 50) {
                lastSavedProgress = permil;
                stateWriter.persistProgress(taskId, permil);
            }
        }

        @Override
        public void sourceInfo(ws.schild.jave.info.MultimediaInfo info) {
        }

        @Override
        public void message(String message) {
        }
    }
}
