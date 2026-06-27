package top.lldwb.alistmediasync.storage.service.engine;

import top.lldwb.alistmediasync.sync.dto.sync.DirectoryEntryVO;
import top.lldwb.alistmediasync.sync.dto.sync.FileEntry;
import top.lldwb.alistmediasync.storage.entity.StorageEngine;

import java.io.InputStream;
import java.util.List;

/**
 * 存储引擎策略接口
 * <p>
 * 定义统一的文件操作抽象，由不同存储后端（AList 远程 / 本地文件系统）实现。
 * Spring 通过构造器注入 List&lt;StorageEngineStrategy&gt; 自动收集所有实现，
 * StorageEngineService 按 engineType 选择策略。
 * </p>
 *
 * @author AList-Media-Sync
 */
public interface StorageEngineStrategy {

    /**
     * 策略类型标识，与 StorageEngine.EngineType 枚举值对应
     *
     * @return "ALIST" 或 "LOCAL"
     */
    String type();

    /**
     * 列出指定路径下的文件和目录
     *
     * @param engine  存储引擎实体（含连接信息）
     * @param path    目录路径
     * @param page    页码（从 1 开始）
     * @param perPage 每页数量
     * @return 文件/目录列表
     */
    List<FileEntry> listFiles(StorageEngine engine, String path, int page, int perPage);

    /**
     * 获取单个文件/目录信息
     *
     * @param engine 存储引擎实体
     * @param path   文件路径
     * @return 文件信息
     */
    FileEntry getFileInfo(StorageEngine engine, String path);

    /**
     * 下载文件，返回输入流（调用方负责关闭）
     *
     * @param engine 存储引擎实体
     * @param path   文件路径
     * @return 文件内容输入流
     */
    InputStream downloadFile(StorageEngine engine, String path);

    /**
     * 上传文件
     *
     * @param engine      存储引擎实体
     * @param remotePath  目标路径
     * @param inputStream 文件内容流
     * @param fileSize    文件大小（字节）
     */
    void uploadFile(StorageEngine engine, String remotePath, InputStream inputStream, long fileSize);

    /**
     * 创建目录
     *
     * @param engine 存储引擎实体
     * @param path   目录路径
     */
    void createDirectory(StorageEngine engine, String path);

    /**
     * 删除文件或目录
     *
     * @param engine 存储引擎实体
     * @param path   路径
     */
    void deleteFile(StorageEngine engine, String path);

    /**
     * 列出指定路径下的子目录（仅目录，不包含文件）
     *
     * @param engine 存储引擎实体
     * @param path   目录路径
     * @return 子目录列表
     */
    List<DirectoryEntryVO> listDirectories(StorageEngine engine, String path);

    /**
     * 列出指定路径下的全部条目（文件 + 子目录），供"文件选择"场景使用
     * <p>
     * 与 {@link #listDirectories} 的区别：
     * <ul>
     *   <li>{@code listDirectories}：仅返回目录，用于"目录选择器"（如同步任务的源/目标目录）</li>
     *   <li>{@code listEntries}：返回目录 + 文件，用于"文件选择器"（如转码任务的源/目标文件）</li>
     * </ul>
     * 默认实现基于 {@link #listFiles} 聚合分页结果，子类可按需重写以做性能优化。
     * </p>
     *
     * @param engine 存储引擎实体
     * @param path   目录路径
     * @return 文件 + 子目录列表，目录在前、文件在后，名称升序
     */
    default List<FileEntry> listEntries(StorageEngine engine, String path) {
        // 默认走单页 + 大 perPage 实现；AList 策略覆盖以走分页接口
        return listFiles(engine, path, 1, Integer.MAX_VALUE);
    }

    /**
     * 测试连接
     *
     * @param engine 存储引擎实体
     * @return true 表示连接成功
     */
    boolean testConnection(StorageEngine engine);

    /**
     * 在同存储引擎内复制文件
     * <p>
     * 默认实现抛出 UnsupportedOperationException，由各策略实现覆盖。
     * </p>
     *
     * @param engine     存储引擎实体
     * @param sourcePath 源文件路径
     * @param targetPath 目标文件路径
     * @throws UnsupportedOperationException 如果策略不支持直接复制
     */
    default void copyFile(StorageEngine engine, String sourcePath, String targetPath) {
        throw new UnsupportedOperationException(
            "copyFile 不被当前策略支持：" + type());
    }

    /**
     * 在同存储引擎内移动文件（重命名 / 跨目录移动）
     * <p>
     * 与 {@link #copyFile} + {@link #deleteFile} 的组合相比，原生 move 通常更高效，
     * 且对某些底层存储（如 Synology DSM 经 AList 暴露的本地驱动）可绕过
     * copy 操作后置的 {@code chown} 权限问题。
     * </p>
     * <p>
     * 默认实现抛出 UnsupportedOperationException，由各策略实现覆盖。
     * </p>
     *
     * @param engine     存储引擎实体
     * @param sourcePath 源文件路径
     * @param targetPath 目标文件路径
     * @throws UnsupportedOperationException 如果策略不支持直接移动
     */
    default void moveFile(StorageEngine engine, String sourcePath, String targetPath) {
        throw new UnsupportedOperationException(
            "moveFile 不被当前策略支持：" + type());
    }
}
