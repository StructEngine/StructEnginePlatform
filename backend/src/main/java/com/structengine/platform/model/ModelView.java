package com.structengine.platform.model;

public record ModelView(
        String modelId,
        String source,
        String prompt,
        String modelUrl,
        int blockCount,
        int minX,
        int maxX,
        int minY,
        int maxY,
        int minZ,
        int maxZ,
        long createdAt,
        String serverId,
        String ownerUserId
) {
}
