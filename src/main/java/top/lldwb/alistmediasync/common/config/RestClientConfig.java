package top.lldwb.alistmediasync.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * RestClient 配置
 * <p>
 * Spring Boot 4.x 下 spring-boot-starter-webmvc 不会自动注册
 * RestClient.Builder Bean，需手动提供。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
