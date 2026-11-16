package com.structengine.platform.auth;

import com.structengine.platform.common.ApiResponse;
import com.structengine.platform.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 单管理员登录接口。
 */
@RestController
@RequestMapping("/api/auth")
public class AdminAuthController {

    private final AppProperties props;

    public AdminAuthController(AppProperties props) {
        this.props = props;
    }

    @PostMapping("/login")
    public ApiResponse<AuthState> login(@RequestBody LoginRequest request, HttpServletRequest http) {
        String expected = props.getAdminPassword();
        if (expected == null || expected.isBlank()
                || request == null || !constantTimeEquals(expected, request.password())) {
            return new ApiResponse<>(401, "密码错误", null);
        }
        HttpSession session = http.getSession(true);
        session.setAttribute(AdminAuthInterceptor.SESSION_KEY, true);
        return ApiResponse.ok(new AuthState(true));
    }

    @PostMapping("/logout")
    public ApiResponse<AuthState> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ApiResponse.ok(new AuthState(false));
    }

    @GetMapping("/status")
    public ApiResponse<AuthState> status(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        boolean authenticated = session != null
                && Boolean.TRUE.equals(session.getAttribute(AdminAuthInterceptor.SESSION_KEY));
        return ApiResponse.ok(new AuthState(authenticated));
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    public record LoginRequest(String password) {
    }

    public record AuthState(boolean authenticated) {
    }
}
