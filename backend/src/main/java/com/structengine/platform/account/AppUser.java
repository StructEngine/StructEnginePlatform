package com.structengine.platform.account;

/**
 * Minecraft 服主/普通用户账号。
 */
public record AppUser(
        long id,
        String username,
        String passwordHash,
        String status,
        int tokenVersion,
        long createdAt,
        long updatedAt,
        Long lastLoginAt
) {

    public boolean active() {
        return "ACTIVE".equals(status);
    }
}
