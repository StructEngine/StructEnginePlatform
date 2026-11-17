package com.structengine.platform.model;

/**
 * 用户模型产物：来源为地图 AI 或参考图，JSON 链接永久公开。
 */
public record ModelArtifact(
        long id,
        long ownerUserId,
        long serverId,
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
        Long deletedAt
) {

    public boolean deleted() {
        return deletedAt != null;
    }
}
