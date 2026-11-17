package com.structengine.platform.server;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 服务器 secret 生成与哈希。数据库只保存 SHA-256，明文只在创建/轮换时返回一次。
 */
public final class ServerSecrets {

    private ServerSecrets() {
    }

    public static String newSecret() {
        return UUID.randomUUID().toString();
    }

    public static String hash(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("secret 不能为空");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(secret.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    public static boolean matches(String secret, String expectedHash) {
        if (secret == null || secret.isBlank() || expectedHash == null) {
            return false;
        }
        byte[] actual = hash(secret).getBytes(StandardCharsets.US_ASCII);
        byte[] expected = expectedHash.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(actual, expected);
    }
}
