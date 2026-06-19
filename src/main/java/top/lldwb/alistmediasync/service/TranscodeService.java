package top.lldwb.alistmediasync.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.lldwb.alistmediasync.client.AListClient;
import top.lldwb.alistmediasync.config.AppProperties;
import top.lldwb.alistmediasync.entity.*;
import top.lldwb.alistmediasync.repository.*;
import top.lldwb.alistmediasync.util.*;
import ws.schild.jave.Encoder;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;
import ws.schild.jave.encode.VideoAttributes;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import tools.jackson.databind.json.JsonMapper;

/**
 * 转码引擎（核心服务）
 * <p>
 * 实现两阶段处理的视频转码系统：
 * <ol>
 *   <li><b>阶段 1 — 扫描</b>：单线程递归遍历源目录，MagicBytes 检测视频格式，收集候选文件</li>
 *   <li><b>阶段 2 — 并行转码</b>：多线程并行执行转码，Semaphore 控制并发上限</li>
 * </ol>
 * </p>
 * <p>
 * 集成 Feature 002 的全部临时文件管理能力：
 * <ul>
 *   <li>可配置临时文件后缀（{@code app.transcode.temp-suffix}）</li>
 *   <li>本地临时目录暂存 → 转码 → 重命名 → 上传 → 删除</li>
 *   <li>磁盘空间预估检查（1.5 倍安全阈值）</li>
 *   <li>并发上限控制（Semaphore）</li>
 *   <li>上传失败保留文件供手动重试</li>
 * </ul>
 * </p>
 *
 * @author AList-Media-Sync
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranscodeService {

    private final TranscodeTaskRepository repository;
    private final TaskExecutionRepository taskExecutionRepository;
    private final StorageEngineRepository storageEngineRepository;
    private final AListClient alistClient;
    private final AppProperties appProperties;

    @Lazy
    private final TranscodeService self;

    /** 并发转码信号量（由配置 maxConcurrentTranscode 控制上限） */
    private Semaphore semaphore;

    /**
     * 初始化信号量
     */
    @jakarta.annotation.PostConstruct
    void init() {
        int maxConcurrent = appProperties.getTranscode().getMaxConcurrentTranscode();
        this.semaphore = new Semaphore(maxConcurrent);
        log.info("转码引擎已初始化，最大并发数：{}", maxConcurrent);
    }

    /**
     * 创建独立转码任务
     */
    @Transactional
    public TranscodeTask createTask(Long sourceEngineId, Long targetEngineId,
                                     String sourcePath, String targetPath,
                                     TranscodeTask.TargetFormat targetFormat, Integer bitrate) {
        TranscodeTask task = new TranscodeTask();
        task.setSourceEngineId(sourceEngineId);
        task.setTargetEngineId(targetEngineId);
        task.setSourceFilePath(sourcePath);
        task.setTargetFilePath(targetPath);
        task.setTargetFormat(targetFormat);
        task.setBitrate(bitrate != null ? bitrate : 128000);
        task.setStatus(TranscodeTask.TranscodeStatus.PENDING);
        task = repository.save(task);
        log.info("转码任务已创建：{} -> {} (格式: {})", sourcePath, targetPath, targetFormat);
        return task;
    }

    /**
     * 异步执行转码任务
     */
    @Async("transcodeExecutor")
    public void executeAsync(TranscodeTask task) {
        executeTask(task);
    }

    /**
     * 同步后置转码（由 SyncService 调用）
     */
    public void executePostSyncTranscode(SyncTask syncTask, TaskExecution syncExecution) {
        // 创建转码任务执行记录
        TaskExecution execution = new TaskExecution();
        execution.setSyncTask(syncTask);
        execution.setTaskType(TaskExecution.TaskType.TRANSCODE);
        execution.setStartTime(java.time.LocalDateTime.now());
        execution.setStatus(TaskExecution.ExecutionStatus.RUNNING);
        execution = taskExecutionRepository.save(execution);

        // 扫描 → 转码 → 上传
        executeTaskInternal(syncTask.getSourceEngine(), syncTask.getTargetEngine(),
            syncTask.getSourcePath(), syncTask.getTargetPath(),
            syncTask.getTargetFormat().name(), syncTask.getConflictStrategy(),
            syncTask, execution);

        execution.setEndTime(java.time.LocalDateTime.now());
        taskExecutionRepository.save(execution);
    }

    // ================================================================
    // 核心转码流程
    // ================================================================

    /**
     * 执行转码任务
     */
    @Transactional
    void executeTask(TranscodeTask task) {
        StorageEngine sourceEngine = task.getSourceEngineId() != null
            ? storageEngineRepository.findById(task.getSourceEngineId()).orElse(null)
            : null;
        StorageEngine targetEngine = storageEngineRepository.findById(task.getTargetEngineId())
            .orElseThrow(() -> new NoSuchElementException("目标存储引擎不存在"));

        // 创建执行记录
        TaskExecution execution = new TaskExecution();
        execution.setTranscodeTask(task);
        execution.setTaskType(TaskExecution.TaskType.TRANSCODE);
        execution.setStartTime(java.time.LocalDateTime.now());
        execution.setStatus(TaskExecution.ExecutionStatus.RUNNING);
        execution = taskExecutionRepository.save(execution);

        try {
            executeTaskInternal(sourceEngine, targetEngine,
                task.getSourceFilePath(), task.getTargetFilePath(),
                task.getTargetFormat().name(), SyncTask.ConflictStrategy.SKIP,
                null, execution);

            task.setStatus(TranscodeTask.TranscodeStatus.COMPLETED);
            task.setProgress(1000);
            execution.setStatus(TaskExecution.ExecutionStatus.SUCCESS);
            log.info("转码任务已完成：{}", task.getSourceFilePath());
        } catch (Exception e) {
            log.error("转码任务失败：{} — {}", task.getSourceFilePath(), e.getMessage(), e);
            task.setStatus(TranscodeTask.TranscodeStatus.FAILED);
            task.setErrorMessage(e.getMessage());
            execution.setStatus(TaskExecution.ExecutionStatus.FAILED);
            execution.setFailureDetails(e.getMessage());
            taskExecutionRepository.save(execution);
        } finally {
            execution.setEndTime(java.time.LocalDateTime.now());
            taskExecutionRepository.save(execution);
            repository.save(task);
        }
    }

    /**
     * 内部转码执行流程
     */
    private void executeTaskInternal(StorageEngine sourceEngine, StorageEngine targetEngine,
                                      String sourcePath, String targetPath, String targetFormatStr,
                                      SyncTask.ConflictStrategy conflictStrategy,
                                      SyncTask syncTask, TaskExecution execution) {

        TranscodeTask.TargetFormat targetFormat = TranscodeTask.TargetFormat.valueOf(targetFormatStr);
        String tempSuffix = appProperties.getTranscode().getTempSuffix();
        Path tempDir = Path.of(appProperties.getTranscode().getTempDir());

        // 阶段 1：扫描源目录
        List<TranscodeCandidate> candidates = scanSourceDirectory(
            sourceEngine, targetEngine, sourcePath, targetPath, conflictStrategy);

        if (candidates.isEmpty()) {
            log.info("未发现需要转码的文件：{}", sourcePath);
            execution.setTotalFiles(0);
            execution.setSuccessFiles(0);
            execution.setStatus(TaskExecution.ExecutionStatus.SUCCESS);
            return;
        }

        log.info("扫描完成，发现 {} 个待转码文件", candidates.size());
        execution.setTotalFiles(candidates.size());

        // 阶段 2：并行转码
        List<CompletableFuture<TranscodeResult>> futures = new ArrayList<>();
        for (TranscodeCandidate candidate : candidates) {
            CompletableFuture<TranscodeResult> future = self.transcodeFile(
                candidate, targetFormat, tempSuffix, tempDir, targetEngine, syncTask, execution);
            futures.add(future);
        }

        // 收集结果
        int successCount = 0;
        List<String> failures = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            try {
                TranscodeResult result = futures.get(i).get(10, TimeUnit.MINUTES);
                if (result.success) {
                    successCount++;
                } else {
                    failures.add(result.sourceFileName + ": " + result.error);
                }
            } catch (Exception e) {
                failures.add(candidates.get(i).name + ": " + e.getMessage());
            }
        }

        execution.setSuccessFiles(successCount);
        execution.setFailedFiles(failures.size());
        if (!failures.isEmpty()) {
            execution.setFailureDetails(toJson(failures));
            execution.setStatus(successCount > 0
                ? TaskExecution.ExecutionStatus.PARTIAL_SUCCESS
                : TaskExecution.ExecutionStatus.FAILED);
        } else {
            execution.setStatus(TaskExecution.ExecutionStatus.SUCCESS);
        }
    }

    // ================================================================
    // 阶段 1：扫描源目录
    // ================================================================

    private List<TranscodeCandidate> scanSourceDirectory(
        StorageEngine sourceEngine, StorageEngine targetEngine,
        String sourcePath, String targetPath, SyncTask.ConflictStrategy conflictStrategy) {

        List<TranscodeCandidate> candidates = new ArrayList<>();
        scanDirectoryRecursive(sourceEngine.getBaseUrl(), sourceEngine.getEncryptedToken(),
            sourcePath, targetPath, targetEngine, conflictStrategy, candidates);
        return candidates;
    }

    @SuppressWarnings("unchecked")
    private void scanDirectoryRecursive(String baseUrl, String token, String sourceDir, String targetDir,
                                         StorageEngine targetEngine, SyncTask.ConflictStrategy conflictStrategy,
                                         List<TranscodeCandidate> candidates) {
        Map<String, Object> resp = alistClient.listFiles(baseUrl, token, sourceDir, 1, 100);
        if (resp == null || resp.get("data") == null) return;

        Map<String, Object> data = (Map<String, Object>) resp.get("data");
        List<Map<String, Object>> files = (List<Map<String, Object>>) data.get("content");
        if (files == null) return;

        for (Map<String, Object> f : files) {
            boolean isDir = Boolean.TRUE.equals(f.get("is_dir"));
            String name = (String) f.get("name");
            if (name == null) continue;
            String fullPath = concatPath(sourceDir, name);

            if (isDir) {
                scanDirectoryRecursive(baseUrl, token, fullPath, concatPath(targetDir, name),
                    targetEngine, conflictStrategy, candidates);
                continue;
            }

            // 魔数检测视频格式
            String format = MagicBytesDetector.detectByExtension(name);
            if ("UNKNOWN".equals(format)) {
                continue; // 非视频文件，跳过
            }

            // 检查目标是否已存在
            boolean targetExists = checkTargetExists(targetEngine.getBaseUrl(),
                targetEngine.getEncryptedToken(), concatPath(targetDir, getOutputName(name)));
            if (targetExists && conflictStrategy == SyncTask.ConflictStrategy.SKIP) {
                log.debug("目标文件已存在，跳过：{}", name);
                continue;
            }

            Number sizeNum = (Number) f.get("size");
            long size = sizeNum != null ? sizeNum.longValue() : 0;

            candidates.add(new TranscodeCandidate(
                name, fullPath, concatPath(targetDir, name), format, size));
        }
    }

    // ================================================================
    // 阶段 2：单文件转码（@Async + Semaphore）
    // ================================================================

    @Async("transcodeExecutor")
    public CompletableFuture<TranscodeResult> transcodeFile(
        TranscodeCandidate candidate, TranscodeTask.TargetFormat targetFormat,
        String tempSuffix, Path tempDir, StorageEngine targetEngine,
        SyncTask syncTask, TaskExecution execution) {

        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CompletableFuture.completedFuture(
                new TranscodeResult(candidate.name, false, "线程被中断"));
        }

        try {
            TranscodeResult result = doTranscode(candidate, targetFormat, tempSuffix, tempDir,
                targetEngine, syncTask, execution);
            return CompletableFuture.completedFuture(result);
        } finally {
            semaphore.release();
        }
    }

    private TranscodeResult doTranscode(TranscodeCandidate candidate,
                                         TranscodeTask.TargetFormat targetFormat,
                                         String tempSuffix, Path tempDir,
                                         StorageEngine targetEngine,
                                         SyncTask syncTask, TaskExecution execution) {

        Path tempFile = null;
        Path finalFile = null;
        TranscodeTask transcodeTask = null;

        try {
            // 1. 创建 TranscodeTask 记录
            transcodeTask = new TranscodeTask();
            transcodeTask.setSyncTask(syncTask);
            transcodeTask.setSourceFilePath(candidate.fullPath);
            transcodeTask.setTargetFilePath(candidate.targetPath);
            transcodeTask.setTargetFormat(targetFormat);
            transcodeTask.setStatus(TranscodeTask.TranscodeStatus.TRANSCODING);
            if (targetEngine != null) {
                transcodeTask.setTargetEngineId(targetEngine.getId());
            }
            transcodeTask = repository.save(transcodeTask);

            // 2. 获取源文件信息（时长等）
            // 从 AList 下载源文件到临时位置用于转码
            Path sourceTempFile = Files.createTempFile("alist-src-", "." + candidate.format.toLowerCase());
            StorageEngine sourceEngine = candidate.sourceEngine != null ? candidate.sourceEngine : targetEngine;
            try (InputStream in = alistClient.downloadFile(
                sourceEngine.getBaseUrl(), sourceEngine.getEncryptedToken(), candidate.fullPath)) {
                if (in == null) throw new IOException("下载源文件失败：" + candidate.fullPath);
                Files.copy(in, sourceTempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            // 3. 获取源文件时长用于磁盘空间预估
            long durationMs = 0;
            try {
                MultimediaObject mmObj = new MultimediaObject(sourceTempFile.toFile());
                durationMs = mmObj.getInfo().getDuration();
            } catch (Exception e) {
                log.warn("无法获取源文件时长：{}，跳过空间预估", candidate.name);
            }

            // 4. 磁盘空间检查
            long estimatedSize = DiskSpaceChecker.estimateOutputSize(durationMs, 128000);
            DiskSpaceChecker.checkSufficient(tempDir, estimatedSize);

            // 5. 创建临时输出文件
            String outputExt = targetFormat.name().toLowerCase();
            tempFile = TempFileManager.createTempFile(tempDir, candidate.name, tempSuffix);
            transcodeTask.setTempFilePath(tempFile.toString());
            repository.save(transcodeTask);

            // 6. 构建 JAVE2 转码参数
            EncodingAttributes attrs = buildEncodingAttributes(targetFormat);

            // 7. 执行转码（带进度监听）
            log.info("开始转码：{} ({} -> {})", candidate.name, candidate.format, outputExt);
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
            String targetFileName = getOutputName(candidate.name);
            String remotePath = targetFileName.contains("/")
                ? candidate.targetPath.replace(candidate.name, targetFileName)
                : concatDirAndName(getDirPath(candidate.targetPath), targetFileName);

            try (InputStream fileIn = Files.newInputStream(finalFile)) {
                alistClient.uploadFile(targetEngine.getBaseUrl(), targetEngine.getEncryptedToken(),
                    remotePath, fileIn, Files.size(finalFile));
            }

            // 10. 上传成功 → 删除本地文件
            TempFileManager.deleteQuietly(finalFile);
            transcodeTask.setStatus(TranscodeTask.TranscodeStatus.COMPLETED);
            transcodeTask.setProgress(1000);
            repository.save(transcodeTask);

            // 清理源临时文件
            TempFileManager.deleteQuietly(sourceTempFile);

            log.info("转码完成：{} -> {}", candidate.name, remotePath);
            return new TranscodeResult(candidate.name, true, null);

        } catch (Exception e) {
            log.error("转码失败：{} — {}", candidate.name, e.getMessage(), e);

            if (transcodeTask != null) {
                transcodeTask.setStatus(TranscodeTask.TranscodeStatus.FAILED);
                transcodeTask.setErrorMessage(e.getMessage());
                // 保留 finalFile 路径用于手动重试
                if (finalFile != null) {
                    transcodeTask.setTempFilePath(finalFile.toString());
                }
                repository.save(transcodeTask);
            }

            return new TranscodeResult(candidate.name, false, e.getMessage());
        }
    }

    // ================================================================
    // 转码参数构建
    // ================================================================

    private EncodingAttributes buildEncodingAttributes(TranscodeTask.TargetFormat targetFormat) {
        EncodingAttributes attrs = new EncodingAttributes();

        switch (targetFormat) {
            case MP3 -> {
                // 仅音频输出：libmp3lame 128kbps 立体声 44100Hz
                attrs.setOutputFormat("mp3");
                AudioAttributes audio = new AudioAttributes();
                audio.setCodec("libmp3lame");
                audio.setBitRate(128000);
                audio.setChannels(2);
                audio.setSamplingRate(44100);
                attrs.setAudioAttributes(audio);
            }
            case MP4 -> {
                // 视频+音频：libx264 + aac
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
                // 视频+音频：flv + libmp3lame
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
    // 手动重试上传
    // ================================================================

    @Transactional
    public boolean retryUpload(Long taskId) {
        TranscodeTask task = repository.findById(taskId)
            .orElseThrow(() -> new NoSuchElementException("转码任务不存在：id=" + taskId));

        if (task.getStatus() != TranscodeTask.TranscodeStatus.FAILED) {
            throw new IllegalStateException("仅失败状态的转码任务可重试上传");
        }

        Path localFile = task.getTempFilePath() != null ? Path.of(task.getTempFilePath()) : null;
        if (localFile == null || !Files.exists(localFile)) {
            throw new IllegalStateException("临时文件已不存在，请重新执行转码");
        }

        StorageEngine targetEngine = storageEngineRepository.findById(task.getTargetEngineId())
            .orElseThrow(() -> new NoSuchElementException("目标存储引擎不存在"));

        // 从 tempFilePath 推导目标远程路径
        String targetFileName = getOutputName(task.getSourceFilePath().substring(
            task.getSourceFilePath().lastIndexOf('/') + 1));
        String remotePath = concatDirAndName(
            getDirPath(task.getTargetFilePath()), targetFileName);

        try (InputStream fileIn = Files.newInputStream(localFile)) {
            alistClient.uploadFile(targetEngine.getBaseUrl(), targetEngine.getEncryptedToken(),
                remotePath, fileIn, Files.size(localFile));
        } catch (IOException e) {
            throw new RuntimeException("重试上传失败：" + e.getMessage(), e);
        }

        // 上传成功 → 删除本地文件 → 更新状态
        TempFileManager.deleteQuietly(localFile);
        task.setStatus(TranscodeTask.TranscodeStatus.COMPLETED);
        task.setProgress(1000);
        task.setTempFilePath(null);
        task.setErrorMessage(null);
        repository.save(task);

        log.info("手动重试上传成功：{} -> {}", localFile, remotePath);
        return true;
    }

    // ================================================================
    // 进度持久化
    // ================================================================

    /**
     * 转码进度监听器
     */
    private class TranscodeProgressListener implements ws.schild.jave.progress.EncoderProgressListener {

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

    private String concatPath(String dir, String name) {
        return concatDirAndName(dir, name);
    }

    /** 获取转码后输出文件名 */
    private String getOutputName(String sourceName) {
        int dotIdx = sourceName.lastIndexOf('.');
        String baseName = dotIdx > 0 ? sourceName.substring(0, dotIdx) : sourceName;
        return baseName + ".mp3"; // 默认 MP3，由调用方根据 targetFormat 覆盖
    }

    @SuppressWarnings("unchecked")
    private boolean checkTargetExists(String baseUrl, String token, String path) {
        try {
            Map<String, Object> resp = alistClient.getFileInfo(baseUrl, token, path);
            return resp != null && resp.get("code") instanceof Number
                && ((Number) resp.get("code")).intValue() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private String toJson(Object obj) {
        try {
            return JsonMapper.builder().build().writeValueAsString(obj);
        } catch (Exception e) {
            return obj.toString();
        }
    }

    // ================================================================
    // 内部类
    // ================================================================

    /** 转码候选文件 */
    record TranscodeCandidate(String name, String fullPath, String targetPath, String format, long size) {
        static StorageEngine sourceEngine;
    }

    /** 转码结果 */
    record TranscodeResult(String sourceFileName, boolean success, String error) {}
}
