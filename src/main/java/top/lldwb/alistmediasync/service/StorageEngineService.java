package top.lldwb.alistmediasync.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.lldwb.alistmediasync.client.AListClient;
import top.lldwb.alistmediasync.dto.storage.StorageEngineCreateDTO;
import top.lldwb.alistmediasync.dto.storage.StorageEngineUpdateDTO;
import top.lldwb.alistmediasync.dto.storage.StorageEngineVO;
import top.lldwb.alistmediasync.entity.StorageEngine;
import top.lldwb.alistmediasync.repository.StorageEngineRepository;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * 存储引擎管理服务
 * <p>
 * 提供存储引擎的 CRUD 操作和连接测试功能。
 * Token 通过 AES-256-GCM 自动加密存储。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageEngineService {

    private final StorageEngineRepository repository;
    private final AListClient alistClient;

    /**
     * 创建存储引擎
     */
    @Transactional
    public StorageEngineVO create(StorageEngineCreateDTO dto) {
        StorageEngine entity = new StorageEngine();
        entity.setName(dto.getName());
        entity.setBaseUrl(dto.getBaseUrl().replaceAll("/$", "")); // 去掉末尾斜杠
        entity.setUsername(dto.getUsername());
        entity.setEncryptedToken(dto.getToken()); // CryptoConverter 自动加密
        entity.setStatus(StorageEngine.EngineStatus.OFFLINE);
        entity = repository.save(entity);
        log.info("存储引擎已创建：{} (URL: {})", entity.getName(), entity.getBaseUrl());
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
        if (dto.getUsername() != null) entity.setUsername(dto.getUsername());
        if (dto.getToken() != null) entity.setEncryptedToken(dto.getToken());

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
     * 测试连接：向 AList /api/me 发送请求验证 Token 有效性
     */
    public boolean testConnection(Long id) {
        StorageEngine entity = repository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("存储引擎不存在：id=" + id));
        log.info("正在测试存储引擎连接：{} -> {}", entity.getName(), entity.getBaseUrl());
        boolean success = alistClient.testConnection(entity.getBaseUrl(), entity.getEncryptedToken());
        entity.setStatus(success ? StorageEngine.EngineStatus.ONLINE : StorageEngine.EngineStatus.ERROR);
        repository.save(entity);
        return success;
    }

    /**
     * 直接获取实体（内部使用，非脱敏）
     */
    StorageEngine getEntity(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("存储引擎不存在：id=" + id));
    }
}
