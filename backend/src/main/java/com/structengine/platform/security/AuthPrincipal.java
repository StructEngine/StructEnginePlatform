package com.structengine.platform.security;

import com.structengine.platform.account.AccountType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;

/**
 * JWT 认证后的当前登录主体。
 */
public record AuthPrincipal(
        AccountType type,
        long id,
        String username,
        String role,
        int tokenVersion
) {

    public Collection<? extends GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    public boolean admin() {
        return type == AccountType.ADMIN;
    }
}
