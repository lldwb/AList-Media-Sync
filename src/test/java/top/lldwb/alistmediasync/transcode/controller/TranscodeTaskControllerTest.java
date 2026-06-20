package top.lldwb.alistmediasync.transcode.controller;

import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import top.lldwb.alistmediasync.common.config.AppProperties;
import top.lldwb.alistmediasync.common.interceptor.AuthInterceptor;
import top.lldwb.alistmediasync.common.service.CleanupService;
import top.lldwb.alistmediasync.transcode.dto.transcode.TranscodeTaskCreateDTO;
import top.lldwb.alistmediasync.transcode.entity.TranscodeTask;
import top.lldwb.alistmediasync.transcode.service.TranscodeService;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 转码任务控制器 WebMvcTest
 * <p>
 * 覆盖原目录转码选项的请求验证。
 * </p>
 *
 * @author AList-Media-Sync
 */
@WebMvcTest(TranscodeTaskController.class)
@DisplayName("转码任务控制器测试")
class TranscodeTaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TranscodeService transcodeService;

    @MockitoBean
    private CleanupService cleanupService;

    @MockitoBean
    private AuthInterceptor authInterceptor;

    @MockitoBean
    private AppProperties appProperties;

    private final JsonMapper objectMapper = new JsonMapper();

    @BeforeEach
    void setUp() throws Exception {
        // 绕过认证拦截器
        when(authInterceptor.preHandle(any(), any(), any())).thenReturn(true);

        // 模拟创建任务返回
        TranscodeTask mockTask = new TranscodeTask();
        mockTask.setId(1L);
        mockTask.setSourceFilePath("/videos/test.flv");
        mockTask.setTargetFilePath("/videos/test.mp3");
        mockTask.setTargetFormat(TranscodeTask.TargetFormat.MP3);
        mockTask.setStatus(TranscodeTask.TranscodeStatus.PENDING);
        when(transcodeService.createTask(any(), any(), any(), any(), any(), any(), anyBoolean()))
            .thenReturn(mockTask);
    }

    @Test
    @DisplayName("sameDirectoryTranscode=true 且 targetFilePath 为空时创建成功（200）")
    void shouldCreateTaskWhenSameDirectoryTranscodeTrueAndTargetPathEmpty() throws Exception {
        TranscodeTaskCreateDTO dto = new TranscodeTaskCreateDTO();
        dto.setSourceFilePath("/videos/test.flv");
        dto.setTargetFilePath(null); // 不填目标路径
        dto.setTargetFormat(TranscodeTask.TargetFormat.MP3);
        dto.setTargetEngineId(1L);
        dto.setSameDirectoryTranscode(true);

        mockMvc.perform(post("/api/transcode-tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("sameDirectoryTranscode=false 且 targetFilePath 为空时应返回错误")
    void shouldReturnErrorWhenSameDirectoryTranscodeFalseAndTargetPathEmpty() throws Exception {
        // 模拟 Service 层抛出校验异常
        when(transcodeService.createTask(any(), any(), any(), any(), any(), any(), eq(false)))
            .thenThrow(new IllegalArgumentException("未启用原目录转码时，目标路径为必填"));

        TranscodeTaskCreateDTO dto = new TranscodeTaskCreateDTO();
        dto.setSourceFilePath("/videos/test.flv");
        dto.setTargetFilePath(null);
        dto.setTargetFormat(TranscodeTask.TargetFormat.MP3);
        dto.setTargetEngineId(1L);
        dto.setSameDirectoryTranscode(false);

        mockMvc.perform(post("/api/transcode-tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("sameDirectoryTranscode=true 时忽略传入的 targetFilePath")
    void shouldIgnoreTargetPathWhenSameDirectoryTranscodeTrue() throws Exception {
        TranscodeTaskCreateDTO dto = new TranscodeTaskCreateDTO();
        dto.setSourceFilePath("/videos/test.flv");
        dto.setTargetFilePath("/ignored/path/");
        dto.setTargetFormat(TranscodeTask.TargetFormat.MP3);
        dto.setTargetEngineId(1L);
        dto.setSameDirectoryTranscode(true);

        mockMvc.perform(post("/api/transcode-tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }
}
