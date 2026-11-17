package com.structengine.platform.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginRateLimiterTest {

    @Test
    void blocksAfterMaxAttempts() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        assertTrue(limiter.allow("ip:user", 2, 60_000));
        assertTrue(limiter.allow("ip:user", 2, 60_000));
        assertFalse(limiter.allow("ip:user", 2, 60_000));
        assertTrue(limiter.allow("other", 2, 60_000));
    }
}
