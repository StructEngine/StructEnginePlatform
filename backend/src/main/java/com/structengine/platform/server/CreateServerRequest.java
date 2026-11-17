package com.structengine.platform.server;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateServerRequest(
        @NotBlank(message = "服务器名称不能为空")
        @Size(max = 64, message = "服务器名称不能超过 64 个字符")
        String name
) {
}
