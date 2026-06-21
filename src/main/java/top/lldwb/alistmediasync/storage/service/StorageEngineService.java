package top.lldwb.alistmediasync.storage.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.lldwb.alistmediasync.common.config.AppProperties;
import top.lldwb.alistmediasync.storage.dto.storage.StorageEngineCreateDTO;
import top.lldwb.alistmediasync.storage.dto.storage.StorageEngineUpdateDTO;
import top.lldwb.alistmediasync.storage.dto.storage.StorageEngineVO;
import top.lldwb.alistmediasync.storage.entity.StorageEngine;
import top.lldwb.alistmediasync.storage.entity.StorageEngine.EngineType;
import top.lldwb.alistmediasync.storage.repository.StorageEngineRepository;
import top.lldwb.alistmediasync.storage.service.engine.StorageEngineStrategy;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 存储引擎管理服务
 * <p>
 * 提供存储引擎的 CRUD 操作和连接测试功能，
 * 通过策略模式按 engineType 分发文件操作到对应的 StorageEngineStrategy 实现。
 * Token 通过 AES-256-GCM 自动加密存储。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Slf4j
@Service
public class StorageEngineService {

    private final StorageEngineRepository repository;
    private final Map<String, StorageEngineStrategy> strategyMap;
    private final AppProperties appProperties;

    /**
     * 构造器注入：Spring 自动收集所有 StorageEngineStrategy 实现，
     * 按 type() 方法构建分发 Map。
     */
    public StorageEngineService(StorageEngineRepository repository,
                                 List<StorageEngineStrategy> strategies,
                                 AppProperties appProperties) {
        this.repository = repository;
        this.appProperties = appProperties;
        this.strategyMap = strategies.stream()
            .collect(Collectors.toMap(StorageEngineStrategy::type, Function.identity()));
        log.info("已加载 {} 个存储引擎策略：{}", strategies.size(),
            strategies.stream().map(StorageEngineStrategy::type).toList());
    }

    /**
     * 按引擎类型获取对应策略
     *
     * @param engine 存储引擎实体
     * @return 对应的策略实现
     * @throws IllegalArgumentException 如果引擎类型不支持
     */
    public StorageEngineStrategy resolve(StorageEngine engine) {
        StorageEngineStrategy strategy = strategyMap.get(engine.getEngineType().name());
        if (strategy == null) {
            throw new IllegalArgumentException("不支持的引擎类型：" + engine.getEngineType());
        }
        return strategy;
    }

    /**
     * 创建存储引擎
     */
    @Transactional
    public StorageEngineVO create(StorageEngineCreateDTO dto) {
        // 校验 engineType
        EngineType engineType = parseEngineType(dto.getEngineType());

        // 类型校验：ALIST 需要 baseUrl+token，LOCAL 需要 localPath
        validateFields(dto, engineType);

        StorageEngine entity = new StorageEngine();
        entity.setName(dto.getName());
        entity.setEngineType(engineType);
        if (dto.getBaseUrl() != null) {
            entity.setBaseUrl(dto.getBaseUrl().replaceAll("/$", "")); // 去掉末尾斜杠
        }
        entity.setEncryptedToken(dto.getToken()); // CryptoConverter 自动加密
        entity.setLocalPath(dto.getLocalPath());
        entity.setStatus(StorageEngine.EngineStatus.OFFLINE);
        entity = repository.save(entity);
        log.info("存储引擎已创建：{} (类型: {}, URL/路径: {})",
            entity.getName(), entity.getEngineType(),
            engineType == EngineType.ALIST ? entity.getBaseUrl() : entity.getLocalPath());
        return StorageEngineVO.from(entity);
    }

    /**
     * 更新存储引擎
     */
    @Transactional
    public StorageEngineVO update(Long id, StorageEngineUpdateDTO dto) {
        StorageEngine entity = repository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("存储引擎不存在：id=" + id));

        if (dto.getName() != null) entity.setName(dto.getName());
        if (dto.getBaseUrl() != null) entity.setBaseUrl(dto.getBaseUrl().replaceAll("/$", ""));
        if (dto.getToken() != null) entity.setEncryptedToken(dto.getToken());
        if (dto.getLocalPath() != null) entity.setLocalPath(dto.getLocalPath());

        entity = repository.save(entity);
        log.info("存储引擎已更新：{}", entity.getName());
        return StorageEngineVO.from(entity);
    }

    /**
     * 删除存储引擎
     */
    @Transactional
    public void delete(Long id) {
        StorageEngine entity = repository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("存储引擎不存在：id=" + id));
        repository.delete(entity);
        log.info("存储引擎已删除：{}", entity.getName());
    }

    /**
     * 根据 ID 查询
     */
    public StorageEngineVO getById(Long id) {
        return repository.findById(id)
            .map(StorageEngineVO::from)
            .orElseThrow(() -> new NoSuchElementException("存储引擎不存在：id=" + id));
    }

    /**
     * 查询所有存储引擎
     */
    public List<StorageEngineVO> listAll() {
        return repository.findAll().stream()
            .map(StorageEngineVO::from)
            .toList();
    }

    /**
     * 测试连接：按 engineType 分发测试逻辑
     */
    @Transactional
    public boolean testConnection(Long id) {
        StorageEngine entity = repository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("存储引擎不存在：id=" + id));
        log.info("正在测试存储引擎连接：{} (类型: {})", entity.getName(), entity.getEngineType());
        StorageEngineStrategy strategy = resolve(entity);
        boolean success = strategy.testConnection(entity);
        entity.setStatus(success ? StorageEngine.EngineStatus.ONLINE : StorageEngine.EngineStatus.ERROR);
        repository.save(entity);
        return success;
    }

    /**
     * 直接获取实体（内部使用，非脱敏）
     */
    public StorageEngine getEntity(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("存储引擎不存在：id=" + id));
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 解析 engineType 字符串为枚举
     */
    private EngineType parseEngineType(String engineTypeStr) {
        try {
            return EngineType.valueOf(engineTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("不支持的引擎类型：" + engineTypeStr + "，支持的类型：ALIST、LOCAL");
        }
    }

    /**
     * 按引擎类型校验必填字段
     */
    private void validateFields(StorageEngineCreateDTO dto, EngineType engineType) {
        switch (engineType) {
            case ALIST -> {
                if (dto.getBaseUrl() == null || dto.getBaseUrl().isBlank()) {
                    throw new IllegalArgumentException("ALIST 类型存储引擎必须提供服务器地址");
                }
                if (dto.getToken() == null || dto.getToken().isBlank()) {
                    throw new IllegalArgumentException("ALIST 类型存储引擎必须提供 API 令牌");
                }
            }
            case LOCAL -> {
                if (dto.getLocalPath() == null || dto.getLocalPath().isBlank()) {
                    throw new IllegalArgumentException("本地路径类型存储引擎必须提供路径");
                }
                java.nio.file.Path path = java.nio.file.Path.of(dto.getLocalPath());
                if (!java.nio.file.Files.exists(path)) {
                    throw new IllegalArgumentException("本地路径不存在：" + dto.getLocalPath());
                }
                if (!java.nio.file.Files.isDirectory(path)) {
                    throw new IllegalArgumentException("本地路径不是目录：" + dto.getLocalPath());
                }
            }
        }
    }

    /**
     * 定时健康检查存储引擎状态
     * <p>
     * 每隔 {@code app.storage.health-check-interval} 秒对所有存储引擎执行连接测试，
     * 自动更新 EngineStatus（ONLINE/OFFLINE/ERROR）。
     * </p>
     */
    @Scheduled(fixedRateString = "#{T(java.util.concurrent.TimeUnit).SECONDS.toMillis("
        + "@appProperties.storage.healthCheckInterval)}")
    public void healthCheck() {
        List<StorageEngine> engines = repository.findAll();
        if (engines.isEmpty()) {
            return;
        }
        log.debug("开始存储引擎定时健康检查，共 {} 个引擎", engines.size());
        for (StorageEngine engine : engines) {
            try {
                StorageEngineStrategy strategy = strategyMap.get(engine.getEngineType().name());
                if (strategy == null) {
                    log.warn("未找到引擎类型对应的策略：{}", engine.getEngineType());
                    continue;
                }
                boolean online = strategy.testConnection(engine);
                StorageEngine.EngineStatus newStatus = online
                    ? StorageEngine.EngineStatus.ONLINE
                    : StorageEngine.EngineStatus.OFFLINE;
                if (engine.getEngineStatus() != newStatus) {
                    log.info("存储引擎状态变更：{} {} -> {}", engine.getName(),
                        engine.getEngineStatus(), newStatus);
                    engine.setEngineStatus(newStatus);
                    repository.save(engine);
                }
            } catch (Exception e) {
                log.warn("存储引擎健康检查异常：{} — {}", engine.getName(), e.getMessage());
                if (engine.getEngineStatus() != StorageEngine.EngineStatus.ERROR) {
                    engine.setEngineStatus(StorageEngine.EngineStatus.ERROR);
                    repository.save(engine);
                }
            }
        }
        log.debug("存储引擎健康检查完成");
    }
}
