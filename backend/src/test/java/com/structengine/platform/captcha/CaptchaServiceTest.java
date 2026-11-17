package com.structengine.platform.captcha;

import com.structengine.platform.config.AppProperties;
import com.structengine.platform.id.SnowflakeIdGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptchaServiceTest {

    private CaptchaService service() {
        AppProperties props = new AppProperties();
        props.setSnowflakeWorkerId(1);
        return new CaptchaService(new SnowflakeIdGenerator(props));
    }

    @Test
    void generatesPngChallenge() {
        CaptchaChallenge challenge = service().generate();

        assertNotNull(challenge.captchaId());
        assertTrue(challenge.image().startsWith("data:image/png;base64,"));
        assertTrue(challenge.image().length() > 100);
        assertEquals(300, challenge.expiresIn());
    }

    @Test
    void wrongCodeIsRejectedAndConsumed() {
        CaptchaService service = service();
        CaptchaChallenge challenge = service.generate();

        assertFalse(service.verify(challenge.captchaId(), "ZZZZ"));
        // 一次性：错误尝试后同一个 captchaId 也不能再用
        assertFalse(service.verify(challenge.captchaId(), "ZZZZ"));
    }

    @Test
    void missingValuesAreRejected() {
        CaptchaService service = service();
        assertFalse(service.verify(null, "ABCD"));
        assertFalse(service.verify("id", null));
        assertFalse(service.verify("id", ""));
    }
}
