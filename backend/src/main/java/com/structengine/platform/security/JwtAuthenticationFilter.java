package com.structengine.platform.security;

import com.structengine.platform.account.AccountService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Bearer JWT 认证过滤器。
 *
 * <p>解析 token 后重新从数据库加载账号并校验 token_version/状态，
 * 这样改密或禁用后旧 token 会立即失效。</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AccountService accountService;

    public JwtAuthenticationFilter(JwtService jwtService, AccountService accountService) {
        this.jwtService = jwtService;
        this.accountService = accountService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            String token = authorization.substring(PREFIX.length()).trim();
            if (!token.isEmpty()) {
                authenticate(token, request);
            }
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request) {
        try {
            JwtClaims claims = jwtService.parse(token);
            Optional<AuthPrincipal> loaded = accountService.load(claims.type(), claims.id());
            if (loaded.isEmpty()) {
                return;
            }
            AuthPrincipal principal = loaded.get();
            if (principal.tokenVersion() != claims.tokenVersion()) {
                return;
            }
            UsernamePasswordAuthenticationToken authentication =
                    UsernamePasswordAuthenticationToken.authenticated(
                            principal, null, principal.authorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (RuntimeException e) {
            SecurityContextHolder.clearContext();
        }
    }
}
