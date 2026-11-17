package com.structengine.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 基础安全响应头。CSP 允许 BlueMap iframe 与 data: 验证码图片。
 */
@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private static final String CSP = String.join("; ",
            "default-src 'self'",
            "img-src 'self' data: blob:",
            "style-src 'self' 'unsafe-inline'",
            "script-src 'self' 'unsafe-inline'",
            "font-src 'self' data:",
            "connect-src 'self' ws: wss:",
            "frame-src *",
            "frame-ancestors 'self'",
            "base-uri 'self'",
            "object-src 'none'");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "SAMEORIGIN");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        response.setHeader("Content-Security-Policy", CSP);
        filterChain.doFilter(request, response);
    }
}
