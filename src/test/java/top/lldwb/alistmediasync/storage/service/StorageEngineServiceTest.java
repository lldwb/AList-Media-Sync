package top.lldwb.alistmediasync.storage.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.lldwb.alistmediasync.storage.dto.StorageEngineCreateDTO;
import top.lldwb.alistmediasync.storage.dto.StorageEngineUpdateDTO;
import top.lldwb.alistmediasync.storage.dto.StorageEngineVO;
import top.lldwb.alistmediasync.storage.entity.StorageEngine;
import top.lldwb.alistmediasync.storage.entity.StorageEngine.EngineType;
import top.lldwb.alistmediasync.common.config.AppProperties;
import top.lldwb.alistmediasync.storage.repository.StorageEngineRepository;
import top.lldwb.alistmediasync.storage.service.engine.StorageEngineStrategy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    @Mock
    private AppProperties appProperties;

    private StorageEngineService service;

    @TempDir
    Path tempDir;

    private StorageEngineCreateDTO createDTO;
    private StorageEngineUpdateDTO updateDTO;
    private StorageEngine mockEngine;

    @BeforeEach
    void setUp() {
        when(alistStrategy.type()).thenReturn("ALIST");
        when(localStrategy.type()).thenReturn("LOCAL");

        service = new StorageEngineService(repository, List.of(alistStrategy, localStrategy), appProperties);

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

    /** 将 createDTO 切换为 LOCAL 类型并指向指定路径 */
    private void useLocalEngineWithPath(String localPath) {
        createDTO.setEngineType("LOCAL");
        createDTO.setBaseUrl(null);
        createDTO.setToken(null);
        createDTO.setLocalPath(localPath);
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
    @DisplayName("resolve 对 null engineType 实际抛出 NullPointerException（javadoc 只声明了 IllegalArgumentException）")
    void resolveShouldThrowNullPointerExceptionForNullEngineType() {
        StorageEngine engine = new StorageEngine();
        engine.setEngineType(null);

        // 现状：engine.getEngineType().name() 缺少 null 保护，NPE 先于"类型不支持"的判断抛出。
        // javadoc 声明的 @throws IllegalArgumentException 只在类型查不到策略时触发，故此处如实断言实际异常类型。
        assertThrows(NullPointerException.class, () -> service.resolve(engine));
    }

    @Test
    @DisplayName("resolve 对查不到策略的引擎类型应抛出 IllegalArgumentException")
    void resolveShouldThrowIllegalArgumentExceptionWhenStrategyAbsent() {
        // 只注册 LOCAL 策略的实例：ALIST 引擎在策略表中不存在，走文档化的 IllegalArgumentException 分支
        StorageEngineService localOnlyService =
            new StorageEngineService(repository, List.of(localStrategy), appProperties);
        StorageEngine engine = new StorageEngine();
        engine.setEngineType(EngineType.ALIST);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> localOnlyService.resolve(engine));

        assertTrue(ex.getMessage().contains("不支持的引擎类型"), "实际消息：" + ex.getMessage());
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

    @Test
    @DisplayName("创建引擎 — engineType 大小写不敏感（alist 应解析为 ALIST）")
    void shouldParseEngineTypeCaseInsensitively() {
        createDTO.setEngineType("alist");
        when(repository.save(any(StorageEngine.class))).thenAnswer(inv -> {
            StorageEngine saved = inv.getArgument(0);
            assertEquals(EngineType.ALIST, saved.getEngineType());
            mockEngine.setLocalPath(saved.getLocalPath());
            return mockEngine;
        });

        StorageEngineVO result = service.create(createDTO);

        assertEquals("ALIST", result.getEngineType());
        verify(repository).save(any(StorageEngine.class));
    }

    @Test
    @DisplayName("创建引擎 — engineType 为 null 时实际抛出 NullPointerException（非文档化的 IllegalArgumentException）")
    void shouldThrowNullPointerExceptionWhenEngineTypeIsNull() {
        // 现状：parseEngineType 先做 engineTypeStr.toUpperCase()，null 时 NPE 直接逃逸出 catch (IllegalArgumentException)。
        createDTO.setEngineType(null);

        assertThrows(NullPointerException.class, () -> service.create(createDTO));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("创建 ALIST 引擎 — baseUrl 为空白字符串应抛出异常")
    void shouldThrowWhenAListBaseUrlBlank() {
        createDTO.setBaseUrl("   ");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.create(createDTO));

        assertTrue(ex.getMessage().contains("服务器地址"), "实际消息：" + ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("创建 ALIST 引擎 — token 为空白字符串应抛出异常")
    void shouldThrowWhenAListTokenBlank() {
        createDTO.setToken("   ");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.create(createDTO));

        assertTrue(ex.getMessage().contains("API 令牌"), "实际消息：" + ex.getMessage());
        verify(repository, never()).save(any());
    }

    // ---- validateFields 的 LOCAL 分支：路径存在性 / 目录性 ----

    @Test
    @DisplayName("创建 LOCAL 引擎 — 路径不存在应抛出异常且提示路径不存在")
    void shouldThrowWhenLocalPathDoesNotExist() {
        useLocalEngineWithPath(tempDir.resolve("no-such-dir").toString());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.create(createDTO));

        assertTrue(ex.getMessage().contains("本地路径不存在"), "实际消息：" + ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("创建 LOCAL 引擎 — 路径指向文件而非目录应抛出异常")
    void shouldThrowWhenLocalPathIsAFile() throws IOException {
        Path file = Files.createFile(tempDir.resolve("a-file.txt"));
        useLocalEngineWithPath(file.toString());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.create(createDTO));

        assertTrue(ex.getMessage().contains("本地路径不是目录"), "实际消息：" + ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("创建 LOCAL 引擎 — 路径为已存在目录时应创建成功且初始状态 OFFLINE")
    void shouldCreateLocalEngineWithExistingDirectory() {
        useLocalEngineWithPath(tempDir.toString());
        when(repository.save(any(StorageEngine.class))).thenAnswer(inv -> {
            StorageEngine saved = inv.getArgument(0);
            assertEquals(EngineType.LOCAL, saved.getEngineType());
            assertEquals(tempDir.toString(), saved.getLocalPath());
            assertEquals(StorageEngine.EngineStatus.OFFLINE, saved.getStatus());
            return saved;
        });

        StorageEngineVO result = service.create(createDTO);

        assertEquals("测试引擎", result.getName());
        assertEquals("LOCAL", result.getEngineType());
        assertEquals("OFFLINE", result.getStatus());
        verify(repository).save(any(StorageEngine.class));
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

    @Test
    @DisplayName("更新引擎 — baseUrl 末尾斜杠应被移除，token 与 localPath 应被写入实体")
    void shouldUpdateAllFieldsAndStripTrailingSlash() {
        when(repository.findById(1L)).thenReturn(Optional.of(mockEngine));
        when(repository.save(any(StorageEngine.class))).thenReturn(mockEngine);

        StorageEngineUpdateDTO fullDTO = new StorageEngineUpdateDTO();
        fullDTO.setBaseUrl("https://new.example.com/");
        fullDTO.setToken("new-token");
        fullDTO.setLocalPath("/data/media");

        service.update(1L, fullDTO);

        assertEquals("https://new.example.com", mockEngine.getBaseUrl(), "末尾斜杠应被移除");
        assertEquals("new-token", mockEngine.getEncryptedToken());
        assertEquals("/data/media", mockEngine.getLocalPath());
        verify(repository).save(mockEngine);
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

    // ================================================================
    // healthCheck 方法测试（@Scheduled 定时健康检查）
    // ================================================================

    @Test
    @DisplayName("healthCheck — 引擎列表为空时应直接返回，不做任何写库")
    void healthCheckShouldReturnEarlyWhenNoEngines() {
        when(repository.findAll()).thenReturn(List.of());

        service.healthCheck();

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("healthCheck — 引擎查不到策略时应跳过该引擎，同时仍检查其它引擎")
    void healthCheckShouldSkipEngineWithoutStrategy() {
        StorageEngine localEngine = new StorageEngine();
        localEngine.setId(2L);
        localEngine.setName("本地引擎");
        localEngine.setEngineType(EngineType.LOCAL);
        localEngine.setStatus(StorageEngine.EngineStatus.ONLINE);
        // ALIST 引擎状态设为 ONLINE：连接测试返回 false 后应触发 OFFLINE 变更并落库
        mockEngine.setStatus(StorageEngine.EngineStatus.ONLINE);

        // 只注册 ALIST 策略的实例：LOCAL 引擎在策略表中查不到
        StorageEngineService alistOnlyService =
            new StorageEngineService(repository, List.of(alistStrategy), appProperties);
        when(repository.findAll()).thenReturn(List.of(localEngine, mockEngine));
        when(alistStrategy.testConnection(mockEngine)).thenReturn(false);
        when(repository.save(any(StorageEngine.class))).thenReturn(mockEngine);

        alistOnlyService.healthCheck();

        // 缺策略的引擎被 continue：状态保持原值，且不写库
        assertEquals(StorageEngine.EngineStatus.ONLINE, localEngine.getStatus(), "缺策略的引擎状态不应被改动");
        ArgumentCaptor<StorageEngine> saved = ArgumentCaptor.forClass(StorageEngine.class);
        verify(repository, times(1)).save(saved.capture());
        assertSame(mockEngine, saved.getValue(), "只有能解析到策略的引擎才应被写库");
        // 策略存在的引擎仍被探测
        verify(alistStrategy).testConnection(mockEngine);
    }

    @Test
    @DisplayName("healthCheck — 连接测试成功且状态由 OFFLINE 变化时应置 ONLINE 并落库")
    void healthCheckShouldSetOnlineWhenStatusChanged() {
        mockEngine.setStatus(StorageEngine.EngineStatus.OFFLINE);
        when(repository.findAll()).thenReturn(List.of(mockEngine));
        when(alistStrategy.testConnection(mockEngine)).thenReturn(true);
        when(repository.save(any(StorageEngine.class))).thenReturn(mockEngine);

        service.healthCheck();

        assertEquals(StorageEngine.EngineStatus.ONLINE, mockEngine.getStatus());
        verify(repository).save(mockEngine);
    }

    @Test
    @DisplayName("healthCheck — 连接测试失败且状态由 ONLINE 变化时应置 OFFLINE 并落库")
    void healthCheckShouldSetOfflineWhenConnectionFails() {
        mockEngine.setStatus(StorageEngine.EngineStatus.ONLINE);
        when(repository.findAll()).thenReturn(List.of(mockEngine));
        when(alistStrategy.testConnection(mockEngine)).thenReturn(false);
        when(repository.save(any(StorageEngine.class))).thenReturn(mockEngine);

        service.healthCheck();

        assertEquals(StorageEngine.EngineStatus.OFFLINE, mockEngine.getStatus());
        verify(repository).save(mockEngine);
    }

    @Test
    @DisplayName("healthCheck — 状态未变化时不应写库（避免无变化的定时写放大）")
    void healthCheckShouldNotPersistWhenStatusUnchanged() {
        mockEngine.setStatus(StorageEngine.EngineStatus.ONLINE);
        when(repository.findAll()).thenReturn(List.of(mockEngine));
        when(alistStrategy.testConnection(mockEngine)).thenReturn(true);

        service.healthCheck();

        assertEquals(StorageEngine.EngineStatus.ONLINE, mockEngine.getStatus());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("healthCheck — 连接测试抛出异常时应置 ERROR 并落库")
    void healthCheckShouldSetErrorWhenStrategyThrows() {
        mockEngine.setStatus(StorageEngine.EngineStatus.ONLINE);
        when(repository.findAll()).thenReturn(List.of(mockEngine));
        when(alistStrategy.testConnection(mockEngine)).thenThrow(new RuntimeException("连接超时"));
        when(repository.save(any(StorageEngine.class))).thenReturn(mockEngine);

        service.healthCheck();

        assertEquals(StorageEngine.EngineStatus.ERROR, mockEngine.getStatus());
        verify(repository).save(mockEngine);
    }

    @Test
    @DisplayName("healthCheck — 引擎已处于 ERROR 时异常不应触发重复写库")
    void healthCheckShouldNotPersistAgainWhenAlreadyError() {
        mockEngine.setStatus(StorageEngine.EngineStatus.ERROR);
        when(repository.findAll()).thenReturn(List.of(mockEngine));
        when(alistStrategy.testConnection(mockEngine)).thenThrow(new RuntimeException("连接超时"));

        service.healthCheck();

        assertEquals(StorageEngine.EngineStatus.ERROR, mockEngine.getStatus());
        verify(repository, never()).save(any());
    }
}
