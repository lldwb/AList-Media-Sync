package top.lldwb.alistmediasync.sync.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.lldwb.alistmediasync.storage.entity.StorageEngine;
import top.lldwb.alistmediasync.storage.service.StorageEngineService;
import top.lldwb.alistmediasync.common.service.WsSessionManager;
import top.lldwb.alistmediasync.common.util.TraceContext;
import top.lldwb.alistmediasync.sync.entity.SyncTask;
import top.lldwb.alistmediasync.sync.entity.TaskExecution;
import top.lldwb.alistmediasync.sync.repository.SyncTaskRepository;
import top.lldwb.alistmediasync.sync.repository.TaskExecutionRepository;
import top.lldwb.alistmediasync.storage.service.engine.StorageEngineStrategy;
import top.lldwb.alistmediasync.transcode.service.TranscodeService;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 同步执行引擎（核心服务）
 * <p>
 * 实现三模式文件同步：
 * <ul>
 *   <li>NEW_ONLY：仅同步目标不存在的新增文件</li>
 *   <li>FULL：完全同步（新增 + 删除目标多余文件）</li>
 *   <li>MOVE：移动模式（同步后删除源文件）</li>
 * </ul>
 * </p>
 * <p>
 * 同步分三个阶段：
 * <ol>
 *   <li>扫描阶段：递归遍历源目录，应用排除规则</li>
 *   <li>比对阶段：计算源-目标差异</li>
 *   <li>执行阶段：流式下载→上传，实时更新进度</li>
 * </ol>
 * 通过 StorageEngineService 策略分发，支持 AList 和本地路径两种引擎。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncService {

    private final StorageEngineService storageEngineService;
    private final SyncTaskRepository syncTaskRepository;
    private final TaskExecutionRepository taskExecutionRepository;
    private final TranscodeService transcodeService;
    private final JsonMapper objectMapper;
    private final WsSessionManager wsSessionManager;

    /** 正在执行的任务执行记录缓存（用于进度查询） */
    private final Map<Long, TaskExecution> activeExecutions = new ConcurrentHashMap<>();

    /**
     * 执行同步任务（异步）
     *
     * @param task 同步任务实体（仅使用其 ID，方法内通过 Repository 重新加载以避免 LazyInitializationException）
     */
    @Async
    @Transactional
    public void executeSyncTask(SyncTask task) {
        // 任务级 traceId：HTTP 触发时继承 MDC 中的 traceId；定时/异步触发时生成新值
        String traceId = TraceContext.getTraceId();
        if (traceId == null) {
            traceId = TraceContext.generate();
        }
        TraceContext.setTraceId(traceId);
        TraceContext.setModuleOperation("sync", "同步任务执行");

        try {
            // 异步线程独立 Session：必须按 ID 重新加载，确保 @ManyToOne(LAZY) 关联（sourceEngine/targetEngine）
            // 在当前事务 Session 内可被初始化，避免 detached 代理触发 LazyInitializationException
            final Long taskId = task.getId();
            task = syncTaskRepository.findById(taskId)
                .orElseThrow(() -> new NoSuchElementException("同步任务不存在：id=" + taskId));

            executeSyncTaskInternal(task);
        } finally {
            TraceContext.clear();
        }
    }

    /**
     * 同步任务执行内部逻辑（traceId 已由外层 {@link #executeSyncTask(SyncTask)} 设置）
     */
    private void executeSyncTaskInternal(SyncTask task) {        // 冲突检测：是否有其他 SyncTask 向同一目标路径写入且正在运行中
        List<TaskExecution> conflicting = taskExecutionRepository.findByStatusAndTaskType(
            TaskExecution.ExecutionStatus.RUNNING, TaskExecution.TaskType.SYNC);
        if (!conflicting.isEmpty()) {
            log.warn("存在正在运行的同步任务，当前任务将继续执行：{}", task.getName());
        }

        // 创建执行记录
        TaskExecution execution = new TaskExecution();
        execution.setSyncTask(task);
        execution.setTaskType(TaskExecution.TaskType.SYNC);
        execution.setStartTime(LocalDateTime.now());
        execution.setStatus(TaskExecution.ExecutionStatus.RUNNING);
        execution.setTotalFiles(0);
        execution.setSuccessFiles(0);
        execution.setFailedFiles(0);
        execution = taskExecutionRepository.save(execution);
        activeExecutions.put(execution.getId(), execution);

        StorageEngine sourceEngine = task.getSourceEngine();
        StorageEngine targetEngine = task.getTargetEngine();
        StorageEngineStrategy sourceStrategy = storageEngineService.resolve(sourceEngine);
        StorageEngineStrategy targetStrategy = storageEngineService.resolve(targetEngine);

        // 检测是否同引擎
        boolean sameEngine = sourceEngine.getId().equals(targetEngine.getId());

        List<String> failedFiles = new ArrayList<>();
        int completedCount = 0;

        String sourceRootPath = task.getSourcePath();
        String targetRootPath = task.getTargetPath();

        try {
            log.info("同步任务开始执行：{} (模式: {}, {} -> {}, 同引擎: {})",
                task.getName(), task.getSyncMode(), sourceRootPath, targetRootPath, sameEngine);

            // 阶段 1：扫描源目录
            List<FileInfo> sourceFiles = scanDirectory(
                sourceEngine, sourceStrategy, sourceRootPath, task.getExcludePatterns());
            log.info("扫描完成，发现 {} 个文件", sourceFiles.size());

            // 阶段 2：扫描目标目录并计算差异
            List<FileInfo> destFiles = sameEngine ? sourceFiles : scanDirectory(
                targetEngine, targetStrategy, targetRootPath, null);

            // 构建目标文件相对路径集合（快速查找）
            Set<String> destRelativePaths = new HashSet<>();
            for (FileInfo f : destFiles) {
                destRelativePaths.add(relativePath(targetRootPath, f.path));
            }

            List<FileInfo> toSync;
            if (task.getSyncMode() == SyncTask.SyncMode.NEW_ONLY) {
                toSync = sourceFiles.stream()
                    .filter(f -> !destRelativePaths.contains(relativePath(sourceRootPath, f.path)))
                    .toList();
            } else {
                toSync = new ArrayList<>(sourceFiles);
            }

            // 阶段 2b：FULL 模式 - 删除目标多余文件
            if (task.getSyncMode() == SyncTask.SyncMode.FULL) {
                Set<String> sourceRelativePaths = new HashSet<>();
                sourceFiles.forEach(f -> sourceRelativePaths.add(relativePath(sourceRootPath, f.path)));
                for (FileInfo f : destFiles) {
                    String destRelativePath = relativePath(targetRootPath, f.path);
                    if (!sourceRelativePaths.contains(destRelativePath)) {
                        try {
                            targetStrategy.deleteFile(targetEngine, f.path);
                            log.debug("已删除目标多余文件：{}", f.path);
                        } catch (Exception e) {
                            log.warn("删除目标文件失败：{}，原因：{}", f.path, e.getMessage());
                        }
                    }
                }
            }

            // 阶段 3：执行同步
            execution.setTotalFiles(toSync.size());

            for (FileInfo file : toSync) {
                String fileRelativePath = relativePath(sourceRootPath, file.path);
                try {
                    String sourceFilePath = file.path;
                    String targetFilePath = concatPath(targetRootPath, fileRelativePath);

                    // 检查目标是否已存在（冲突策略）
                    if (destRelativePaths.contains(fileRelativePath)) {
                        switch (task.getConflictStrategy()) {
                            case SKIP -> {
                                log.debug("跳过已存在文件：{}", fileRelativePath);
                                completedCount++;
                                continue;
                            }
                            case RENAME -> targetFilePath = generateUniqueName(
                                targetFilePath, targetRootPath, destRelativePaths);
                            // OVERWRITE：继续执行覆盖
                        }
                    }

                    if (sameEngine) {
                        // 同引擎：直接调用 copyFile 方法
                        log.debug("同引擎复制：{} -> {}", sourceFilePath, targetFilePath);
                        // 确保目标父目录存在
                        String targetDir = getDirPath(targetFilePath);
                        if (!targetDir.isEmpty()) {
                            targetStrategy.createDirectory(targetEngine, targetDir);
                        }
                        targetStrategy.copyFile(targetEngine, sourceFilePath, targetFilePath);
                    } else if (file.size > 100 * 1024 * 1024) { // > 100MB 使用临时文件
                        syncLargeFile(sourceEngine, sourceStrategy, targetEngine, targetStrategy,
                            sourceFilePath, targetFilePath, file);
                    } else {
                        syncSmallFile(sourceEngine, sourceStrategy, targetEngine, targetStrategy,
                            sourceFilePath, targetFilePath, file);
                    }

                    completedCount++;
                    execution.setSuccessFiles(completedCount);
                    if (!failedFiles.isEmpty()) {
                        execution.setFailureDetails(toJson(new ArrayList<>(failedFiles)));
                    }
                    taskExecutionRepository.save(execution);

                    // WebSocket 推送 SYNC_PROGRESS 增量消息
                    wsSessionManager.broadcast("SYNC_PROGRESS", Map.of(
                        "taskId", task.getId(),
                        "executionId", execution.getId(),
                        "status", "RUNNING",
                        "successFiles", completedCount,
                        "failedFiles", failedFiles.size(),
                        "totalFiles", toSync.size(),
                        "progressPercent", toSync.size() > 0 ? (completedCount + failedFiles.size()) * 100 / toSync.size() : 0
                    ));

                    // MOVE 模式：删除源文件
                    if (task.getSyncMode() == SyncTask.SyncMode.MOVE) {
                        try {
                            sourceStrategy.deleteFile(sourceEngine, sourceFilePath);
                        } catch (Exception e) {
                            log.warn("MOVE 模式删除源文件失败，源文件将保留：{}", sourceFilePath);
                        }
                    }

                    log.debug("文件已同步：{} -> {} ({}/{})",
                        sourceFilePath, targetFilePath, completedCount, toSync.size());
                } catch (Exception e) {
                    log.error("同步文件失败：{}，原因：{}", fileRelativePath, e.getMessage(), e);
                    failedFiles.add(fileRelativePath + ": " + e.getMessage());
                    execution.setFailedFiles(execution.getFailedFiles() + 1);
                }
            }

            // 完成
            execution.setEndTime(LocalDateTime.now());
            execution.setStatus(failedFiles.isEmpty()
                ? TaskExecution.ExecutionStatus.SUCCESS
                : TaskExecution.ExecutionStatus.PARTIAL_SUCCESS);
            if (!failedFiles.isEmpty()) {
                execution.setFailureDetails(toJson(failedFiles));
            }
            execution = taskExecutionRepository.save(execution);

            // 更新任务的最后执行时间
            task.setLastExecutedAt(LocalDateTime.now());
            syncTaskRepository.save(task);

            log.info("同步任务执行完毕：{} — {} 成功 / {} 失败",
                task.getName(), execution.getSuccessFiles(), execution.getFailedFiles());

            // WebSocket 推送最终 SYNC_PROGRESS 消息
            wsSessionManager.broadcast("SYNC_PROGRESS", Map.of(
                "taskId", task.getId(),
                "executionId", execution.getId(),
                "status", execution.getStatus().name(),
                "successFiles", execution.getSuccessFiles(),
                "failedFiles", execution.getFailedFiles(),
                "totalFiles", execution.getTotalFiles(),
                "progressPercent", 100
            ));

            // 同步后置转码
            if (task.getTranscodeEnabled() && execution.getStatus() == TaskExecution.ExecutionStatus.SUCCESS) {
                transcodeService.executePostSyncTranscode(task, execution);
            }

        } catch (Exception e) {
            TraceContext.setErrorType(e.getClass().getSimpleName());
            log.error("同步任务执行异常：{} — {}", task.getName(), e.getMessage(), e);
            execution.setEndTime(LocalDateTime.now());
            execution.setStatus(TaskExecution.ExecutionStatus.FAILED);
            execution.setFailureDetails(e.getMessage());
            taskExecutionRepository.save(execution);
        } finally {
            activeExecutions.remove(execution.getId());
        }
    }

    /**
     * 递归扫描目录（深度优先），通过策略模式适配不同引擎
     */
    private List<FileInfo> scanDirectory(StorageEngine engine, StorageEngineStrategy strategy,
                                          String path, String excludePatterns) {
        log.debug("开始扫描目录：引擎={}, path={}", engine.getName(), path);
        List<FileInfo> result = new ArrayList<>();
        List<String> excludePatternList = parseExcludePatterns(excludePatterns);
        scanDirectoryRecursive(engine, strategy, path, result, excludePatternList, 1);
        log.debug("目录扫描完成：path={}, 发现 {} 个文件", path, result.size());
        return result;
    }

    private void scanDirectoryRecursive(StorageEngine engine, StorageEngineStrategy strategy,
                                         String path, List<FileInfo> result,
                                         List<String> excludePatterns, int page) {
        log.debug("递归扫描：引擎={}, path={}, page={}", engine.getName(), path, page);
        var entries = strategy.listFiles(engine, path, page, 100);
        if (entries.isEmpty()) return;

        for (var entry : entries) {
            String name = entry.name();
            if (name == null) continue;
            String fullPath = concatPath(path, name);

            // 应用排除规则
            if (matchesExcludePattern(name, excludePatterns)) {
                log.debug("排除文件：{}", fullPath);
                continue;
            }

            if (entry.isDirectory()) {
                scanDirectoryRecursive(engine, strategy, fullPath, result, excludePatterns, 1);
            } else {
                result.add(new FileInfo(name, fullPath, entry.size()));
            }
        }

        // 分页处理：如果返回数量等于 perPage，可能还有更多
        if (entries.size() == 100) {
            scanDirectoryRecursive(engine, strategy, path, result, excludePatterns, page + 1);
        }
    }

    /** 小文件流式同步（< 100MB） */
    private void syncSmallFile(StorageEngine sourceEngine, StorageEngineStrategy sourceStrategy,
                                StorageEngine targetEngine, StorageEngineStrategy targetStrategy,
                                String sourcePath, String targetPath, FileInfo file) {
        long startTime = System.currentTimeMillis();
        log.debug("小文件同步（流式）：{} -> {}, size={}bytes", sourcePath, targetPath, file.size);
        InputStream inputStream = sourceStrategy.downloadFile(sourceEngine, sourcePath);
        if (inputStream == null) {
            log.error("下载文件失败：源策略返回 null 流，sourcePath={}", sourcePath);
            throw new RuntimeException("下载文件失败：" + sourcePath);
        }
        try (inputStream) {
            targetStrategy.uploadFile(targetEngine, targetPath, inputStream, file.size);
        } catch (Exception e) {
            log.error("上传文件失败：sourcePath={}, targetPath={}, size={}bytes — {}",
                sourcePath, targetPath, file.size, e.getMessage(), e);
            throw new RuntimeException("上传文件失败：" + targetPath, e);
        }
        log.debug("小文件同步完成：{} -> {}, 耗时={}ms", sourcePath, targetPath,
            System.currentTimeMillis() - startTime);
    }

    /** 大文件同步（> 100MB，暂存磁盘） */
    private void syncLargeFile(StorageEngine sourceEngine, StorageEngineStrategy sourceStrategy,
                                StorageEngine targetEngine, StorageEngineStrategy targetStrategy,
                                String sourcePath, String targetPath, FileInfo file) {
        long startTime = System.currentTimeMillis();
        log.info("大文件同步（磁盘）：{} -> {}, size={}MB", sourcePath, targetPath, file.size / 1048576);
        java.nio.file.Path tempFile = null;
        try {
            tempFile = java.nio.file.Files.createTempFile("alist-sync-", ".tmp");
            try (InputStream in = sourceStrategy.downloadFile(sourceEngine, sourcePath)) {
                if (in == null) {
                    log.error("大文件下载失败：源策略返回 null 流，sourcePath={}", sourcePath);
                    throw new RuntimeException("下载文件失败：" + sourcePath);
                }
                java.nio.file.Files.copy(in, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            try (InputStream fileIn = java.nio.file.Files.newInputStream(tempFile)) {
                targetStrategy.uploadFile(targetEngine, targetPath, fileIn, file.size);
            }
            log.info("大文件同步完成：{} -> {}, 耗时={}ms", sourcePath, targetPath,
                System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("大文件同步失败：sourcePath={}, targetPath={}, size={}MB — {}",
                sourcePath, targetPath, file.size / 1048576, e.getMessage(), e);
            throw new RuntimeException("大文件同步失败：" + sourcePath, e);
        } finally {
            if (tempFile != null) {
                try { java.nio.file.Files.deleteIfExists(tempFile); } catch (Exception ignored) {}
            }
        }
    }

    /** 从完整路径中提取父目录路径 */
    private String getDirPath(String filePath) {
        int lastSlash = filePath.lastIndexOf('/');
        return lastSlash > 0 ? filePath.substring(0, lastSlash) : "";
    }

    /** 路径拼接 */
    private String concatPath(String dir, String name) {
        if (dir == null || dir.isEmpty() || dir.equals("/")) return "/" + trimLeadingSlash(name);
        return dir.endsWith("/") ? dir + trimLeadingSlash(name) : dir + "/" + trimLeadingSlash(name);
    }

    /** 计算文件相对根目录的路径 */
    private String relativePath(String rootPath, String filePath) {
        String normalizedRoot = normalizePath(rootPath);
        String normalizedFile = normalizePath(filePath);
        if (normalizedFile.equals(normalizedRoot)) return "";
        String prefix = normalizedRoot.equals("/") ? "/" : normalizedRoot + "/";
        if (normalizedFile.startsWith(prefix)) {
            return normalizedFile.substring(prefix.length());
        }
        return trimLeadingSlash(normalizedFile.substring(normalizedFile.lastIndexOf('/') + 1));
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) return "/";
        String normalized = path.replace('\\', '/');
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private String trimLeadingSlash(String path) {
        if (path == null) return "";
        int index = 0;
        while (index < path.length() && path.charAt(index) == '/') {
            index++;
        }
        return path.substring(index);
    }

    /** 生成不重名的目标路径 */
    private String generateUniqueName(String path, String rootPath, Set<String> existingRelativePaths) {
        int idx = path.lastIndexOf('.');
        String base = idx > 0 ? path.substring(0, idx) : path;
        String ext = idx > 0 ? path.substring(idx) : "";
        int counter = 1;
        String newPath;
        do {
            newPath = base + " (" + counter + ")" + ext;
            counter++;
        } while (existingRelativePaths.contains(relativePath(rootPath, newPath)));
        return newPath;
    }

    /** 解析排除模式（换行分隔） */
    private List<String> parseExcludePatterns(String patterns) {
        if (patterns == null || patterns.isBlank()) return List.of();
        return Stream.of(patterns.split("\n"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }

    /** 检查文件名是否匹配排除模式（支持 glob 通配符） */
    private boolean matchesExcludePattern(String fileName, List<String> patterns) {
        for (String pattern : patterns) {
            if (matchGlob(pattern, fileName)) return true;
        }
        return false;
    }

    /** 简单 Glob 匹配（支持 * 和 ?） */
    private boolean matchGlob(String pattern, String str) {
        String regex = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".");
        return str.matches(regex);
    }

    /** 获取活跃的执行进度 */
    public TaskExecution getProgress(Long executionId) {
        return activeExecutions.getOrDefault(executionId,
            taskExecutionRepository.findById(executionId).orElse(null));
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JacksonException e) {
            return obj.toString();
        }
    }

    /** 文件信息记录 */
    private record FileInfo(String name, String path, long size) {}
}
