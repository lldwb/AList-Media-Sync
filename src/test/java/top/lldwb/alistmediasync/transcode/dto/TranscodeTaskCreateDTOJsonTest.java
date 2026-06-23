package top.lldwb.alistmediasync.transcode.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import top.lldwb.alistmediasync.transcode.dto.transcode.TranscodeTaskCreateDTO;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 转码任务创建 DTO 的 JSON 反序列化测试
 * <p>
 * 回归测试：曾出现前端发送 {@code "sourceDirectoryTranscode": true} 时
 * 后端反序列化为 false 的问题（Jackson 3.x 对 boolean primitive + Lombok
 * is-getter 的属性名推导差异）。本测试保护字段类型 + 注解组合不被回退。
 * </p>
 *
 * @author AList-Media-Sync
 */
@DisplayName("TranscodeTaskCreateDTO JSON 反序列化测试")
class TranscodeTaskCreateDTOJsonTest {

    private final JsonMapper mapper = new JsonMapper();

    @Test
    @DisplayName("sourceDirectoryTranscode=true 应正确反序列化为 true")
    void shouldDeserializeSourceDirectoryTranscodeTrue() {
        String json = """
            {
              "sourceFilePath": "/test/file.flv",
              "targetFormat": "MP3",
              "sourceEngineId": 1,
              "sourceDirectoryTranscode": true
            }
            """;

        TranscodeTaskCreateDTO dto = mapper.readValue(json, TranscodeTaskCreateDTO.class);

        assertNotNull(dto.getSourceDirectoryTranscode(), "字段不应为 null");
        assertTrue(dto.getSourceDirectoryTranscode(), "true 必须反序列化为 true");
    }

    @Test
    @DisplayName("sourceDirectoryTranscode=false 应正确反序列化为 false")
    void shouldDeserializeSourceDirectoryTranscodeFalse() {
        String json = """
            {
              "sourceFilePath": "/test/file.flv",
              "targetFilePath": "/output/file.mp3",
              "targetFormat": "MP3",
              "sourceEngineId": 1,
              "targetEngineId": 2,
              "sourceDirectoryTranscode": false
            }
            """;

        TranscodeTaskCreateDTO dto = mapper.readValue(json, TranscodeTaskCreateDTO.class);

        assertNotNull(dto.getSourceDirectoryTranscode());
        assertFalse(dto.getSourceDirectoryTranscode());
    }

    @Test
    @DisplayName("缺失 sourceDirectoryTranscode 字段时使用默认值 false")
    void shouldDefaultToFalseWhenFieldMissing() {
        String json = """
            {
              "sourceFilePath": "/test/file.flv",
              "targetFilePath": "/output/file.mp3",
              "targetFormat": "MP3"
            }
            """;

        TranscodeTaskCreateDTO dto = mapper.readValue(json, TranscodeTaskCreateDTO.class);

        // 字段默认值 false（Lombok 初始化器）
        assertFalse(Boolean.TRUE.equals(dto.getSourceDirectoryTranscode()),
            "字段缺失时默认值应为 false");
    }

    @Test
    @DisplayName("历史字段名 sameDirectoryTranscode 应被 @JsonAlias 兼容映射")
    void shouldAcceptLegacyFieldNameViaAlias() {
        String json = """
            {
              "sourceFilePath": "/test/file.flv",
              "targetFormat": "MP3",
              "sourceEngineId": 1,
              "sameDirectoryTranscode": true
            }
            """;

        TranscodeTaskCreateDTO dto = mapper.readValue(json, TranscodeTaskCreateDTO.class);

        assertNotNull(dto.getSourceDirectoryTranscode());
        assertTrue(dto.getSourceDirectoryTranscode(),
            "@JsonAlias 应将旧字段名 sameDirectoryTranscode 映射到 sourceDirectoryTranscode");
    }
}
