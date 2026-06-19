package top.lldwb.alistmediasync.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.lldwb.alistmediasync.client.AListClient;
import top.lldwb.alistmediasync.config.AppProperties;
import top.lldwb.alistmediasync.entity.*;
import top.lldwb.alistmediasync.repository.*;
import top.lldwb.alistmediasync.util.*;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 转码引擎（编排层）
 * <p>
 * 实现两阶段处理的视频转码系统：
 * <ol>
 *   <li><b>阶段 1 — 扫描</b>：单线程递归遍历源目录，MagicBytes 检测视频格式，收集候选文件</li>
 *   <li><b>阶段 2 — 并行转码</b>：委托 {@link TranscodeFileProcessor} 异步执行转码</li>
 * </ol>
 * </p>
 * <p>
 * 文件转码逻辑已提取至 {@link TranscodeFileProcessor}（方案 B），
 * 彻底消除 SyncService ↔ TranscodeService 的循环依赖。
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
    private final TranscodeFileProcessor fileProcessor;

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
    // 核心转码流程（编排）
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
     * 内部转码执行流程（编排：扫描 → 并行转发到 TranscodeFileProcessor）
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

        // 阶段 2：并行转码 — 委托到 TranscodeFileProcessor（无循环依赖）
        List<CompletableFuture<TranscodeResult>> futures = new ArrayList<>();
        for (TranscodeCandidate candidate : candidates) {
            CompletableFuture<TranscodeResult> future = fileProcessor.process(
                candidate, targetFormat, tempSuffix, tempDir, targetEngine, syncTask, execution);
            futures.add(future);
        }

        // 收集结果
        int successCount = 0;
        List<String> failures = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            try {
                TranscodeResult result = futures.get(i).get(10, TimeUnit.MINUTES);
                if (result.success()) {
                    successCount++;
                } else {
                    failures.add(result.sourceFileName() + ": " + result.error());
                }
            } catch (Exception e) {
                failures.add(candidates.get(i).name() + ": " + e.getMessage());
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
    // 内部类（已提取为独立文件：TranscodeCandidate.java / TranscodeResult.java）
    // ================================================================
}
