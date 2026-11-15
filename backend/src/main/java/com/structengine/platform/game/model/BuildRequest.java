package com.structengine.platform.game.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 前端地图选点提交的构建请求。
 */
public record BuildRequest(
        @NotBlank(message = "玩家名不能为空")
        @Pattern(regexp = "[A-Za-z0-9_]{3,16}", message = "玩家名需为 3-16 位字母、数字或下划线")
        String playerName,

        @NotNull(message = "X 坐标不能为空")
        @Min(value = -30_000_000, message = "X 坐标超出 Minecraft 世界边界")
        @Max(value = 30_000_000, message = "X 坐标超出 Minecraft 世界边界")
        Integer x,

        @NotNull(message = "Z 坐标不能为空")
        @Min(value = -30_000_000, message = "Z 坐标超出 Minecraft 世界边界")
        @Max(value = 30_000_000, message = "Z 坐标超出 Minecraft 世界边界")
        Integer z,

        @NotBlank(message = "提示词不能为空")
        @Size(max = 1000, message = "提示词不能超过 1000 字符")
        String prompt,

        @Size(max = 64, message = "serverId 不能超过 64 字符")
        String serverId
) {
}
