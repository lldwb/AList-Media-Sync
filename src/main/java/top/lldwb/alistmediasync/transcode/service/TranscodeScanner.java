package top.lldwb.alistmediasync.transcode.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.lldwb.alistmediasync.common.enums.ConflictStrategy;
import top.lldwb.alistmediasync.common.util.MagicBytesDetector;
import top.lldwb.alistmediasync.common.util.PathUtils;
import top.lldwb.alistmediasync.storage.dto.FileEntry;
import top.lldwb.alistmediasync.storage.entity.StorageEngine;
import top.lldwb.alistmediasync.storage.service.StorageEngineService;
import top.lldwb.alistmediasync.storage.service.engine.StorageEngineStrategy;

import java.util.ArrayList;
import java.util.List;

/**
 * 转码源目录扫描器（自 {@code TranscodeService} 按职责切出）
 * <p>
 * 负责把「源路径」解析为待转码候选文件列表，是三步流程之前的前置阶段：
 * </p>
 * <ul>
 *   <li>判断源路径是目录还是单个文件；</li>
 *   <li>目录模式下递归扫描（只收集文件、不收集目录，深度上限 10）；</li>
 *   <li>按扩展名做魔数检测过滤，非视频文件跳过；</li>
 *   <li>按冲突策略跳过目标已存在的文件。</li>
 * </ul>
 * <p>
 * 本类只做扫描：不执行转码、不修改任何任务状态、不持久化。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TranscodeScanner {

    private final StorageEngineService storageEngineService;

    /**
     * 判断源路径是否为目录
     *
     * @param sourceEngine 源存储引擎（为 {@code null} 时按文件处理）
     * @param path         待判断的源路径
     * @return 目录返回 {@code true}；判断失败时按文件处理并返回 {@code false}
     */
    boolean isDirectory(StorageEngine sourceEngine, String path) {
        if (sourceEngine == null) return false;
        try {
            StorageEngineStrategy strategy = storageEngineService.resolve(sourceEngine);
            FileEntry info = strategy.getFileInfo(sourceEngine, path);
            return info != null && info.isDirectory();
        } catch (Exception e) {
            log.warn("无法判断路径类型，默认为文件：{} — {}", path, e.getMessage());
            return false;
        }
    }

    /**
     * 扫描源目录（递归，只收集文件不收集目录）
     *
     * @param sourceEngine     源存储引擎
     * @param sourceStrategy   源存储引擎策略
     * @param targetEngine     目标存储引擎
     * @param targetStrategy   目标存储引擎策略
     * @param sourcePath       源目录路径
     * @param targetPath       目标目录路径
     * @param conflictStrategy 目标已存在时的冲突策略
     * @return 待转码候选文件列表，无候选时为空列表
     */
    List<TranscodeCandidate> scanSourceDirectory(
        StorageEngine sourceEngine, StorageEngineStrategy sourceStrategy,
        StorageEngine targetEngine, StorageEngineStrategy targetStrategy,
        String sourcePath, String targetPath, ConflictStrategy conflictStrategy) {

        log.debug("扫描源目录：sourcePath={}", sourcePath);
        List<TranscodeCandidate> candidates = new ArrayList<>();
        scanDirectoryRecursive(sourceEngine, sourceStrategy, targetEngine, targetStrategy,
            sourcePath, targetPath, conflictStrategy, candidates, 1, 10);
        log.debug("源目录扫描完成：sourcePath={}, 发现 {} 个候选文件", sourcePath, candidates.size());
        return candidates;
    }

    /**
     * 递归扫描目录并收集候选文件
     *
     * @param depth    当前递归深度（从 1 开始）
     * @param maxDepth 递归深度上限
     */
    private void scanDirectoryRecursive(
        StorageEngine sourceEngine, StorageEngineStrategy sourceStrategy,
        StorageEngine targetEngine, StorageEngineStrategy targetStrategy,
        String sourceDir, String targetDir, ConflictStrategy conflictStrategy,
        List<TranscodeCandidate> candidates, int depth, int maxDepth) {

        log.debug("递归扫描转码目录：sourceDir={}, depth={}", sourceDir, depth);

        if (depth > maxDepth) {
            log.warn("扫描深度已达上限 {}，停止递归：{}", maxDepth, sourceDir);
            return;
        }

        List<FileEntry> entries = sourceStrategy.listFiles(sourceEngine, sourceDir, 1, Integer.MAX_VALUE);
        for (FileEntry entry : entries) {
            String name = entry.name();
            String fullPath = PathUtils.join(sourceDir, name);

            if (entry.isDirectory()) {
                scanDirectoryRecursive(sourceEngine, sourceStrategy, targetEngine, targetStrategy,
                    fullPath, PathUtils.join(targetDir, name), conflictStrategy,
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
                PathUtils.join(targetDir, PathUtils.swapExtension(name, "mp3")));
            if (targetExists && conflictStrategy == ConflictStrategy.SKIP) {
                log.debug("目标文件已存在，跳过：{}", name);
                continue;
            }

            candidates.add(new TranscodeCandidate(
                name, fullPath, PathUtils.join(targetDir, name), format, entry.size(), sourceEngine));
        }
    }

    /**
     * 检查目标路径是否已存在
     *
     * @return 目标存在返回 {@code true}；查询异常时返回 {@code false}
     */
    private boolean checkTargetExists(StorageEngine engine, StorageEngineStrategy strategy, String path) {
        try {
            FileEntry info = strategy.getFileInfo(engine, path);
            return info != null;
        } catch (Exception e) {
            return false;
        }
    }
}
