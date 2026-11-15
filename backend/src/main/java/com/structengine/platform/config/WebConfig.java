package com.structengine.platform.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * 静态资源与 SPA 回退装配：{@code /**} 指向固定 dist 目录，由 SpaResourceResolver 兜底。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    public static final Path DIST_DIR = Path.of("./dist").toAbsolutePath().normalize();

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations(DIST_DIR.toUri().toString().replaceAll("/?$", "/"))
                .resourceChain(true)
                .addResolver(new SpaResourceResolver());
    }
}