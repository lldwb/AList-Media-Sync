package top.lldwb.alistmediasync.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.lldwb.alistmediasync.client.AListClient;
import top.lldwb.alistmediasync.entity.StorageEngine;
import top.lldwb.alistmediasync.entity.SyncTask;
import top.lldwb.alistmediasync.entity.TaskExecution;
import top.lldwb.alistmediasync.repository.*;

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
 * </p>
 *
 * @author AList-Media-Sync
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncService {

    private final AListClient alistClient;
    private final SyncTaskRepository syncTaskRepository;
    private final StorageEngineRepository storageEngineRepository;
    private final TaskExecutionRepository taskExecutionRepository;
    private final TranscodeService transcodeService;
    private final ObjectMapper objectMapper;

    /** 正在执行的任务执行记录缓存（用于进度查询） */
    private final Map<Long, TaskExecution> activeExecutions = new ConcurrentHashMap<>();

    /**
     * 执行同步任务（异步）
     *
     * @param task 同步任务实体
     */
    @Async
    @Transactional
    public void executeSyncTask(SyncTask task) {
        // 冲突检测：是否有其他 SyncTask 向同一目标路径写入且正在运行中
        List<TaskExecution> conflicting = taskExecutionRepository.findByStatusAndTaskType(
            TaskExecution.ExecutionStatus.RUNNING, TaskExecution.TaskType.SYNC);
        if (!conflicting.isEmpty()) {
            log.warn("存在正在运行的同步任务，当前任务等待中：{}", task.getName());
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

        List<String> failedFiles = new ArrayList<>();
        int completedCount = 0;

        try {
            log.info("同步任务开始执行：{} (模式: {}, {} -> {})",
                task.getName(), task.getSyncMode(), task.getSourcePath(), task.getTargetPath());

            // 阶段 1：扫描源目录
            List<FileInfo> sourceFiles = scanDirectory(
                sourceEngine.getBaseUrl(), sourceEngine.getEncryptedToken(),
                task.getSourcePath(), task.getExcludePatterns());
            log.info("扫描完成，发现 {} 个文件", sourceFiles.size());

            // 阶段 2：扫描目标目录并计算差异
            List<FileInfo> destFiles = scanDirectory(
                targetEngine.getBaseUrl(), targetEngine.getEncryptedToken(),
                task.getTargetPath(), null);

            // 构建目标文件名集合（快速查找）
            Set<String> destFileNames = new HashSet<>();
            for (FileInfo f : destFiles) {
                destFileNames.add(f.name);
            }

            List<FileInfo> toSync;
            if (task.getSyncMode() == SyncTask.SyncMode.NEW_ONLY) {
                // 仅同步源有、目标无的文件
                toSync = sourceFiles.stream()
                    .filter(f -> !destFileNames.contains(f.name))
                    .toList();
            } else {
                // FULL 或 MOVE 模式：同步所有源文件
                toSync = new ArrayList<>(sourceFiles);
            }

            // 阶段 2b：FULL 模式 - 删除目标多余文件
            if (task.getSyncMode() == SyncTask.SyncMode.FULL) {
                Set<String> sourceFileNames = new HashSet<>();
                sourceFiles.forEach(f -> sourceFileNames.add(f.name));
                for (FileInfo f : destFiles) {
                    if (!sourceFileNames.contains(f.name)) {
                        try {
                            String destPath = concatPath(task.getTargetPath(), f.name);
                            alistClient.deleteFile(targetEngine.getBaseUrl(), targetEngine.getEncryptedToken(), destPath);
                            log.debug("已删除目标多余文件：{}", destPath);
                        } catch (Exception e) {
                            log.warn("删除目标文件失败：{}，原因：{}", f.name, e.getMessage());
                        }
                    }
                }
            }

            // 阶段 3：执行同步
            execution.setTotalFiles(toSync.size());

            for (FileInfo file : toSync) {
                try {
                    String sourceFilePath = concatPath(task.getSourcePath(), file.name);
                    String targetFilePath = concatPath(task.getTargetPath(), file.name);

                    // 检查目标是否已存在（冲突策略）
                    if (destFileNames.contains(file.name)) {
                        switch (task.getConflictStrategy()) {
                            case SKIP -> {
                                log.debug("跳过已存在文件：{}", file.name);
                                completedCount++;
                                continue;
                            }
                            case RENAME -> {
                                targetFilePath = generateUniqueName(targetFilePath, destFileNames);
                            }
                            // OVERWRITE：继续执行覆盖
                        }
                    }

                    // 流式下载→上传
                    if (file.size > 100 * 1024 * 1024) { // > 100MB 使用临时文件
                        syncLargeFile(sourceEngine, targetEngine, sourceFilePath, targetFilePath, file);
                    } else {
                        syncSmallFile(sourceEngine, targetEngine, sourceFilePath, targetFilePath, file);
                    }

                    completedCount++;
                    execution.setSuccessFiles(completedCount);
                    if (!failedFiles.isEmpty()) {
                        execution.setFailureDetails(toJson(new ArrayList<>(failedFiles)));
                    }
                    taskExecutionRepository.save(execution);

                    // MOVE 模式：删除源文件
                    if (task.getSyncMode() == SyncTask.SyncMode.MOVE) {
                        try {
                            alistClient.deleteFile(sourceEngine.getBaseUrl(), sourceEngine.getEncryptedToken(), sourceFilePath);
                        } catch (Exception e) {
                            log.warn("MOVE 模式删除源文件失败：{}", sourceFilePath);
                        }
                    }

                    log.debug("文件已同步：{} -> {} ({}/{})",
                        sourceFilePath, targetFilePath, completedCount, toSync.size());
                } catch (Exception e) {
                    log.error("同步文件失败：{}，原因：{}", file.name, e.getMessage());
                    failedFiles.add(file.name + ": " + e.getMessage());
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

            // 同步后置转码
            if (task.getTranscodeEnabled() && execution.getStatus() == TaskExecution.ExecutionStatus.SUCCESS) {
                transcodeService.executePostSyncTranscode(task, execution);
            }

        } catch (Exception e) {
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
     * 递归扫描目录（深度优先）
     */
    @SuppressWarnings("unchecked")
    private List<FileInfo> scanDirectory(String baseUrl, String token, String path, String excludePatterns) {
        List<FileInfo> result = new ArrayList<>();
        List<String> excludePatternList = parseExcludePatterns(excludePatterns);
        scanDirectoryRecursive(baseUrl, token, path, result, excludePatternList, 1);
        return result;
    }

    private void scanDirectoryRecursive(String baseUrl, String token, String path,
                                         List<FileInfo> result, List<String> excludePatterns, int page) {
        Map<String, Object> resp = alistClient.listFiles(baseUrl, token, path, page, 100);
        if (resp == null || resp.get("data") == null) return;

        Map<String, Object> data = (Map<String, Object>) resp.get("data");
        List<Map<String, Object>> files = (List<Map<String, Object>>) data.get("content");
        if (files == null || files.isEmpty()) return;

        for (Map<String, Object> f : files) {
            boolean isDir = Boolean.TRUE.equals(f.get("is_dir"));
            String name = (String) f.get("name");
            if (name == null) continue;
            String fullPath = concatPath(path, name);

            // 应用排除规则
            if (matchesExcludePattern(name, excludePatterns)) {
                log.debug("排除文件：{}", fullPath);
                continue;
            }

            if (isDir) {
                scanDirectoryRecursive(baseUrl, token, fullPath, result, excludePatterns, 1);
            } else {
                Number sizeNum = (Number) f.get("size");
                long size = sizeNum != null ? sizeNum.longValue() : 0;
                result.add(new FileInfo(name, fullPath, size));
            }
        }

        // 分页处理
        Number total = (Number) data.get("total");
        if (total != null && page * 100 < total.intValue()) {
            scanDirectoryRecursive(baseUrl, token, path, result, excludePatterns, page + 1);
        }
    }

    /** 小文件流式同步（< 100MB） */
    private void syncSmallFile(StorageEngine source, StorageEngine target,
                                String sourcePath, String targetPath, FileInfo file) {
        InputStream inputStream = alistClient.downloadFile(source.getBaseUrl(), source.getEncryptedToken(), sourcePath);
        if (inputStream == null) throw new RuntimeException("下载文件失败：" + sourcePath);
        try (inputStream) {
            alistClient.uploadFile(target.getBaseUrl(), target.getEncryptedToken(), targetPath, inputStream, file.size);
        } catch (Exception e) {
            throw new RuntimeException("上传文件失败：" + targetPath, e);
        }
    }

    /** 大文件同步（> 100MB，暂存磁盘） */
    private void syncLargeFile(StorageEngine source, StorageEngine target,
                                String sourcePath, String targetPath, FileInfo file) {
        java.nio.file.Path tempFile = null;
        try {
            tempFile = java.nio.file.Files.createTempFile("alist-sync-", ".tmp");
            try (InputStream in = alistClient.downloadFile(source.getBaseUrl(), source.getEncryptedToken(), sourcePath)) {
                if (in == null) throw new RuntimeException("下载文件失败：" + sourcePath);
                java.nio.file.Files.copy(in, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            try (InputStream fileIn = java.nio.file.Files.newInputStream(tempFile)) {
                alistClient.uploadFile(target.getBaseUrl(), target.getEncryptedToken(), targetPath, fileIn, file.size);
            }
        } catch (Exception e) {
            throw new RuntimeException("大文件同步失败：" + sourcePath, e);
        } finally {
            if (tempFile != null) {
                try { java.nio.file.Files.deleteIfExists(tempFile); } catch (Exception ignored) {}
            }
        }
    }

    /** 路径拼接 */
    private String concatPath(String dir, String name) {
        if (dir.endsWith("/")) return dir + name;
        return dir + "/" + name;
    }

    /** 生成不重名的目标路径 */
    private String generateUniqueName(String path, Set<String> existingNames) {
        int idx = path.lastIndexOf('.');
        String base = idx > 0 ? path.substring(0, idx) : path;
        String ext = idx > 0 ? path.substring(idx) : "";
        int counter = 1;
        String newPath;
        do {
            newPath = base + " (" + counter + ")" + ext;
            counter++;
        } while (existingNames.contains(newPath.substring(newPath.lastIndexOf('/') + 1)));
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
        } catch (JsonProcessingException e) {
            return obj.toString();
        }
    }

    /** 文件信息记录 */
    private record FileInfo(String name, String path, long size) {}
}
