package com.structengine.platform.game.model;

import com.structengine.platform.game.protocol.PlayerInfo;

import java.util.List;

/**
 * 服务器管理/地图工作台共用的服务器视图。
 */
public record GameServerInfo(
        String serverId,
        String serverName,
        String blueMapUrl,
        String mapId,
        String status,
        String ownerUserId,
        int playerCount,
        boolean connected,
        List<PlayerInfo> players,
        Long lastSeenAt,
        Long registeredAt,
        Long createdAt
) {
}
