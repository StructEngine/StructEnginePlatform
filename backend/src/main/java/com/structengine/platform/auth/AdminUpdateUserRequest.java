package com.structengine.platform.auth;

import jakarta.validation.constraints.Pattern;

public record AdminUpdateUserRequest(
        @Pattern(regexp = "^[A-Za-z]{4,16}$", message = "用户名必须为 4~16 位英文字母")
        String username,

        @Pattern(regexp = "^(ACTIVE|DISABLED)$", message = "状态只能是 ACTIVE 或 DISABLED")
        String status
) {
}
