package com.structengine.platform.captcha;

/**
 * 验证码挑战：id + PNG data URI + 有效期。
 */
public record CaptchaChallenge(String captchaId, String image, int expiresIn) {
}
