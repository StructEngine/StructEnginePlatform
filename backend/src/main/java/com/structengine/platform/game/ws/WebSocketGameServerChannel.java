package com.structengine.platform.game.ws;

import com.structengine.platform.game.protocol.GameMessage;
import com.structengine.platform.game.protocol.GameMessageCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * 基于 Spring WebSocketSession 的连接实现。
 * <p>会话在 Handler 中已用 {@code ConcurrentWebSocketSessionDecorator} 包装，允许并发发送。</p>
 */
public final class WebSocketGameServerChannel implements GameServerChannel {

    private static final Logger log = LoggerFactory.getLogger(WebSocketGameServerChannel.class);

    private final String serverId;
    private final WebSocketSession session;
    private final GameMessageCodec codec;

    public WebSocketGameServerChannel(String serverId, WebSocketSession session, GameMessageCodec codec) {
        this.serverId = serverId;
        this.session = session;
        this.codec = codec;
    }

    @Override
    public String serverId() {
        return serverId;
    }

    @Override
    public boolean send(GameMessage message) {
        if (!isOpen()) {
            return false;
        }
        try {
            session.sendMessage(new TextMessage(codec.encode(message)));
            return true;
        } catch (Exception e) {
            log.warn("向游戏服 {} 发送 {} 失败: {}", serverId, message.action(), e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isOpen() {
        return session.isOpen();
    }

    @Override
    public void close(String reason) {
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.NORMAL);
            }
        } catch (Exception e) {
            log.debug("关闭游戏服 {} 连接失败: {}", serverId, e.getMessage());
        }
    }
}
