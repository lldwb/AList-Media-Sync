package top.lldwb.alistmediasync.transcode.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.lldwb.alistmediasync.common.config.AppProperties;
import top.lldwb.alistmediasync.sync.dto.sync.FileEntry;
import top.lldwb.alistmediasync.sync.entity.SyncTask;
import top.lldwb.alistmediasync.sync.entity.TaskExecution;
import top.lldwb.alistmediasync.sync.repository.TaskExecutionRepository;
import top.lldwb.alistmediasync.transcode.dto.transcode.TranscodeTaskVO;
import top.lldwb.alistmediasync.storage.entity.StorageEngine;
import top.lldwb.alistmediasync.storage.service.StorageEngineService;
import top.lldwb.alistmediasync.transcode.entity.TranscodeTask;
import top.lldwb.alistmediasync.transcode.entity.TranscodeTask.TranscodeStatus;
import top.lldwb.alistmediasync.transcode.repository.TranscodeTaskRepository;
import top.lldwb.alistmediasync.storage.service.engine.StorageEngineStrategy;
import top.lldwb.alistmediasync.common.util.*;
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
 * 实现三步流程转码系统：
 * <ol>
 *   <li><b>下载</b>：从源存储引擎下载源文件到临时目录</li>
 *   <li><b>转码</b>：FFmpeg 转码为指定格式</li>
 *   <li><b>上传</b>：上传转码输出到目标存储引擎</li>
 * </ol>
 * 采用 8 状态模型，每步可独立失败和重试。
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
    private final StorageEngineService storageEngineService;
    private final AppProperties appProperties;
    private final TranscodeFileProcessor fileProcessor;

    /**
     * 合法状态转换集合（8 状态模型）
     * <p>
     * 三步流程：PENDING → DOWNLOADING → TRANSCODING → UPLOADING → COMPLETED，
     * 每步可独立失败，重试从失败步骤继续。
     * </p>
     */
    private static final Set<Map.Entry<TranscodeStatus, TranscodeStatus>> VALID_TRANSITIONS = Set.of(
        Map.entry(TranscodeStatus.PENDING, TranscodeStatus.DOWNLOADING),
        Map.entry(TranscodeStatus.DOWNLOADING, TranscodeStatus.TRANSCODING),
        Map.entry(TranscodeStatus.DOWNLOADING, TranscodeStatus.DOWNLOAD_FAILED),
        Map.entry(TranscodeStatus.DOWNLOAD_FAILED, TranscodeStatus.DOWNLOADING),    // 重试
        Map.entry(TranscodeStatus.TRANSCODING, TranscodeStatus.UPLOADING),
        Map.entry(TranscodeStatus.TRANSCODING, TranscodeStatus.TRANSCODE_FAILED),
        Map.entry(TranscodeStatus.TRANSCODE_FAILED, TranscodeStatus.TRANSCODING),   // 重试
        Map.entry(TranscodeStatus.UPLOADING, TranscodeStatus.COMPLETED),
        Map.entry(TranscodeStatus.UPLOADING, TranscodeStatus.UPLOAD_FAILED),
        Map.entry(TranscodeStatus.UPLOAD_FAILED, TranscodeStatus.UPLOADING)         // 重试
    );

    /**
     * 验证状态转换是否合法
     *
     * @param from 当前状态
     * @param to   目标状态
     * @throws IllegalStateException 如果转换非法
     */
    public void validateTransition(TranscodeStatus from, TranscodeStatus to) {
        if (!VALID_TRANSITIONS.contains(Map.entry(from, to))) {
            throw new IllegalStateException(
                String.format("非法的转码状态转换：%s → %s", from, to));
        }
    }

    /**
     * 创建独立转码任务
     *
     * @param sameDirectoryTranscode 原目录转码选项，true 时自动使用源文件目录作为目标路径
     */
    @Transactional
    public TranscodeTask createTask(Long sourceEngineId, Long targetEngineId,
                                     String sourcePath, String targetPath,
                                     TranscodeTask.TargetFormat targetFormat, Integer bitrate,
                                     boolean sameDirectoryTranscode) {
        // 原目录转码：自动计算目标路径
        if (sameDirectoryTranscode) {
            targetPath = getDirPath(sourcePath);
            if (targetPath.isEmpty()) {
                targetPath = "/";
            }
        } else if (targetPath == null || targetPath.isBlank()) {
            throw new IllegalArgumentException("未启用原目录转码时，目标路径为必填");
        }

        TranscodeTask task = new TranscodeTask();
        task.setSourceEngineId(sourceEngineId);
        task.setTargetEngineId(targetEngineId);
        task.setSourceFilePath(sourcePath);
        task.setTargetFilePath(targetPath);
        task.setTargetFormat(targetFormat);
        task.setBitrate(bitrate != null ? bitrate : appProperties.getTranscode().getDefaultBitrate());
        task.setStatus(TranscodeStatus.PENDING);
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
        TaskExecution execution = new TaskExecution();
        execution.setSyncTask(syncTask);
        execution.setTaskType(TaskExecution.TaskType.TRANSCODE);
        execution.setStartTime(java.time.LocalDateTime.now());
        execution.setStatus(TaskExecution.ExecutionStatus.RUNNING);
        execution = taskExecutionRepository.save(execution);

        executeTaskInternal(syncTask.getSourceEngine(), syncTask.getTargetEngine(),
            syncTask.getSourcePath(), syncTask.getTargetPath(),
            syncTask.getTargetFormat().name(), syncTask.getConflictStrategy(),
            syncTask, execution);

        execution.setEndTime(java.time.LocalDateTime.now());
        taskExecutionRepository.save(execution);
    }

    // ================================================================
    // 核心转码流程（三步编排）
    // ================================================================

    /**
     * 执行转码任务（三步流程 + 状态机）
     * <p>
     * 从数据库重新加载实体以确保拿到最新版本号，避免乐观锁冲突。
     * 三步流程中的状态变更仅修改内存对象，最终在 finally 块统一持久化。
     * </p>
     */
    @Transactional
    void executeTask(TranscodeTask task) {
        // 重新加载实体，确保拿到最新的 @Version 字段值，避免分离实体 merge 时乐观锁冲突
        TranscodeTask managedTask = repository.findById(task.getId())
            .orElseThrow(() -> new NoSuchElementException("转码任务不存在：id=" + task.getId()));

        StorageEngine sourceEngine = managedTask.getSourceEngineId() != null
            ? storageEngineService.getEntity(managedTask.getSourceEngineId())
            : null;
        StorageEngine targetEngine = storageEngineService.getEntity(managedTask.getTargetEngineId());

        // 创建执行记录
        TaskExecution execution = new TaskExecution();
        execution.setTranscodeTask(managedTask);
        execution.setTaskType(TaskExecution.TaskType.TRANSCODE);
        execution.setStartTime(java.time.LocalDateTime.now());
        execution.setStatus(TaskExecution.ExecutionStatus.RUNNING);
        execution = taskExecutionRepository.save(execution);

        try {
            executeThreeStepFlow(managedTask, sourceEngine, targetEngine);
            managedTask.setStatus(TranscodeStatus.COMPLETED);
            managedTask.setProgress(1000);
            execution.setStatus(TaskExecution.ExecutionStatus.SUCCESS);
            log.info("转码任务已完成：{}", managedTask.getSourceFilePath());
        } catch (Exception e) {
            log.error("转码任务失败：{} — {}", managedTask.getSourceFilePath(), e.getMessage(), e);
            // 仅在非失败状态时设置（可能已在三步流程中设置具体失败状态）
            if (!isFailureStatus(managedTask.getStatus())) {
                managedTask.setErrorMessage(e.getMessage());
            }
            execution.setStatus(TaskExecution.ExecutionStatus.FAILED);
            execution.setFailureDetails(e.getMessage());
        } finally {
            execution.setEndTime(java.time.LocalDateTime.now());
            taskExecutionRepository.save(execution);
            repository.save(managedTask);
        }
    }

    /**
     * 三步流程编排
     */
    private void executeThreeStepFlow(TranscodeTask task, StorageEngine sourceEngine,
                                       StorageEngine targetEngine) {
        Path tempDir = Path.of(appProperties.getTranscode().getTempDir());

        // 创建临时目录
        try {
            Files.createDirectories(tempDir);
        } catch (IOException e) {
            throw new RuntimeException("无法创建临时目录：" + tempDir, e);
        }

        StorageEngineStrategy sourceStrategy = sourceEngine != null
            ? storageEngineService.resolve(sourceEngine) : null;
        StorageEngineStrategy targetStrategy = storageEngineService.resolve(targetEngine);

        try {
            // 步骤 1：下载源文件
            transition(task, TranscodeStatus.DOWNLOADING);
            if (sourceStrategy == null) {
                throw new RuntimeException("源存储引擎不存在");
            }
            String sourceTempPath = downloadSourceFile(task, sourceEngine, sourceStrategy, tempDir);
            task.setTempSourcePath(sourceTempPath);

            // 步骤 2：转码
            transition(task, TranscodeStatus.TRANSCODING);
            String outputTempPath = transcodeFile(task, sourceTempPath, tempDir);
            task.setTempFilePath(outputTempPath);

            // 步骤 3：上传
            transition(task, TranscodeStatus.UPLOADING);
            uploadOutputFile(task, targetEngine, targetStrategy, outputTempPath);

            // 成功 — 清理临时文件
            cleanupTempFiles(sourceTempPath, outputTempPath);

        } catch (Exception e) {
            // 已在 transition 中设置具体失败状态
            throw e;
        }
    }

    /**
     * 步骤 1：下载源文件到临时目录
     */
    private String downloadSourceFile(TranscodeTask task, StorageEngine sourceEngine,
                                       StorageEngineStrategy strategy, Path tempDir) {
        try {
            String sourceFormat = task.getSourceFilePath().substring(
                task.getSourceFilePath().lastIndexOf('.') + 1).toLowerCase();
            Path sourceTemp = Files.createTempFile(tempDir, "src-", "." + sourceFormat);
            try (InputStream in = strategy.downloadFile(sourceEngine, task.getSourceFilePath())) {
                if (in == null) throw new IOException("下载源文件失败：" + task.getSourceFilePath());
                Files.copy(in, sourceTemp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("源文件已下载：{} -> {}", task.getSourceFilePath(), sourceTemp);
            return sourceTemp.toString();
        } catch (Exception e) {
            transition(task, TranscodeStatus.DOWNLOAD_FAILED);
            task.setErrorMessage("下载失败：" + e.getMessage());
            throw new RuntimeException("下载源文件失败", e);
        }
    }

    /**
     * 步骤 2：转码
     */
    private String transcodeFile(TranscodeTask task, String sourceTempPath, Path tempDir) {
        try {
            TranscodeTask.TargetFormat targetFormat = task.getTargetFormat();
            String outputExt = targetFormat.name().toLowerCase();
            Path outputTemp = Files.createTempFile(tempDir, "out-", "." + outputExt);

            // 委托 TranscodeFileProcessor 执行实际转码
            fileProcessor.doTranscode(
                Path.of(sourceTempPath), outputTemp, targetFormat,
                task.getBitrate() != null ? task.getBitrate()
                    : appProperties.getTranscode().getDefaultBitrate(),
                task);

            log.info("转码完成：{} -> {}", sourceTempPath, outputTemp);
            return outputTemp.toString();
        } catch (Exception e) {
            transition(task, TranscodeStatus.TRANSCODE_FAILED);
            task.setErrorMessage("转码失败：" + e.getMessage());
            throw new RuntimeException("转码失败", e);
        }
    }

    /**
     * 步骤 3：上传转码输出
     */
    private void uploadOutputFile(TranscodeTask task, StorageEngine targetEngine,
                                   StorageEngineStrategy strategy, String outputTempPath) {
        try {
            Path outputFile = Path.of(outputTempPath);
            String targetFileName = getOutputName(task.getSourceFilePath(), task.getTargetFormat());
            String remotePath = concatPath(
                getDirPath(task.getTargetFilePath()), targetFileName);

            try (InputStream fileIn = Files.newInputStream(outputFile)) {
                strategy.uploadFile(targetEngine, remotePath, fileIn, Files.size(outputFile));
            }
            log.info("转码文件已上传：{} -> {}", outputTempPath, remotePath);
        } catch (Exception e) {
            transition(task, TranscodeStatus.UPLOAD_FAILED);
            task.setErrorMessage("上传失败：" + e.getMessage());
            throw new RuntimeException("上传转码文件失败", e);
        }
    }

    // ================================================================
    // 内部转码流程（扫描 → 并行转发）
    // ================================================================

    private void executeTaskInternal(StorageEngine sourceEngine, StorageEngine targetEngine,
                                      String sourcePath, String targetPath, String targetFormatStr,
                                      SyncTask.ConflictStrategy conflictStrategy,
                                      SyncTask syncTask, TaskExecution execution) {

        TranscodeTask.TargetFormat targetFormat = TranscodeTask.TargetFormat.valueOf(targetFormatStr);
        StorageEngineStrategy sourceStrategy = storageEngineService.resolve(sourceEngine);
        StorageEngineStrategy targetStrategy = storageEngineService.resolve(targetEngine);

        // 阶段 1：扫描源目录（仅文件，过滤目录）
        List<TranscodeCandidate> candidates = scanSourceDirectory(
            sourceEngine, sourceStrategy, targetEngine, targetStrategy,
            sourcePath, targetPath, conflictStrategy);

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
        String tempSuffix = appProperties.getTranscode().getTempSuffix();
        Path tempDir = Path.of(appProperties.getTranscode().getTempDir());

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
    // 扫描源目录（递归，只收集文件不收集目录）
    // ================================================================

    private List<TranscodeCandidate> scanSourceDirectory(
        StorageEngine sourceEngine, StorageEngineStrategy sourceStrategy,
        StorageEngine targetEngine, StorageEngineStrategy targetStrategy,
        String sourcePath, String targetPath, SyncTask.ConflictStrategy conflictStrategy) {

        List<TranscodeCandidate> candidates = new ArrayList<>();
        scanDirectoryRecursive(sourceEngine, sourceStrategy, targetEngine, targetStrategy,
            sourcePath, targetPath, conflictStrategy, candidates, 1, 10);
        return candidates;
    }

    private void scanDirectoryRecursive(
        StorageEngine sourceEngine, StorageEngineStrategy sourceStrategy,
        StorageEngine targetEngine, StorageEngineStrategy targetStrategy,
        String sourceDir, String targetDir, SyncTask.ConflictStrategy conflictStrategy,
        List<TranscodeCandidate> candidates, int depth, int maxDepth) {

        if (depth > maxDepth) {
            log.warn("扫描深度已达上限 {}，停止递归：{}", maxDepth, sourceDir);
            return;
        }

        List<FileEntry> entries = sourceStrategy.listFiles(sourceEngine, sourceDir, 1, Integer.MAX_VALUE);
        for (FileEntry entry : entries) {
            String name = entry.name();
            String fullPath = concatPath(sourceDir, name);

            if (entry.isDirectory()) {
                scanDirectoryRecursive(sourceEngine, sourceStrategy, targetEngine, targetStrategy,
                    fullPath, concatPath(targetDir, name), conflictStrategy,
                    candidates, depth + 1, maxDepth);
                continue;
            }

            // 魔数检测视频格式
            String format = MagicBytesDetector.detectByExtension(name);
            if ("UNKNOWN".equals(format)) {
                continue; // 非视频文件，跳过
            }

            // 检查目标是否已存在
            boolean targetExists = checkTargetExists(targetEngine, targetStrategy,
                concatPath(targetDir, getOutputName(name)));
            if (targetExists && conflictStrategy == SyncTask.ConflictStrategy.SKIP) {
                log.debug("目标文件已存在，跳过：{}", name);
                continue;
            }

            candidates.add(new TranscodeCandidate(
                name, fullPath, concatPath(targetDir, name), format, entry.size(), sourceEngine));
        }
    }

    // ================================================================
    // 重试逻辑
    // ================================================================

    /**
     * 从任意失败状态重试
     */
    @Transactional
    public void retry(Long taskId) {
        TranscodeTask task = repository.findById(taskId)
            .orElseThrow(() -> new NoSuchElementException("转码任务不存在：id=" + taskId));

        TranscodeStatus status = task.getStatus();

        switch (status) {
            case DOWNLOAD_FAILED -> {
                // 清理部分下载文件
                if (task.getTempSourcePath() != null) {
                    TempFileManager.deleteQuietly(Path.of(task.getTempSourcePath()));
                    task.setTempSourcePath(null);
                }
                // 回退到下载中
                transition(task, TranscodeStatus.DOWNLOADING);
                task.setErrorMessage(null);
                repository.save(task);
                log.info("转码任务重试：{} — 重新下载", task.getSourceFilePath());
            }
            case TRANSCODE_FAILED -> {
                // 保留源临时文件，跳过下载步骤
                transition(task, TranscodeStatus.TRANSCODING);
                task.setErrorMessage(null);
                repository.save(task);
                log.info("转码任务重试：{} — 跳过下载，重新转码", task.getSourceFilePath());
            }
            case UPLOAD_FAILED -> {
                // 保留源+输出临时文件，跳过前两步
                transition(task, TranscodeStatus.UPLOADING);
                task.setErrorMessage(null);
                repository.save(task);
                log.info("转码任务重试：{} — 跳过下载和转码，重新上传", task.getSourceFilePath());
            }
            default -> throw new IllegalStateException(
                "仅失败状态的转码任务可重试，当前状态：" + status);
        }
    }

    // ================================================================
    // 查询方法
    // ================================================================

    /**
     * 查询所有转码任务
     */
    public List<TranscodeTaskVO> listAll() {
        return repository.findAll().stream()
            .map(TranscodeTaskVO::from)
            .toList();
    }

    /**
     * 查询单个转码任务
     */
    public TranscodeTaskVO getById(Long id) {
        return repository.findById(id)
            .map(TranscodeTaskVO::from)
            .orElseThrow(() -> new NoSuchElementException("转码任务不存在：id=" + id));
    }

    // ================================================================
    // 私有辅助方法
    // ================================================================

    /**
     * 执行状态转换并校验（仅更新内存状态，不单独持久化）
     * <p>
     * 状态变更由调用方统一持久化，避免事务内多次 save 引发乐观锁冲突。
     * </p>
     */
    private void transition(TranscodeTask task, TranscodeStatus targetStatus) {
        validateTransition(task.getStatus(), targetStatus);
        task.setStatus(targetStatus);
    }

    /**
     * 检查是否为失败状态
     */
    private boolean isFailureStatus(TranscodeStatus status) {
        return status == TranscodeStatus.DOWNLOAD_FAILED
            || status == TranscodeStatus.TRANSCODE_FAILED
            || status == TranscodeStatus.UPLOAD_FAILED;
    }

    /**
     * 清理临时文件
     */
    private void cleanupTempFiles(String sourceTempPath, String outputTempPath) {
        if (sourceTempPath != null) {
            TempFileManager.deleteQuietly(Path.of(sourceTempPath));
        }
        if (outputTempPath != null) {
            TempFileManager.deleteQuietly(Path.of(outputTempPath));
        }
    }

    private boolean checkTargetExists(StorageEngine engine, StorageEngineStrategy strategy, String path) {
        try {
            FileEntry info = strategy.getFileInfo(engine, path);
            return info != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** 获取转码后输出文件名 */
    private String getOutputName(String sourceName) {
        int dotIdx = sourceName.lastIndexOf('.');
        String baseName = dotIdx > 0 ? sourceName.substring(0, dotIdx) : sourceName;
        return baseName + ".mp3";
    }

    /** 带目标格式的输出文件名 */
    private String getOutputName(String sourceName, TranscodeTask.TargetFormat targetFormat) {
        int dotIdx = sourceName.lastIndexOf('.');
        String baseName = dotIdx > 0 ? sourceName.substring(0, dotIdx) : sourceName;
        return baseName + "." + targetFormat.name().toLowerCase();
    }

    /** 获取目标目录路径 */
    private String getDirPath(String fullPath) {
        int idx = fullPath.lastIndexOf('/');
        return idx > 0 ? fullPath.substring(0, idx) : "";
    }

    private String concatPath(String dir, String name) {
        if (dir == null || dir.isEmpty() || dir.equals("/")) return "/" + name;
        return dir.endsWith("/") ? dir + name : dir + "/" + name;
    }

    private String toJson(Object obj) {
        try {
            return JsonMapper.builder().build().writeValueAsString(obj);
        } catch (Exception e) {
            return obj.toString();
        }
    }
}
