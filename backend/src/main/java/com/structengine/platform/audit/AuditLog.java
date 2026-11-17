package com.structengine.platform.audit;

public record AuditLog(
        long id,
        String actorType,
        Long actorId,
        String actorName,
        String action,
        Long serverId,
        Long modelId,
        Long taskId,
        Integer x,
        Integer y,
        Integer z,
        String ip,
        String result,
        String detail,
        long createdAt
) {
}
