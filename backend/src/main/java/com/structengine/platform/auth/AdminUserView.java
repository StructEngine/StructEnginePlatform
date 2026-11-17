package com.structengine.platform.auth;

public record AdminUserView(
        String id,
        String username,
        String status,
        long createdAt,
        Long lastLoginAt
) {
}
