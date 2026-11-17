package com.structengine.platform.security;

import com.structengine.platform.account.AccountType;

/**
 * JWT 中承载的最小身份信息。
 */
public record JwtClaims(
        AccountType type,
        long id,
        String username,
        String role,
        int tokenVersion
) {
}
