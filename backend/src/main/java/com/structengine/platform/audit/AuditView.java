package com.structengine.platform.audit;

public record AuditView(
        String id,
        String actorType,
        String actorName,
        String action,
        String serverId,
        String modelId,
        String taskId,
        Integer x,
        Integer y,
        Integer z,
        String ip,
        String result,
        String detail,
        long createdAt
) {
}
