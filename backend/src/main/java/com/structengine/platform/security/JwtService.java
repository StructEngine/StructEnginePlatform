package com.structengine.platform.security;

import com.structengine.platform.account.AccountType;
import com.structengine.platform.config.AppProperties;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * JWT 签发与解析。
 */
@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final long ttlSeconds;

    public JwtService(JwtEncoder encoder, JwtDecoder decoder, AppProperties props) {
        this.encoder = encoder;
        this.decoder = decoder;
        this.ttlSeconds = props.getJwtTtlHours() * 3600L;
    }

    public String issue(AccountType type, long id, String username, String role, int tokenVersion) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(Long.toString(id))
                .issuedAt(now)
                .expiresAt(now.plus(ttlSeconds, ChronoUnit.SECONDS))
                .claim("type", type.name())
                .claim("username", username)
                .claim("role", role)
                .claim("ver", tokenVersion)
                .build();
        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    public JwtClaims parse(String token) {
        Jwt jwt = decoder.decode(token);
        String subject = jwt.getSubject();
        if (subject == null) {
            throw new IllegalArgumentException("JWT 缺少 subject");
        }
        String type = jwt.getClaimAsString("type");
        String username = jwt.getClaimAsString("username");
        String role = jwt.getClaimAsString("role");
        Number version = jwt.getClaim("ver");
        if (type == null || username == null || role == null || version == null) {
            throw new IllegalArgumentException("JWT 缺少必要 claims");
        }
        return new JwtClaims(AccountType.valueOf(type), Long.parseLong(subject),
                username, role, version.intValue());
    }

    public long ttlSeconds() {
        return ttlSeconds;
    }
}
