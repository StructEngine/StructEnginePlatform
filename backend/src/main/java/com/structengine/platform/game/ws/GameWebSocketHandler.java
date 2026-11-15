package com.structengine.platform.game.ws;

import com.structengine.platform.game.protocol.ActionType;
import com.structengine.platform.game.protocol.GameMessage;
import com.structengine.platform.game.protocol.GameMessageCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 游戏服 WebSocket 入口：处理插件回传的 RESPONSE_Y、ERROR、PING。
 *
 * <p>所有发送均走 {@link ConcurrentWebSocketSessionDecorator}，允许 HTTP 处理线程与
 * WebSocket 回调线程并发下发消息。</p>
 */
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    public static final String ATTR_CHANNEL = "structengine.channel";

    private static final Logger log = LoggerFactory.getLogger(GameWebSocketHandler.class);
    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int BUFFER_SIZE_LIMIT = 1024 * 1024;

    private final GameServerRegistry registry;
    private final GameMessageCodec codec;

    public GameWebSocketHandler(GameServerRegistry registry, GameMessageCodec codec) {
        this.registry = registry;
        this.codec = codec;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String serverId = serverId(session);
        WebSocketSession concurrent = new ConcurrentWebSocketSessionDecorator(
                session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT);
        GameServerChannel channel = new WebSocketGameServerChannel(serverId, concurrent, codec);
        session.getAttributes().put(ATTR_CHANNEL, channel);
        registry.register(channel);
        channel.send(GameMessage.connected(serverId));
        log.info("游戏服 {} WebSocket 会话建立 sessionId={}", serverId, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String serverId = serverId(session);
        GameMessage decoded;
        try {
            decoded = codec.decode(message.getPayload());
        } catch (IllegalArgumentException e) {
            log.warn("游戏服 {} 消息解析失败: {}", serverId, e.getMessage());
            return;
        }

        ActionType action = decoded.action();
        if (action == null) {
            log.warn("游戏服 {} 消息缺少 action", serverId);
            return;
        }

        switch (action) {
            case RESPONSE_Y -> handleResponseY(serverId, decoded);
            case ERROR -> handleRemoteError(serverId, decoded);
            case PING -> send(serverId, GameMessage.pong());
            case CONNECTED, PONG -> {
                // 连接确认/心跳回执，无需处理。
            }
            default -> log.debug("游戏服 {} 发送了 Platform 侧不处理的消息: {}", serverId, action);
        }
    }

    private void handleResponseY(String serverId, GameMessage message) {
        if (message.taskId() == null || message.y() == null) {
            log.warn("游戏服 {} 的 RESPONSE_Y 缺少 taskId 或 y", serverId);
            return;
        }
        boolean completed = registry.completeHeight(message.taskId(), message.y());
        if (completed) {
            log.info("游戏服 {} 返回高度 taskId={} y={}", serverId, message.taskId(), message.y());
        } else {
            log.warn("游戏服 {} 返回了未知/已超时的 taskId={}", serverId, message.taskId());
        }
    }

    private void handleRemoteError(String serverId, GameMessage message) {
        String reason = message.message() == null ? "插件端执行失败" : message.message();
        if (message.taskId() != null) {
            registry.failHeight(message.taskId(), new IllegalStateException(reason));
        }
        log.warn("游戏服 {} 报告错误 taskId={}: {}", serverId, message.taskId(), reason);
    }

    private void send(String serverId, GameMessage message) {
        if (!registry.send(serverId, message)) {
            log.debug("游戏服 {} 已断开，{} 发送失败", serverId, message.action());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        GameServerChannel channel = (GameServerChannel) session.getAttributes().get(ATTR_CHANNEL);
        if (channel == null) {
            return;
        }
        registry.unregister(channel.serverId(), channel);
        log.info("游戏服 {} WebSocket 会话关闭 status={}", channel.serverId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        GameServerChannel channel = (GameServerChannel) session.getAttributes().get(ATTR_CHANNEL);
        if (channel != null) {
            log.warn("游戏服 {} 传输异常: {}", channel.serverId(), exception.getMessage());
            registry.unregister(channel.serverId(), channel);
        }
        try {
            session.close(CloseStatus.SERVER_ERROR);
        } catch (Exception ignored) {
            // 已关闭
        }
    }

    private static String serverId(WebSocketSession session) {
        Object value = session.getAttributes().get(GameHandshakeInterceptor.ATTR_SERVER_ID);
        return value == null ? "unknown" : value.toString();
    }
}
