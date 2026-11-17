package com.structengine.platform.auth;

public record AuthTokenResponse(
        String token,
        String tokenType,
        long expiresIn,
        AccountView user
) {
}
