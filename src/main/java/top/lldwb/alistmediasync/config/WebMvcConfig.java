package top.lldwb.alistmediasync.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
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
    public void addViewControllers(ViewControllerRegistry registry) {
        // SPA 入口路由：将 /app/ 请求转发到 index.html
        // Spring Boot 6.x 中目录 Resource.isReadable() 返回 false，
        // 仅靠 ResourceHandler 的 /app/** 无法自动回退到 index.html
        registry.addViewController("/app/").setViewName("forward:/app/index.html");
        registry.addViewController("/app").setViewName("forward:/app/index.html");
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

        // favicon.ico 映射到 app 目录下的 vite.svg
        // 注意：href 应为 "/app/vite.svg"，此处仅处理浏览器根路径回退请求
        registry.addResourceHandler("/favicon.ico")
            .addResourceLocations("classpath:/static/app/vite.svg");

        // Chrome DevTools 探测请求：返回空 JSON 避免 NoResourceFoundException 日志污染
        // Chrome 浏览器会自动请求此路径以检测开发服务器配置
        registry.addResourceHandler("/.well-known/**")
            .addResourceLocations("classpath:/static/.well-known/");
    }
}
