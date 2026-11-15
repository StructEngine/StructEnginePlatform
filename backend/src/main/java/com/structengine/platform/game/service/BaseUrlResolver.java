package com.structengine.platform.game.service;

import com.structengine.platform.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 解析模型文件对外基础 URL。
 *
 * <p>优先使用 {@code STRUCTENGINE_PUBLIC_BASE_URL}（反向代理/容器场景），
 * 否则按请求协议与 Host 推导。游戏服必须能访问该地址以下载模型 JSON。</p>
 */
@Component
public class BaseUrlResolver {

    private final AppProperties props;

    public BaseUrlResolver(AppProperties props) {
        this.props = props;
    }

    public String resolve(HttpServletRequest request) {
        if (StringUtils.hasText(props.getPublicBaseUrl())) {
            return trimTrailingSlash(props.getPublicBaseUrl().trim());
        }

        String scheme = firstValue(firstNonBlank(
                request.getHeader("X-Forwarded-Proto"),
                request.getScheme(),
                "http"
        ));
        String host = firstValue(firstNonBlank(
                request.getHeader("X-Forwarded-Host"),
                request.getHeader("Host")
        ));
        if (!StringUtils.hasText(host)) {
            int port = request.getServerPort();
            host = request.getServerName() + (port > 0 ? ":" + port : "");
        }
        return trimTrailingSlash(scheme + "://" + host);
    }

    private static String trimTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    /** 多级代理会以逗号分隔多个值，取最靠近客户端的第一个。 */
    private static String firstValue(String value) {
        if (value == null) {
            return null;
        }
        int comma = value.indexOf(',');
        return (comma >= 0 ? value.substring(0, comma) : value).trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
