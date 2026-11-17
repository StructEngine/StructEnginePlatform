package com.structengine.platform.auth;

public record AccountView(
        String id,
        String username,
        String role,
        String status,
        boolean mustChangePassword
) {
}
