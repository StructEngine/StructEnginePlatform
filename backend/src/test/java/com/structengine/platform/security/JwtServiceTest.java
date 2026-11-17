package com.structengine.platform.security;

import com.structengine.platform.account.AccountType;
import com.structengine.platform.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import javax.crypto.SecretKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    private static AppProperties props(String secret) {
        AppProperties props = new AppProperties();
        props.setJwtSecret(secret);
        props.setJwtTtlHours(2);
        return props;
    }

    private static JwtService service(AppProperties props) {
        JwtConfig config = new JwtConfig();
        SecretKey key = config.jwtSecretKey(props);
        JwtEncoder encoder = config.jwtEncoder(key);
        JwtDecoder decoder = config.jwtDecoder(key);
        return new JwtService(encoder, decoder, props);
    }

    @Test
    void issuesAndParsesClaims() {
        JwtService service = service(props("0123456789abcdef0123456789abcdef"));
        String token = service.issue(AccountType.USER, 42L, "Steve", "USER", 3);
        JwtClaims claims = service.parse(token);

        assertEquals(AccountType.USER, claims.type());
        assertEquals(42L, claims.id());
        assertEquals("Steve", claims.username());
        assertEquals("USER", claims.role());
        assertEquals(3, claims.tokenVersion());
        assertEquals(7200L, service.ttlSeconds());
    }

    @Test
    void rejectsShortSecret() {
        assertThrows(IllegalStateException.class,
                () -> new JwtConfig().jwtSecretKey(props("too-short")));
    }

    @Test
    void rejectsTamperedToken() {
        JwtService service = service(props("0123456789abcdef0123456789abcdef"));
        String token = service.issue(AccountType.ADMIN, 1L, "admin", "ADMIN", 0);
        String tampered = token.substring(0, token.length() - 2) + "xx";
        assertThrows(RuntimeException.class, () -> service.parse(tampered));
    }
}
