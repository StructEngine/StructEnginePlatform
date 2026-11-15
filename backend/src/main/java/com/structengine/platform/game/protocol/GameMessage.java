package com.structengine.platform.game.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 双向 WebSocket 统一消息体。
 *
 * <p>字段按场景裁剪：QUERY_Y 只带 taskId/playerName/x/z；RESPONSE_Y 带 taskId/y；
 * BUILD 带完整 taskId/playerName/x/y/z/modelUrl。空字段不序列化，减小协议体积。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GameMessage(
        ActionType action,
        String taskId,
        String playerName,
        Integer x,
        Integer y,
        Integer z,
        String modelUrl,
        String message
) {

    public static GameMessage queryY(String taskId, String playerName, int x, int z) {
        return new GameMessage(ActionType.QUERY_Y, taskId, playerName, x, null, z, null, null);
    }

    public static GameMessage responseY(String taskId, int y) {
        return new GameMessage(ActionType.RESPONSE_Y, taskId, null, null, y, null, null, null);
    }

    public static GameMessage build(String taskId, String playerName, int x, int y, int z, String modelUrl) {
        return new GameMessage(ActionType.BUILD, taskId, playerName, x, y, z, modelUrl, null);
    }

    public static GameMessage connected(String serverId) {
        return new GameMessage(ActionType.CONNECTED, null, null, null, null, null, null,
                "game server connected: " + serverId);
    }

    public static GameMessage ping() {
        return new GameMessage(ActionType.PING, null, null, null, null, null, null, null);
    }

    public static GameMessage pong() {
        return new GameMessage(ActionType.PONG, null, null, null, null, null, null, null);
    }

    public static GameMessage error(String taskId, String message) {
        return new GameMessage(ActionType.ERROR, taskId, null, null, null, null, null, message);
    }
}
