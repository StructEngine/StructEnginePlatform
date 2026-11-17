package com.structengine.platform.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Pattern(regexp = "^[A-Za-z]{4,16}$", message = "用户名必须为 4~16 位英文字母")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 16, message = "密码长度必须为 6~16 位")
        String password,

        @NotBlank(message = "验证码 ID 不能为空")
        String captchaId,

        @NotBlank(message = "验证码不能为空")
        String captchaCode
) {
}
