package com.structengine.platform.game.protocol;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * GameMessage 的 JSON 编解码器，独立于 WebSocket 容器，便于单元测试。
 */
@Component
public class GameMessageCodec {

    private final ObjectMapper mapper = new ObjectMapper();

    public String encode(GameMessage message) {
        try {
            return mapper.writeValueAsString(message);
        } catch (Exception e) {
            throw new IllegalStateException("游戏服消息序列化失败", e);
        }
    }

    public GameMessage decode(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("消息体为空");
        }
        try {
            return mapper.readValue(payload, GameMessage.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("游戏服消息解析失败: " + e.getMessage(), e);
        }
    }
}
