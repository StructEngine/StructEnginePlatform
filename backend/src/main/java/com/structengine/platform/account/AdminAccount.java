package com.structengine.platform.account;

/**
 * 超级管理员账号。固定单行，用户名 admin。
 */
public record AdminAccount(
        long id,
        String username,
        String passwordHash,
        int tokenVersion,
        boolean mustChangePassword,
        long createdAt,
        long updatedAt,
        Long lastLoginAt
) {
}
