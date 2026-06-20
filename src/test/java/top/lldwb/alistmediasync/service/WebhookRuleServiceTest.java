package top.lldwb.alistmediasync.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.lldwb.alistmediasync.dto.webhook.WebhookRuleCreateDTO;
import top.lldwb.alistmediasync.dto.webhook.WebhookRuleVO;
import top.lldwb.alistmediasync.entity.StorageEngine;
import top.lldwb.alistmediasync.entity.WebhookRule;
import top.lldwb.alistmediasync.repository.StorageEngineRepository;
import top.lldwb.alistmediasync.repository.WebhookRuleRepository;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Webhook 规则管理服务单元测试
 * <p>
 * 覆盖所有 7 个 public 方法的正常和异常场景。
 * </p>
 *
 * @author AList-Media-Sync
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Webhook 规则管理服务测试")
class WebhookRuleServiceTest {

    @Mock
    private WebhookRuleRepository repository;

    @Mock
    private StorageEngineRepository storageEngineRepository;

    @InjectMocks
    private WebhookRuleService service;

    private WebhookRuleCreateDTO createDTO;
    private StorageEngine mockEngine;
    private WebhookRule mockRule;

    @BeforeEach
    void setUp() {
        createDTO = new WebhookRuleCreateDTO();
        createDTO.setName("测试规则");
        createDTO.setTriggerEventType(WebhookRule.WebhookEventType.FILE_CLOSED);
        createDTO.setAction(WebhookRule.RuleAction.SYNC_ONLY);
        createDTO.setTargetEngineId(1L);
        createDTO.setTargetFilePath("/target/path");

        mockEngine = new StorageEngine();
        mockEngine.setId(1L);
        mockEngine.setName("测试引擎");

        mockRule = new WebhookRule();
        mockRule.setId(1L);
        mockRule.setName("测试规则");
        mockRule.setTriggerEventType(WebhookRule.WebhookEventType.FILE_CLOSED);
        mockRule.setAction(WebhookRule.RuleAction.SYNC_ONLY);
        mockRule.setTargetEngine(mockEngine);
        mockRule.setTargetFilePath("/target/path");
        mockRule.setEnabled(true);
    }

    // ================================================================
    // create 方法测试
    // ================================================================

    @Test
    @DisplayName("创建规则 — 正常流程应返回 VO")
    void shouldCreateRule() {
        when(storageEngineRepository.findById(1L)).thenReturn(Optional.of(mockEngine));
        when(repository.save(any(WebhookRule.class))).thenReturn(mockRule);

        WebhookRuleVO result = service.create(createDTO);

        assertNotNull(result);
        assertEquals("测试规则", result.getName());
        verify(repository).save(any(WebhookRule.class));
    }

    @Test
    @DisplayName("创建规则 — targetEngineId 为 null 应抛出 IllegalArgumentException")
    void shouldThrowWhenTargetEngineIdIsNull() {
        createDTO.setTargetEngineId(null);

        assertThrows(IllegalArgumentException.class, () -> service.create(createDTO));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("创建规则 — targetEngineId <= 0 应抛出 IllegalArgumentException")
    void shouldThrowWhenTargetEngineIdIsInvalid() {
        createDTO.setTargetEngineId(0L);

        assertThrows(IllegalArgumentException.class, () -> service.create(createDTO));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("创建规则 — 目标引擎不存在应抛出 NoSuchElementException")
    void shouldThrowWhenTargetEngineNotFound() {
        when(storageEngineRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.create(createDTO));
        verify(repository, never()).save(any());
    }

    // ================================================================
    // update 方法测试
    // ================================================================

    @Test
    @DisplayName("更新规则 — 正常更新全部字段")
    void shouldUpdateRule() {
        when(repository.findById(1L)).thenReturn(Optional.of(mockRule));
        when(storageEngineRepository.getReferenceById(2L)).thenReturn(mockEngine);
        when(repository.save(any(WebhookRule.class))).thenReturn(mockRule);

        createDTO.setName("更新后名称");
        createDTO.setTargetEngineId(2L);
        WebhookRuleVO result = service.update(1L, createDTO);

        assertNotNull(result);
        assertEquals("更新后名称", mockRule.getName());
        verify(repository).save(mockRule);
    }

    @Test
    @DisplayName("更新规则 — 部分字段为 null 时保持原值")
    void shouldUpdateOnlyNonNullFields() {
        when(repository.findById(1L)).thenReturn(Optional.of(mockRule));
        when(storageEngineRepository.getReferenceById(1L)).thenReturn(mockEngine);
        when(repository.save(any(WebhookRule.class))).thenReturn(mockRule);

        WebhookRuleCreateDTO partialDTO = new WebhookRuleCreateDTO();
        partialDTO.setName("仅更新名称");
        partialDTO.setAction(WebhookRule.RuleAction.SYNC_ONLY);
        partialDTO.setTargetEngineId(1L); // SYNC_ONLY 时 targetEngineId 必填
        // triggerEventType、roomIdFilter 等字段为 null，应保持原值

        WebhookRuleVO result = service.update(1L, partialDTO);

        assertNotNull(result);
        assertEquals("仅更新名称", mockRule.getName());
        // 未传入的字段应保持原值
        assertEquals(WebhookRule.WebhookEventType.FILE_CLOSED, mockRule.getTriggerEventType());
    }

    @Test
    @DisplayName("更新规则 — 规则不存在应抛出 NoSuchElementException")
    void shouldThrowWhenUpdatingNonExistentRule() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.update(999L, createDTO));
        verify(repository, never()).save(any());
    }

    // ================================================================
    // delete 方法测试
    // ================================================================

    @Test
    @DisplayName("删除规则 — 正常流程")
    void shouldDeleteRule() {
        when(repository.findById(1L)).thenReturn(Optional.of(mockRule));

        assertDoesNotThrow(() -> service.delete(1L));
        verify(repository).delete(mockRule);
    }

    @Test
    @DisplayName("删除规则 — 规则不存在应抛出 NoSuchElementException")
    void shouldThrowWhenDeletingNonExistentRule() {
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
        when(repository.findById(1L)).thenReturn(Optional.of(mockRule));

        WebhookRuleVO result = service.getById(1L);

        assertNotNull(result);
        assertEquals("测试规则", result.getName());
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
        WebhookRule rule2 = new WebhookRule();
        rule2.setId(2L);
        rule2.setName("规则2");
        rule2.setTriggerEventType(WebhookRule.WebhookEventType.SESSION_ENDED);
        rule2.setAction(WebhookRule.RuleAction.BOTH);
        rule2.setTargetEngine(mockEngine);
        rule2.setEnabled(false);

        when(repository.findAll()).thenReturn(List.of(mockRule, rule2));

        List<WebhookRuleVO> result = service.listAll();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("测试规则", result.get(0).getName());
        assertEquals("规则2", result.get(1).getName());
    }

    @Test
    @DisplayName("查询全部 — 无数据时返回空列表")
    void shouldReturnEmptyListWhenNoData() {
        when(repository.findAll()).thenReturn(List.of());

        List<WebhookRuleVO> result = service.listAll();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ================================================================
    // enable / disable 方法测试
    // ================================================================

    @Test
    @DisplayName("启用规则 — 正常设置 enabled=true")
    void shouldEnableRule() {
        mockRule.setEnabled(false); // 初始禁用
        when(repository.findById(1L)).thenReturn(Optional.of(mockRule));
        when(repository.save(any(WebhookRule.class))).thenReturn(mockRule);

        WebhookRuleVO result = service.enable(1L);

        assertNotNull(result);
        assertTrue(mockRule.getEnabled());
        verify(repository).save(mockRule);
    }

    @Test
    @DisplayName("禁用规则 — 正常设置 enabled=false")
    void shouldDisableRule() {
        when(repository.findById(1L)).thenReturn(Optional.of(mockRule));
        when(repository.save(any(WebhookRule.class))).thenReturn(mockRule);

        WebhookRuleVO result = service.disable(1L);

        assertNotNull(result);
        assertFalse(mockRule.getEnabled());
        verify(repository).save(mockRule);
    }

    @Test
    @DisplayName("启用规则 — 规则不存在应抛出 NoSuchElementException")
    void shouldThrowWhenEnablingNonExistentRule() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.enable(999L));
    }

    @Test
    @DisplayName("禁用规则 — 规则不存在应抛出 NoSuchElementException")
    void shouldThrowWhenDisablingNonExistentRule() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.disable(999L));
    }
}
