package com.structengine.platform.server;

/**
 * 轮换 secret 响应：新 secret 只出现一次。
 */
public record ServerSecretRotated(String serverId, String secret, String wsUrl) {
}
