package top.lldwb.alistmediasync.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.lldwb.alistmediasync.dto.storage.StorageEngineCreateDTO;
import top.lldwb.alistmediasync.dto.storage.StorageEngineUpdateDTO;
import top.lldwb.alistmediasync.dto.storage.StorageEngineVO;
import top.lldwb.alistmediasync.entity.StorageEngine;
import top.lldwb.alistmediasync.entity.StorageEngine.EngineType;
import top.lldwb.alistmediasync.repository.StorageEngineRepository;
import top.lldwb.alistmediasync.service.engine.StorageEngineStrategy;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 存储引擎管理服务单元测试
 * <p>
 * 测试策略分发、CRUD、连接测试和字段校验。
 * </p>
 *
 * @author AList-Media-Sync
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StorageEngineService 策略分发与 CRUD 测试")
class StorageEngineServiceTest {

    @Mock
    private StorageEngineRepository repository;

    @Mock
    private StorageEngineStrategy alistStrategy;

    @Mock
    private StorageEngineStrategy localStrategy;

    private StorageEngineService service;

    private StorageEngineCreateDTO createDTO;
    private StorageEngineUpdateDTO updateDTO;
    private StorageEngine mockEngine;

    @BeforeEach
    void setUp() {
        when(alistStrategy.type()).thenReturn("ALIST");
        when(localStrategy.type()).thenReturn("LOCAL");

        service = new StorageEngineService(repository, List.of(alistStrategy, localStrategy));

        createDTO = new StorageEngineCreateDTO();
        createDTO.setName("测试引擎");
        createDTO.setEngineType("ALIST");
        createDTO.setBaseUrl("https://alist.example.com/");
        createDTO.setToken("test-token-123");

        updateDTO = new StorageEngineUpdateDTO();
        updateDTO.setName("更新后名称");

        mockEngine = new StorageEngine();
        mockEngine.setId(1L);
        mockEngine.setName("测试引擎");
        mockEngine.setEngineType(EngineType.ALIST);
        mockEngine.setBaseUrl("https://alist.example.com");
        mockEngine.setEncryptedToken("encrypted-token-value");
        mockEngine.setStatus(StorageEngine.EngineStatus.OFFLINE);
    }

    // ================================================================
    // 策略分发测试
    // ================================================================

    @Test
    @DisplayName("resolve 应返回 ALIST 策略")
    void resolveShouldReturnAListStrategy() {
        StorageEngine engine = new StorageEngine();
        engine.setEngineType(EngineType.ALIST);
        assertSame(alistStrategy, service.resolve(engine));
    }

    @Test
    @DisplayName("resolve 应返回 LOCAL 策略")
    void resolveShouldReturnLocalStrategy() {
        StorageEngine engine = new StorageEngine();
        engine.setEngineType(EngineType.LOCAL);
        assertSame(localStrategy, service.resolve(engine));
    }

    @Test
    @DisplayName("resolve 对 null engineType 应抛出 NullPointerException")
    void resolveShouldThrowForUnsupportedType() {
        StorageEngine engine = new StorageEngine();
        engine.setEngineType(null);
        assertThrows(NullPointerException.class, () -> service.resolve(engine));
    }

    // ================================================================
    // create 方法测试
    // ================================================================

    @Test
    @DisplayName("创建 ALIST 引擎 — 正常流程应返回 VO")
    void shouldCreateAListEngine() {
        when(repository.save(any(StorageEngine.class))).thenReturn(mockEngine);

        StorageEngineVO result = service.create(createDTO);

        assertNotNull(result);
        assertEquals("测试引擎", result.getName());
        assertEquals("ALIST", result.getEngineType());
        verify(repository).save(any(StorageEngine.class));
    }

    @Test
    @DisplayName("创建引擎 — URL 末尾斜杠应被移除")
    void shouldStripTrailingSlashOnCreate() {
        when(repository.save(any(StorageEngine.class))).thenAnswer(inv -> {
            StorageEngine saved = inv.getArgument(0);
            assertEquals("https://alist.example.com", saved.getBaseUrl());
            mockEngine.setBaseUrl(saved.getBaseUrl());
            return mockEngine;
        });

        StorageEngineVO result = service.create(createDTO);
        assertNotNull(result);
        verify(repository).save(any(StorageEngine.class));
    }

    @Test
    @DisplayName("创建引擎 — 初始状态应为 OFFLINE")
    void shouldSetStatusToOfflineOnCreate() {
        when(repository.save(any(StorageEngine.class))).thenAnswer(inv -> {
            StorageEngine saved = inv.getArgument(0);
            assertEquals(StorageEngine.EngineStatus.OFFLINE, saved.getStatus());
            return mockEngine;
        });

        service.create(createDTO);
        verify(repository).save(any(StorageEngine.class));
    }

    @Test
    @DisplayName("创建 ALIST 引擎 — 缺少 baseUrl 应抛出异常")
    void shouldThrowWhenAListMissingBaseUrl() {
        createDTO.setBaseUrl(null);
        assertThrows(IllegalArgumentException.class, () -> service.create(createDTO));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("创建 ALIST 引擎 — 缺少 token 应抛出异常")
    void shouldThrowWhenAListMissingToken() {
        createDTO.setToken(null);
        assertThrows(IllegalArgumentException.class, () -> service.create(createDTO));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("创建 LOCAL 引擎 — 缺少 localPath 应抛出异常")
    void shouldThrowWhenLocalMissingPath() {
        createDTO.setEngineType("LOCAL");
        createDTO.setBaseUrl(null);
        createDTO.setToken(null);
        createDTO.setLocalPath(null);
        assertThrows(IllegalArgumentException.class, () -> service.create(createDTO));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("创建引擎 — 不支持的 engineType 应抛出异常")
    void shouldThrowForUnsupportedEngineType() {
        createDTO.setEngineType("FTP");
        assertThrows(IllegalArgumentException.class, () -> service.create(createDTO));
        verify(repository, never()).save(any());
    }

    // ================================================================
    // update 方法测试
    // ================================================================

    @Test
    @DisplayName("更新引擎 — 正常更新字段")
    void shouldUpdateEngine() {
        when(repository.findById(1L)).thenReturn(Optional.of(mockEngine));
        when(repository.save(any(StorageEngine.class))).thenReturn(mockEngine);

        StorageEngineVO result = service.update(1L, updateDTO);

        assertNotNull(result);
        assertEquals("更新后名称", mockEngine.getName());
        verify(repository).save(mockEngine);
    }

    @Test
    @DisplayName("更新引擎 — 部分字段为 null 时保持原值")
    void shouldUpdateOnlyNonNullFieldsOnUpdate() {
        when(repository.findById(1L)).thenReturn(Optional.of(mockEngine));
        when(repository.save(any(StorageEngine.class))).thenReturn(mockEngine);

        StorageEngineUpdateDTO partialDTO = new StorageEngineUpdateDTO();
        partialDTO.setName("仅更名");

        StorageEngineVO result = service.update(1L, partialDTO);
        assertNotNull(result);
        assertEquals("仅更名", mockEngine.getName());
        assertEquals("https://alist.example.com", mockEngine.getBaseUrl());
    }

    @Test
    @DisplayName("更新引擎 — 不存在应抛出 NoSuchElementException")
    void shouldThrowWhenUpdatingNonExistent() {
        when(repository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service.update(999L, updateDTO));
        verify(repository, never()).save(any());
    }

    // ================================================================
    // delete 方法测试
    // ================================================================

    @Test
    @DisplayName("删除引擎 — 正常流程")
    void shouldDeleteEngine() {
        when(repository.findById(1L)).thenReturn(Optional.of(mockEngine));
        assertDoesNotThrow(() -> service.delete(1L));
        verify(repository).delete(mockEngine);
    }

    @Test
    @DisplayName("删除引擎 — 不存在应抛出 NoSuchElementException")
    void shouldThrowWhenDeletingNonExistent() {
        when(repository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service.delete(999L));
        verify(repository, never()).delete(any());
    }

    // ================================================================
    // getById 方法测试
    // ================================================================

    @Test
    @DisplayName("按 ID 查询 — 正常返回 VO")
    void shouldGetById() {
        when(repository.findById(1L)).thenReturn(Optional.of(mockEngine));

        StorageEngineVO result = service.getById(1L);
        assertNotNull(result);
        assertEquals("测试引擎", result.getName());
        assertEquals("ALIST", result.getEngineType());
    }

    @Test
    @DisplayName("按 ID 查询 — 不存在应抛出异常")
    void shouldThrowWhenGetByIdNotFound() {
        when(repository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service.getById(999L));
    }

    // ================================================================
    // listAll 方法测试
    // ================================================================

    @Test
    @DisplayName("查询全部 — 有数据时返回列表")
    void shouldListAll() {
        StorageEngine engine2 = new StorageEngine();
        engine2.setId(2L);
        engine2.setName("引擎2");
        engine2.setEngineType(EngineType.LOCAL);
        engine2.setLocalPath("/data");
        engine2.setStatus(StorageEngine.EngineStatus.ONLINE);

        when(repository.findAll()).thenReturn(List.of(mockEngine, engine2));

        List<StorageEngineVO> result = service.listAll();
        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("查询全部 — 无数据时返回空列表")
    void shouldReturnEmptyListWhenNoEngines() {
        when(repository.findAll()).thenReturn(List.of());
        List<StorageEngineVO> result = service.listAll();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ================================================================
    // testConnection 方法测试
    // ================================================================

    @Test
    @DisplayName("测试连接 — ALIST 成功时更新状态为 ONLINE")
    void shouldSetOnlineWhenConnectionSuccessful() {
        when(repository.findById(1L)).thenReturn(Optional.of(mockEngine));
        when(alistStrategy.testConnection(mockEngine)).thenReturn(true);
        when(repository.save(any(StorageEngine.class))).thenReturn(mockEngine);

        boolean result = service.testConnection(1L);
        assertTrue(result);
        assertEquals(StorageEngine.EngineStatus.ONLINE, mockEngine.getStatus());
        verify(repository).save(mockEngine);
    }

    @Test
    @DisplayName("测试连接 — 失败时更新状态为 ERROR")
    void shouldSetErrorWhenConnectionFails() {
        when(repository.findById(1L)).thenReturn(Optional.of(mockEngine));
        when(alistStrategy.testConnection(mockEngine)).thenReturn(false);
        when(repository.save(any(StorageEngine.class))).thenReturn(mockEngine);

        boolean result = service.testConnection(1L);
        assertFalse(result);
        assertEquals(StorageEngine.EngineStatus.ERROR, mockEngine.getStatus());
    }

    @Test
    @DisplayName("测试连接 — 引擎不存在应抛出异常")
    void shouldThrowWhenTestingNonExistent() {
        when(repository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service.testConnection(999L));
    }

    // ================================================================
    // getEntity 方法测试
    // ================================================================

    @Test
    @DisplayName("getEntity 应返回实体")
    void getEntityShouldReturnEntity() {
        when(repository.findById(1L)).thenReturn(Optional.of(mockEngine));
        StorageEngine result = service.getEntity(1L);
        assertSame(mockEngine, result);
    }
}
