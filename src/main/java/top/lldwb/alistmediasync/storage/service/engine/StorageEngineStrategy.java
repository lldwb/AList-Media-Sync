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
     * 测试连接
     *
     * @param engine 存储引擎实体
     * @return true 表示连接成功
     */
    boolean testConnection(StorageEngine engine);
}
