package top.lldwb.alistmediasync.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 应用配置属性绑定类
 * <p>
 * 将 application.yaml 中 app.* 命名空间的配置绑定到此类的字段，
 * 支持 Spring Boot Relaxed Binding（可通过环境变量覆盖）。
 * 例如：app.data-dir 对应 DATA_DIR 环境变量。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** 数据库文件存储目录（默认 /app/data，Docker 环境；本地开发建议设置为 ./data） */
    private String dataDir = "/app/data";

    /** 认证配置 */
    @NotNull
    private Auth auth = new Auth();

    /** 转码配置 */
    @NotNull
    private Transcode transcode = new Transcode();

    /** 线程池配置 */
    @NotNull
    private Pool pool = new Pool();

    /** 历史记录保留天数（默认 30 天） */
    @Min(1)
    private int retentionDays = 30;

    /**
     * 服务器地址（用于生成 Webhook 地址等外部 URL）
     * 未配置时使用请求 origin
     * 可通过环境变量 SERVER_ADDRESS 覆盖
     */
    private String serverAddress;

    /**
     * 认证配置内部类
     */
    @Data
    public static class Auth {
        /** 管理后台用户名 */
        @NotEmpty
        private String username = "admin";

        /**
         * BCrypt 哈希后的密码（必须以 {bcrypt} 前缀标识）
         * 默认值对应明文 "admin123"
         */
        @NotEmpty
        private String password = "{bcrypt}$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
    }

    /**
     * 转码配置内部类
     */
    @Data
    public static class Transcode {
        /**
         * 临时文件后缀（默认 .tmp）
         * 若不以点号开头，系统自动补充
         * 可通过环境变量 TRANSCODE_TEMP_SUFFIX 覆盖
         */
        @NotEmpty
        private String tempSuffix = ".tmp";

        /**
         * 临时文件存储目录
         * Docker 场景下建议挂载独立卷
         * 可通过环境变量 TRANSCODE_TEMP_DIR 覆盖
         */
        @NotEmpty
        private String tempDir = System.getProperty("java.io.tmpdir") + "/alist-media-sync/transcode";

        /**
         * 最大并发转码任务数
         * Docker 容器内 availableProcessors() 可能返回宿主机核心数，
         * 因此使用静态默认值 32 作为保守上限。
         * 可通过环境变量 TRANSCODE_MAX_CONCURRENT 覆盖
         */
        @Min(1)
        private int maxConcurrentTranscode = 32;

        /**
         * 默认音频比特率（bps，默认 128000 = 128kbps）
         * 转码任务未指定码率时使用此值
         * 可通过环境变量 TRANSCODE_DEFAULT_BITRATE 覆盖
         */
        @Min(1)
        private int defaultBitrate = 128000;

        /** 临时文件后缀最大长度（超过截断并警告） */
        @Min(1)
        private int maxSuffixLength = 50;
    }

    /**
     * 线程池配置内部类
     */
    @Data
    public static class Pool {
        /** 线程池核心线程数 */
        @Min(1)
        private int coreSize = 8;

        /** 线程池最大线程数 */
        @Min(1)
        private int maxSize = 32;
    }
}
