package top.lldwb.alistmediasync.storage.service.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.client.RestClient;
import top.lldwb.alistmediasync.sync.dto.sync.DirectoryEntryVO;
import top.lldwb.alistmediasync.sync.dto.sync.FileEntry;
import top.lldwb.alistmediasync.storage.entity.StorageEngine;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * AListStorageStrategy 单元测试
 * <p>
 * 使用 Mock RestClient 验证 AList API 调用逻辑。
 * </p>
 *
 * @author AList-Media-Sync
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AListStorageStrategy 单元测试")
class AListStorageStrategyTest {

    @Mock
    private RestClient.Builder restClientBuilder;

    private AListStorageStrategy strategy;

    private StorageEngine engine;

    @BeforeEach
    void setUp() {
        // 提供真实的 RestClient 实例，配合无效 URL 测试异常处理
        when(restClientBuilder.build()).thenReturn(RestClient.create());
        strategy = new AListStorageStrategy(restClientBuilder);
        engine = new StorageEngine();
        engine.setId(1L);
        engine.setName("测试AList");
        engine.setEngineType(StorageEngine.EngineType.ALIST);
        engine.setBaseUrl("https://alist.example.com");
        engine.setEncryptedToken("test-token");
    }

    @Test
    @DisplayName("type() 应返回 ALIST")
    void typeShouldReturnALIST() {
        assertEquals("ALIST", strategy.type());
    }

    @Test
    @DisplayName("testConnection 失败时应返回 false")
    void testConnectionShouldReturnFalseOnError() {
        // 使用无效 URL 测试异常处理
        engine.setBaseUrl("https://invalid-host-that-does-not-exist.local");
        boolean result = strategy.testConnection(engine);
        assertFalse(result);
    }

    @Test
    @DisplayName("listDirectories 应对无效路径返回空列表")
    void listDirectoriesShouldReturnEmptyForInvalidPath() {
        // 使用无效 URL 测试异常处理返回空列表
        engine.setBaseUrl("https://invalid-host.local");
        List<DirectoryEntryVO> result = strategy.listDirectories(engine, "/");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
