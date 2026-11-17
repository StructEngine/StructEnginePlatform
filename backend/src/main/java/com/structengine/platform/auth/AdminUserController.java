package com.structengine.platform.auth;

import com.structengine.platform.common.ApiResponse;
import com.structengine.platform.common.BusinessException;
import com.structengine.platform.common.ErrorCode;
import com.structengine.platform.id.SnowflakeIdGenerator;
import com.structengine.platform.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员用户管理接口。
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AuthService authService;

    public AdminUserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/users")
    public ApiResponse<PageResponse<AdminUserView>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(authService.listUsers(page, size, keyword));
    }

    @PostMapping("/users")
    public ApiResponse<AdminUserView> create(@Valid @RequestBody AdminCreateUserRequest request) {
        return ApiResponse.ok(authService.createUser(request));
    }

    @PutMapping("/users/{userId}")
    public ApiResponse<AdminUserView> update(@PathVariable String userId,
                                             @Valid @RequestBody AdminUpdateUserRequest request) {
        return ApiResponse.ok(authService.updateUser(parseId(userId), request));
    }

    @PutMapping("/users/{userId}/password")
    public ApiResponse<Void> resetPassword(@PathVariable String userId,
                                           @Valid @RequestBody AdminResetPasswordRequest request) {
        authService.resetPassword(parseId(userId), request);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/users/{userId}")
    public ApiResponse<Void> delete(@PathVariable String userId) {
        authService.deleteUser(parseId(userId));
        return ApiResponse.ok(null);
    }

    @PutMapping("/password")
    public ApiResponse<AuthTokenResponse> changeAdminPassword(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        return ApiResponse.ok(authService.changeAdminPassword(principal, request));
    }

    private static long parseId(String value) {
        try {
            return SnowflakeIdGenerator.parse(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户 ID 格式非法");
        }
    }
}
