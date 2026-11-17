package com.structengine.platform.chat;

public record ChatMessage(
        long id,
        long serverId,
        String sessionId,
        String actorType,
        long actorId,
        String role,
        String content,
        long createdAt
) {
}
