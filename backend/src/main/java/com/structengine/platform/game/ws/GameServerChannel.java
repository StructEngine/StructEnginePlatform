package com.structengine.platform.game.ws;

import com.structengine.platform.game.protocol.GameMessage;

/**
 * 游戏服连接句柄：屏蔽 Spring WebSocketSession 细节，便于注册表与单元测试。
 */
public interface GameServerChannel {

    String serverId();

    boolean send(GameMessage message);

    boolean isOpen();

    void close(String reason);
}
