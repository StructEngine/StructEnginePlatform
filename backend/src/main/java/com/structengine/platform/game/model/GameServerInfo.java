package com.structengine.platform.game.model;

import com.structengine.platform.game.protocol.PlayerInfo;

import java.util.List;

/**
 * 已连接游戏服的精简信息，供 Web 选择服务器、加载 BlueMap 与玩家列表。
 */
public record GameServerInfo(
        String serverId,
        String serverName,
        String blueMapUrl,
        String mapId,
        int playerCount,
        boolean connected,
        List<PlayerInfo> players
) {
}
