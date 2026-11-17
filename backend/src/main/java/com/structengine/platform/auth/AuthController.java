package com.structengine.platform.auth;

import com.structengine.platform.captcha.CaptchaChallenge;
import com.structengine.platform.captcha.CaptchaService;
import com.structengine.platform.common.ApiResponse;
import com.structengine.platform.common.BusinessException;
import com.structengine.platform.common.ErrorCode;
import com.structengine.platform.security.AuthPrincipal;
import com.structengine.platform.security.LoginRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户/管理员统一认证接口。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;
    private final LoginRateLimiter rateLimiter;

    public AuthController(AuthService authService, CaptchaService captchaService,
                          LoginRateLimiter rateLimiter) {
        this.authService = authService;
        this.captchaService = captchaService;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/captcha")
    public ApiResponse<CaptchaChallenge> captcha() {
        return ApiResponse.ok(captchaService.generate());
    }

    @PostMapping("/register")
    public ApiResponse<AuthTokenResponse> register(@Valid @RequestBody RegisterRequest request,
                                                   HttpServletRequest httpRequest) {
        checkRate("register", request.username(), httpRequest, 5);
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request,
                                                HttpServletRequest httpRequest) {
        checkRate("login", request.username(), httpRequest, 10);
        return ApiResponse.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<AccountView> me(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(authService.me(principal));
    }

    @PostMapping("/password")
    public ApiResponse<AuthTokenResponse> changePassword(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        return ApiResponse.ok(authService.changePassword(principal, request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.ok(null);
    }

    private void checkRate(String action, String username, HttpServletRequest request, int max) {
        String ip = clientIp(request);
        String key = action + ":" + ip + ":" + (username == null ? "" : username.toLowerCase());
        if (!rateLimiter.allow(key, max, 60_000L)) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "尝试过于频繁，请稍后再试");
        }
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
