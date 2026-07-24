package top.lldwb.alistmediasync.integration.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import jakarta.persistence.OptimisticLockException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import top.lldwb.alistmediasync.storage.entity.StorageEngine;
import top.lldwb.alistmediasync.storage.repository.StorageEngineRepository;

import jakarta.persistence.EntityManager;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 存储引擎 Repository 集成测试
 * <p>
 * 验证持久化、@Version 乐观锁、CRUD 操作。
 * 使用 {@link DataJpaTest} 自动配置 H2 内存数据库，测试事务默认回滚。
 * 乐观锁测试需显式 flush 触发 {@link OptimisticLockException}。
 * </p>
 *
 * @author AList-Media-Sync
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("StorageEngineRepository 集成测试")
class StorageEngineRepositoryIT {

    @Autowired
    private StorageEngineRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private StorageEngine engine;

    @BeforeEach
    void setUp() {
        engine = new StorageEngine();
        engine.setName("测试引擎");
        engine.setEngineType(StorageEngine.EngineType.ALIST);
        engine.setBaseUrl("https://alist.example.com");
        engine.setEncryptedToken("test-token-encrypted");
        engine.setStatus(StorageEngine.EngineStatus.OFFLINE);
    }

    @Test
    @DisplayName("保存引擎并验证自动填充 createdAt/updatedAt/version")
    void shouldPersistAndAutoFillTimestamps() {
        StorageEngine saved = repository.save(engine);

        assertNotNull(saved.getId(), "ID 应自动生成");
        assertNotNull(saved.getCreatedAt(), "createdAt 应自动填充");
        assertNotNull(saved.getUpdatedAt(), "updatedAt 应自动填充");
        assertEquals(0L, saved.getVersion(), "初始 version 应为 0");
    }

    @Test
    @DisplayName("按 ID 查询引擎")
    void shouldFindById() {
        StorageEngine saved = repository.save(engine);
        StorageEngine found = repository.findById(saved.getId()).orElse(null);

        assertNotNull(found);
        assertEquals("测试引擎", found.getName());
        assertEquals(StorageEngine.EngineType.ALIST, found.getEngineType());
        assertEquals("https://alist.example.com", found.getBaseUrl());
    }

    @Test
    @DisplayName("更新引擎字段")
    void shouldUpdateEngine() {
        StorageEngine saved = repository.save(engine);
        saved.setName("更新后的引擎");
        saved.setStatus(StorageEngine.EngineStatus.ONLINE);
        repository.save(saved);

        StorageEngine updated = repository.findById(saved.getId()).orElse(null);
        assertNotNull(updated);
        assertEquals("更新后的引擎", updated.getName());
        assertEquals(StorageEngine.EngineStatus.ONLINE, updated.getStatus());
    }

    @Test
    @DisplayName("@Version 乐观锁冲突检测")
    void shouldDetectOptimisticLockConflict() {
        StorageEngine saved = repository.save(engine);
        entityManager.flush();
        Long id = saved.getId();

        // 加载托管实体，Hibernate 记录 loaded state 中 version=0
        StorageEngine e2 = repository.findById(id).orElseThrow();

        // 用原生 JDBC 直接递增数据库 version，模拟另一事务已提交
        // jdbcTemplate 绕过 Hibernate 持久化上下文，e2 的 loaded state 仍为旧值 0
        jdbcTemplate.update("UPDATE storage_engine SET version = version + 1 WHERE id = ?", id);

        // 修改 e2 字段使其成为 dirty，flush 时触发版本检查
        e2.setName("线程2更新");

        // flush 时 Hibernate 用 loaded state version=0 做 WHERE 条件
        // 数据库 version 已被 JDBC 改为 1，不匹配，抛出 OptimisticLockException
        assertThrows(OptimisticLockException.class, () -> {
            repository.save(e2);
            entityManager.flush();
        }, "version 冲突时应抛出 OptimisticLockException");
    }

    @Test
    @DisplayName("删除引擎")
    void shouldDeleteEngine() {
        StorageEngine saved = repository.save(engine);
        Long id = saved.getId();

        repository.deleteById(id);
        assertFalse(repository.findById(id).isPresent(), "删除后不应存在");
    }

    @Test
    @DisplayName("查询所有引擎")
    void shouldFindAll() {
        repository.save(engine);

        StorageEngine engine2 = new StorageEngine();
        engine2.setName("第二个引擎");
        engine2.setEngineType(StorageEngine.EngineType.LOCAL);
        engine2.setLocalPath("/data/media");
        engine2.setStatus(StorageEngine.EngineStatus.ONLINE);
        repository.save(engine2);

        assertEquals(2, repository.findAll().size());
    }

    @Test
    @DisplayName("EngineStatus 枚举持久化")
    void shouldPersistEngineStatusEnum() {
        engine.setStatus(StorageEngine.EngineStatus.ERROR);
        StorageEngine saved = repository.save(engine);

        assertEquals(StorageEngine.EngineStatus.ERROR, saved.getStatus());
    }
}