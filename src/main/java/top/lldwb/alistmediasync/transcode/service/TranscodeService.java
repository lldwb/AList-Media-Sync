package top.lldwb.alistmediasync.transcode.service;

import tools.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.lldwb.alistmediasync.common.config.AppProperties;
import top.lldwb.alistmediasync.common.enums.ConflictStrategy;
import top.lldwb.alistmediasync.common.enums.TargetFormat;
import top.lldwb.alistmediasync.common.util.*;
import top.lldwb.alistmediasync.sync.entity.SyncTask;
import top.lldwb.alistmediasync.sync.service.PostSyncTranscodeTrigger;
import top.lldwb.alistmediasync.execution.TaskExecution;
import top.lldwb.alistmediasync.execution.TaskExecutionRepository;
import top.lldwb.alistmediasync.transcode.dto.TranscodeTaskVO;
import top.lldwb.alistmediasync.storage.entity.StorageEngine;
import top.lldwb.alistmediasync.storage.service.StorageEngineService;
import top.lldwb.alistmediasync.transcode.entity.TranscodeTask;
import top.lldwb.alistmediasync.transcode.entity.TranscodeTask.TranscodeStatus;
import top.lldwb.alistmediasync.transcode.repository.TranscodeTaskRepository;
import top.lldwb.alistmediasync.storage.service.engine.StorageEngineStrategy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.ObjectProvider;

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
 * <p>
 * 职责拆分：状态机规则见 {@link TranscodeStateMachine}，源目录扫描见 {@link TranscodeScanner}；
 * 本类保留同名并作为编排门面，对外方法签名不变。
 * </p>
 * <p>
 * 依赖倒置：实现 {@code sync} 模块定义的 {@link PostSyncTranscodeTrigger} 接口，
 * 使 {@code sync} 无需 import 本模块即可在同步成功后触发后置转码。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranscodeService implements PostSyncTranscodeTrigger {

    private final TranscodeTaskRepository repository;
    private final TaskExecutionRepository taskExecutionRepository;
    private final StorageEngineService storageEngineService;
    private final AppProperties appProperties;
    private final TranscodeFileProcessor fileProcessor;
    /** 源目录扫描器（路径类型判定 + 目录递归扫描） */
    private final TranscodeScanner scanner;
    private final JsonMapper objectMapper;
    /** 自代理：@Transactional/@Async 注解经 Spring 代理才生效，同类自调用需经代理 */
    private final ObjectProvider<TranscodeService> selfProvider;

    /**
     * 验证状态转换是否合法
     * <p>
     * 门面方法：保留原有对外签名与异常行为，规则实现见 {@link TranscodeStateMachine}。
     * </p>
     *
     * @param from 当前状态
     * @param to   目标状态
     * @throws IllegalStateException 如果转换非法
     */
    public void validateTransition(TranscodeStatus from, TranscodeStatus to) {
        TranscodeStateMachine.validateTransition(from, to);
    }

    /**
     * 创建独立转码任务
     *
     * @param sourceDirectoryTranscode 源目录转码选项，true 时自动使用源文件目录作为目标路径
     */
    @Transactional
    public TranscodeTask createTask(Long sourceEngineId, Long targetEngineId,
                                     String sourcePath, String targetPath,
                                     TargetFormat targetFormat, Integer bitrate,
                                     boolean sourceDirectoryTranscode) {
        // 源目录转码：自动计算目标路径（源文件所在的父目录）
        if (sourceDirectoryTranscode) {
            targetPath = PathUtils.parentDir(sourcePath);
            if (targetPath.isEmpty()) {
                targetPath = "/";
            }
        } else if (targetPath == null || targetPath.isBlank()) {
            throw new IllegalArgumentException("未启用源目录转码时，目标路径为必填");
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
     * <p>
     * 通过自代理调用 {@link #executeTask}，确保其 {@code @Transactional} 注解生效
     * （同类直接调用不会经过 Spring AOP 代理，事务会静默失效）。
     * </p>
     */
    @Async("transcodeExecutor")
    public void executeAsync(TranscodeTask task) {
        TraceContext.runWith("transcode", "转码任务执行", () -> selfProvider.getObject().executeTask(task));
    }

    /**
     * 触发同步后置转码（{@link PostSyncTranscodeTrigger} 实现，由 SyncService 注入接口后调用）
     * <p>
     * 转调 {@link #executePostSyncTranscode(SyncTask, TaskExecution)}：两者同为同步的直接方法调用，
     * 不引入 Spring 事件、异步或新的事务边界，调用时序与运行结果完全一致。
     * </p>
     *
     * @param task      本次已同步成功的同步任务
     * @param execution 触发本次后置转码的同步执行记录
     */
    @Override
    public void trigger(SyncTask task, TaskExecution execution) {
        executePostSyncTranscode(task, execution);
    }

    /**
     * 同步后置转码（由 SyncService 调用）
     */
    public void executePostSyncTranscode(SyncTask syncTask, TaskExecution syncExecution) {
        // 沿用同步任务的 traceId，便于将同步+转码视作同一次任务链路
        TraceContext.runWith("transcode", "同步后置转码", () -> {
            log.info("同步后置转码开始：syncTask={}", syncTask.getName());
            TaskExecution execution = new TaskExecution();
            execution.setSyncTaskId(syncTask.getId());
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
            log.info("同步后置转码完成：syncTask={}, 成功 {} / 失败 {}",
                syncTask.getName(), execution.getSuccessFiles(), execution.getFailedFiles());
        });
    }

    // ================================================================
    // 核心转码流程（三步编排）
    // ================================================================

    /**
     * 执行转码任务（三步流程 + 状态机）
     * <p>
     * 从数据库重新加载实体以确保拿到最新版本号，避免乐观锁冲突。
     * 统一采用"先收集待处理文件列表，再并行处理"的架构：
     * <ul>
     *   <li><b>目录模式</b>：递归扫描目录下所有视频文件，并行执行下载→转码→上传</li>
     *   <li><b>文件模式</b>：将该单个文件视为扫描结果中唯一的一项，
     *       同样纳入统一的并行处理流水线</li>
     * </ul>
     * </p>
     */
    @Transactional
    void executeTask(TranscodeTask task) {
        // 重新加载实体，确保拿到最新的 @Version 字段值，避免分离实体 merge 时乐观锁冲突
        TranscodeTask managedTask = repository.findById(task.getId())
            .orElseThrow(() -> new NoSuchElementException("转码任务不存在：id=" + task.getId()));

        log.info("开始执行转码任务：id={}, sourcePath={}, targetFormat={}",
            managedTask.getId(), managedTask.getSourceFilePath(), managedTask.getTargetFormat());

        StorageEngine sourceEngine = managedTask.getSourceEngineId() != null
            ? storageEngineService.getEntity(managedTask.getSourceEngineId())
            : null;
        StorageEngine targetEngine = storageEngineService.getEntity(managedTask.getTargetEngineId());

        // 创建执行记录
        TaskExecution execution = new TaskExecution();
        execution.setTranscodeTaskId(managedTask.getId());
        execution.setTaskType(TaskExecution.TaskType.TRANSCODE);
        execution.setStartTime(java.time.LocalDateTime.now());
        execution.setStatus(TaskExecution.ExecutionStatus.RUNNING);
        execution = taskExecutionRepository.save(execution);

        try {
            List<TranscodeCandidate> candidates;
            boolean isDir = scanner.isDirectory(sourceEngine, managedTask.getSourceFilePath());

            if (isDir) {
                // 目录模式：递归扫描所有视频文件
                StorageEngineStrategy sourceStrategy = storageEngineService.resolve(sourceEngine);
                StorageEngineStrategy targetStrategy = storageEngineService.resolve(targetEngine);
                candidates = scanner.scanSourceDirectory(
                    sourceEngine, sourceStrategy, targetEngine, targetStrategy,
                    managedTask.getSourceFilePath(), managedTask.getTargetFilePath(),
                    ConflictStrategy.SKIP);
            } else {
                // 文件模式：构建单元素候选列表，统一纳入并行处理流水线
                String sourcePath = managedTask.getSourceFilePath();
                String name = sourcePath.substring(sourcePath.lastIndexOf('/') + 1);
                candidates = List.of(new TranscodeCandidate(
                    name, sourcePath, managedTask.getTargetFilePath(),
                    MagicBytesDetector.detectByExtension(name), 0, sourceEngine));
            }

            if (candidates.isEmpty()) {
                log.info("未发现需要转码的文件：{}", managedTask.getSourceFilePath());
                execution.setTotalFiles(0);
                execution.setSuccessFiles(0);
                execution.setStatus(TaskExecution.ExecutionStatus.SUCCESS);
                managedTask.setStatus(TranscodeStatus.COMPLETED);
                managedTask.setProgress(1000);
                return;
            }

            log.info("收集到 {} 个待转码文件", candidates.size());
            execution.setTotalFiles(candidates.size());

            // 统一并行处理
            int successCount = processCandidates(candidates, managedTask.getTargetFormat(),
                targetEngine, execution);

            managedTask.setStatus(TranscodeStatus.COMPLETED);
            managedTask.setProgress(1000);
            if (successCount == candidates.size()) {
                execution.setStatus(TaskExecution.ExecutionStatus.SUCCESS);
            }
            log.info("转码任务已完成：{}，成功 {} / 失败 {}",
                managedTask.getSourceFilePath(), successCount,
                candidates.size() - successCount);
        } catch (Exception e) {
            TraceContext.setErrorType(e.getClass().getSimpleName());
            log.error("转码任务失败：{} — {}", managedTask.getSourceFilePath(), e.getMessage(), e);
            // 单步失败状态已由处理流程设置；编排级失败（扫描/收集异常、全部文件失败）设为 FAILED，
            // 避免任务永久停留在 PENDING/DOWNLOADING 等中间状态
            if (!TranscodeStateMachine.isFailureStatus(managedTask.getStatus())) {
                managedTask.setErrorMessage(e.getMessage());
                managedTask.setStatus(TranscodeStatus.FAILED);
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
     * 并行处理候选文件列表（统一的下载→转码→上传流水线）
     * <p>
     * 对每个 {@link TranscodeCandidate} 调用 {@link TranscodeFileProcessor#process}，
     * 由 fileProcessor 内部执行三步流程并维护独立的 TranscodeTask 状态记录。
     * 此方法为文件模式和目录模式提供统一的并行处理入口。
     * </p>
     *
     * @return 成功处理的文件数量
     */
    private int processCandidates(List<TranscodeCandidate> candidates,
                                  TargetFormat targetFormat,
                                  StorageEngine targetEngine,
                                  TaskExecution execution) {
        String tempSuffix = appProperties.getTranscode().getTempSuffix();
        Path tempDir = Path.of(appProperties.getTranscode().getTempDir());
        try {
            Files.createDirectories(tempDir);
        } catch (IOException e) {
            throw new RuntimeException("无法创建临时目录：" + tempDir, e);
        }

        List<CompletableFuture<TranscodeResult>> futures = new ArrayList<>();
        for (TranscodeCandidate candidate : candidates) {
            CompletableFuture<TranscodeResult> future = fileProcessor.process(
                candidate, targetFormat, tempSuffix, tempDir, targetEngine, null, execution);
            futures.add(future);
        }

        // 收集结果
        ResultCollection collected = collectResults(futures, candidates);
        int successCount = collected.successCount();
        List<String> failures = collected.failures();

        execution.setSuccessFiles(successCount);
        execution.setFailedFiles(failures.size());
        if (!failures.isEmpty()) {
            execution.setFailureDetails(JsonUtils.toJson(objectMapper, failures));
            if (successCount > 0) {
                execution.setStatus(TaskExecution.ExecutionStatus.PARTIAL_SUCCESS);
            } else {
                execution.setStatus(TaskExecution.ExecutionStatus.FAILED);
                throw new RuntimeException("所有文件转码失败：" + failures.size() + " 个");
            }
        }
        log.info("并行转码完成：成功 {}，失败 {}", successCount, failures.size());
        return successCount;
    }

    // ================================================================
    // 内部转码流程（扫描 → 并行转发）
    // ================================================================

    private void executeTaskInternal(StorageEngine sourceEngine, StorageEngine targetEngine,
                                      String sourcePath, String targetPath, String targetFormatStr,
                                      ConflictStrategy conflictStrategy,
                                      SyncTask syncTask, TaskExecution execution) {

        log.info("后置转码开始：sourcePath={}, targetPath={}, format={}", sourcePath, targetPath, targetFormatStr);
        TargetFormat targetFormat = TargetFormat.valueOf(targetFormatStr);
        StorageEngineStrategy sourceStrategy = storageEngineService.resolve(sourceEngine);
        StorageEngineStrategy targetStrategy = storageEngineService.resolve(targetEngine);

        // 阶段 1：扫描源目录（仅文件，过滤目录）
        List<TranscodeCandidate> candidates = scanner.scanSourceDirectory(
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
        ResultCollection collected = collectResults(futures, candidates);
        int successCount = collected.successCount();
        List<String> failures = collected.failures();

        execution.setSuccessFiles(successCount);
        execution.setFailedFiles(failures.size());
        if (!failures.isEmpty()) {
            execution.setFailureDetails(JsonUtils.toJson(objectMapper, failures));
            execution.setStatus(successCount > 0
                ? TaskExecution.ExecutionStatus.PARTIAL_SUCCESS
                : TaskExecution.ExecutionStatus.FAILED);
        } else {
            execution.setStatus(TaskExecution.ExecutionStatus.SUCCESS);
        }
        log.info("后置转码完成：sourcePath={}, 成功 {} / 失败 {}", sourcePath, successCount, failures.size());
    }

    // ================================================================
    // 重试逻辑
    // ================================================================

    /**
     * 从任意失败状态重试
     * <p>
     * 状态回退并保存后，经自代理调用 {@link #executeTask} 真正触发执行，
     * 避免任务仅被标记为"进行中"却无任何执行（此前缺陷）。
     * </p>
     */
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
                TranscodeStateMachine.transition(task, TranscodeStatus.DOWNLOADING);
                task.setErrorMessage(null);
                repository.save(task);
                log.info("转码任务重试：{} — 重新下载", task.getSourceFilePath());
            }
            case TRANSCODE_FAILED -> {
                // 保留源临时文件，跳过下载步骤
                TranscodeStateMachine.transition(task, TranscodeStatus.TRANSCODING);
                task.setErrorMessage(null);
                repository.save(task);
                log.info("转码任务重试：{} — 跳过下载，重新转码", task.getSourceFilePath());
            }
            case UPLOAD_FAILED -> {
                // 保留源+输出临时文件，跳过前两步
                TranscodeStateMachine.transition(task, TranscodeStatus.UPLOADING);
                task.setErrorMessage(null);
                repository.save(task);
                log.info("转码任务重试：{} — 跳过下载和转码，重新上传", task.getSourceFilePath());
            }
            case FAILED -> {
                // 编排级失败：清空错误，重新执行
                task.setErrorMessage(null);
                repository.save(task);
                log.info("转码任务重试：{} — 重新执行", task.getSourceFilePath());
            }
            default -> throw new IllegalStateException(
                "仅失败状态的转码任务可重试，当前状态：" + status);
        }

        // 状态回退提交后，真正触发执行（经自代理调用，确保 executeTask 的 @Transactional 生效）
        selfProvider.getObject().executeTask(task);
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

    /**
     * 按状态批量删除转码任务
     * <p>
     * 删除前先解除 {@code task_execution.transcode_task_id} 外键引用（置为 NULL），
     * 以保留历史执行记录并避免完整性约束冲突。整个过程在同一事务内完成。
     * </p>
     *
     * @param statuses 待删除任务的状态集合
     * @return 实际删除的转码任务数量
     */
    @Transactional
    public int deleteByStatusIn(List<TranscodeStatus> statuses) {
        List<Long> taskIds = repository.findByStatusIn(statuses).stream()
            .map(TranscodeTask::getId)
            .toList();
        if (taskIds.isEmpty()) {
            return 0;
        }
        // 1. 先解除 TaskExecution 对这些转码任务的外键引用（保留历史记录）
        taskExecutionRepository.nullifyTranscodeTaskRefs(taskIds);
        // 2. 再批量删除转码任务
        int deleted = repository.deleteByStatusIn(statuses);
        log.info("已按状态批量删除转码任务：statuses={}, 删除数量={}", statuses, deleted);
        return deleted;
    }

    /**
     * 按状态批量查询转码任务数量
     */
    public long countByStatusIn(List<TranscodeStatus> statuses) {
        return repository.countByStatusIn(statuses);
    }

    /**
     * 按状态批量查询转码任务
     */
    public List<TranscodeTask> findByStatusIn(List<TranscodeStatus> statuses) {
        return repository.findByStatusIn(statuses);
    }

    // ================================================================
    // 私有辅助方法
    // ================================================================

    /**
     * 收集并行转码结果（文件模式与后置转码模式共用）
     * <p>
     * 按提交顺序等待每个 future 完成（超时 10 分钟），统计成功数量并汇总失败明细：
     * 任务本身失败取结果中的文件名与错误信息，等待异常（超时/中断）取候选文件名与异常消息。
     * 本方法只做收集，不写回执行记录、不改变任务状态——状态回写与失败处置由调用方按各自语义处理
     * （目录模式在全失败时抛异常，后置转码模式仅置为 FAILED）。
     * </p>
     *
     * @param futures    已提交的并行转码任务，顺序与 {@code candidates} 一致
     * @param candidates 与 {@code futures} 一一对应的候选文件
     * @return 成功数量与失败明细
     */
    private ResultCollection collectResults(List<CompletableFuture<TranscodeResult>> futures,
                                            List<TranscodeCandidate> candidates) {
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
        return new ResultCollection(successCount, failures);
    }

    /** 并行转码结果收集产物（成功数量 + 失败明细） */
    private record ResultCollection(int successCount, List<String> failures) {
    }
}
