package top.lldwb.alistmediasync.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.lldwb.alistmediasync.client.AListClient;
import top.lldwb.alistmediasync.dto.storage.StorageEngineCreateDTO;
import top.lldwb.alistmediasync.dto.storage.StorageEngineUpdateDTO;
import top.lldwb.alistmediasync.dto.storage.StorageEngineVO;
import top.lldwb.alistmediasync.entity.StorageEngine;
import top.lldwb.alistmediasync.repository.StorageEngineRepository;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 存储引擎管理服务单元测试
 * <p>
 * 覆盖所有 6 个 public 方法的正常和异常场景。
 * </p>
 *
 * @author AList-Media-Sync
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("存储引擎管理服务测试")
class StorageEngineServiceTest {

    @Mock
    private StorageEngineRepository repository;

    @Mock
    private AListClient alistClient;

    @InjectMocks
    private StorageEngineService service;

    private StorageEngineCreateDTO createDTO;
    private StorageEngineUpdateDTO updateDTO;
    private StorageEngine mockEngine;

    @BeforeEach
    void setUp() {
        createDTO = new StorageEngineCreateDTO();
        createDTO.setName("测试AList");
        createDTO.setBaseUrl("https://alist.example.com/");
        createDTO.setUsername("admin");
        createDTO.setToken("test-token-123");

        updateDTO = new StorageEngineUpdateDTO();
        updateDTO.setName("更新后名称");
        updateDTO.setBaseUrl("https://alist2.example.com");

        mockEngine = new StorageEngine();
        mockEngine.setId(1L);
        mockEngine.setName("测试AList");
        mockEngine.setBaseUrl("https://alist.example.com");
        mockEngine.setUsername("admin");
        mockEngine.setEncryptedToken("encrypted-token-value");
        mockEngine.setStatus(StorageEngine.EngineStatus.OFFLINE);
    }

    // ================================================================
    // create 方法测试
    // ================================================================

    @Test
    @DisplayName("创建引擎 — 正常流程应返回 VO")
    void shouldCreateEngine() {
        when(repository.save(any(StorageEngine.class))).thenReturn(mockEngine);

        StorageEngineVO result = service.create(createDTO);

        assertNotNull(result);
        assertEquals("测试AList", result.getName());
        verify(repository).save(any(StorageEngine.class));
    }

    @Test
    @DisplayName("创建引擎 — URL 末尾斜杠应被移除")
    void shouldStripTrailingSlashOnCreate() {
        createDTO.setBaseUrl("https://alist.example.com///");
        when(repository.save(any(StorageEngine.class))).thenAnswer(inv -> {
            StorageEngine saved = inv.getArgument(0);
            // 验证 baseUrl 的斜杠已在传入 save 前被移除
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

    // ================================================================
    // update 方法测试
    // ================================================================

    @Test
    @DisplayName("更新引擎 — 正常更新全部字段")
    void shouldUpdateEngine() {
        when(repository.findById(1L)).thenReturn(Optional.of(mockEngine));
        when(repository.save(any(StorageEngine.class))).thenReturn(mockEngine);

        StorageEngineVO result = service.update(1L, updateDTO);

        assertNotNull(result);
        assertEquals("更新后名称", mockEngine.getName());
        assertEquals("https://alist2.example.com", mockEngine.getBaseUrl());
        verify(repository).save(mockEngine);
    }

    @Test
    @DisplayName("更新引擎 — 部分字段为 null 时保持原值")
    void shouldUpdateOnlyNonNullFieldsOnUpdate() {
        when(repository.findById(1L)).thenReturn(Optional.of(mockEngine));
        when(repository.save(any(StorageEngine.class))).thenReturn(mockEngine);

        StorageEngineUpdateDTO partialDTO = new StorageEngineUpdateDTO();
        partialDTO.setName("仅更名");
        // 其他字段均为 null

        StorageEngineVO result = service.update(1L, partialDTO);

        assertNotNull(result);
        assertEquals("仅更名", mockEngine.getName());
        assertEquals("https://alist.example.com", mockEngine.getBaseUrl()); // 不变
    }

    @Test
    @DisplayName("更新引擎 — URL 末尾斜杠应被移除")
    void shouldStripTrailingSlashOnUpdate() {
        when(repository.findById(1L)).thenReturn(Optional.of(mockEngine));
        when(repository.save(any(StorageEngine.class))).thenReturn(mockEngine);

        updateDTO.setBaseUrl("https://new.example.com//");
        service.update(1L, updateDTO);

        assertEquals("https://new.example.com", mockEngine.getBaseUrl());
    }

    @Test
    @DisplayName("更新引擎 — 引擎不存在应抛出 NoSuchElementException")
    void shouldThrowWhenUpdatingNonExistentEngine() {
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
    @DisplayName("删除引擎 — 引擎不存在应抛出 NoSuchElementException")
    void shouldThrowWhenDeletingNonExistentEngine() {
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
        assertEquals("测试AList", result.getName());
        assertEquals("https://alist.example.com", result.getBaseUrl());
    }

    @Test
    @DisplayName("按 ID 查询 — 不存在应抛出 NoSuchElementException")
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
        engine2.setBaseUrl("https://alist2.example.com");
        engine2.setUsername("user2");
        engine2.setStatus(StorageEngine.EngineStatus.ONLINE);

        when(repository.findAll()).thenReturn(List.of(mockEngine, engine2));

        List<StorageEngineVO> result = service.listAll();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("测试AList", result.get(0).getName());
        assertEquals("引擎2", result.get(1).getName());
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
    @DisplayName("测试连接 — 成功时更新状态为 ONLINE")
    void shouldSetOnlineWhenConnectionSuccessful() {
        when(repository.findById(1L)).thenReturn(Optional.of(mockEngine));
        when(alistClient.testConnection("https://alist.example.com", "encrypted-token-value"))
            .thenReturn(true);
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
        when(alistClient.testConnection(anyString(), anyString()))
            .thenReturn(false);
        when(repository.save(any(StorageEngine.class))).thenReturn(mockEngine);

        boolean result = service.testConnection(1L);

        assertFalse(result);
        assertEquals(StorageEngine.EngineStatus.ERROR, mockEngine.getStatus());
        verify(repository).save(mockEngine);
    }

    @Test
    @DisplayName("测试连接 — 引擎不存在应抛出 NoSuchElementException")
    void shouldThrowWhenTestingNonExistentEngine() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.testConnection(999L));
        verify(alistClient, never()).testConnection(anyString(), anyString());
    }
}
