package top.lldwb.alistmediasync.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import top.lldwb.alistmediasync.interceptor.AuthInterceptor;

/**
 * Web MVC 配置
 * <p>
 * 注册认证拦截器到拦截器链，配置 CORS 跨域策略，
 * 以及前端静态资源映射。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebMvcConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
            .addPathPatterns("/api/**")
            .excludePathPatterns("/api/webhooks/**", "/actuator/health", "/h2-console/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 开发阶段允许所有来源跨域
        registry.addMapping("/api/**")
            .allowedOrigins("*")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 前端 SPA 静态资源映射（Vite 构建产物输出到 classpath:/static/app/）
        registry.addResourceHandler("/app/**")
            .addResourceLocations("classpath:/static/app/");
    }
}
