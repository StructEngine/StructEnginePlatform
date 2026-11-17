package com.structengine.platform.server;

/**
 * 用户拥有的 Minecraft 服务器。secret 只保存 SHA-256。
 */
public record MinecraftServer(
        long id,
        long ownerUserId,
        String name,
        String secretHash,
        String status,
        String blueMapUrl,
        String mapId,
        Long lastSeenAt,
        Long registeredAt,
        Long secretRotatedAt,
        Long deletedAt,
        long createdAt,
        long updatedAt
) {

    public boolean deleted() {
        return deletedAt != null;
    }

    public boolean disabled() {
        return "DISABLED".equals(status);
    }
}
