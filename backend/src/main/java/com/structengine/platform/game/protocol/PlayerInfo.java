package com.structengine.platform.game.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 游戏服上报的在线玩家快照。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlayerInfo(
        String uuid,
        String name,
        String world,
        double x,
        double y,
        double z,
        boolean online,
        Long lastSeen
) {
}
