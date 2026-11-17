package com.structengine.platform.server;

import com.structengine.platform.game.model.GameServerInfo;

/**
 * 创建服务器响应：secret 只在此处出现一次。
 */
public record ServerCreated(GameServerInfo server, String secret, String wsUrl) {
}
