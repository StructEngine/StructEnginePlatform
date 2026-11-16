package com.structengine.platform.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 单管理员会话鉴权。模型文件和游戏服 WebSocket 不走此拦截器。
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    public static final String SESSION_KEY = "structengine.admin";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String path = request.getRequestURI();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || isPublic(path)) {
            return true;
        }
        Object admin = request.getSession(false) == null
                ? null : request.getSession(false).getAttribute(SESSION_KEY);
        if (Boolean.TRUE.equals(admin)) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":401,\"message\":\"请先登录\"}");
        return false;
    }

    private static boolean isPublic(String path) {
        return path.startsWith("/api/auth/")
                || path.startsWith("/models/")
                || path.startsWith("/ws/")
                || path.equals("/")
                || path.startsWith("/assets/")
                || path.startsWith("/favicon");
    }
}
