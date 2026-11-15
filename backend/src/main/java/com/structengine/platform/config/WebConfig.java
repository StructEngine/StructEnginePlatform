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

    private final AppProperties props;

    public WebConfig(AppProperties props) {
        this.props = props;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path distDir = props.projectPath().resolve("dist");
        registry.addResourceHandler("/**")
                .addResourceLocations(distDir.toUri().toString().replaceAll("/?$", "/"))
                .resourceChain(true)
                .addResolver(new SpaResourceResolver());
    }
}