package com.structengine.platform.task;

public record BuildTask(
        long id,
        long ownerUserId,
        long serverId,
        long modelId,
        String playerUuid,
        String playerName,
        int centerX,
        int centerY,
        int centerZ,
        int originX,
        int originY,
        int originZ,
        int rotation,
        String status,
        int placed,
        int total,
        String error,
        long createdAt,
        long updatedAt
) {
}
