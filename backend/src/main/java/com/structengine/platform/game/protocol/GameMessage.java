package com.structengine.platform.game.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Platform 与插件双向统一消息体（协议 v2）。
 *
 * <p>空字段不序列化。新增字段全部保持可选，旧版 BUY/QUERY_Y/BUILD 仍可被旧插件处理。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GameMessage(
        ActionType action,
        Integer protocolVersion,
        String serverId,
        String serverName,
        String taskId,
        String requestId,
        String playerUuid,
        String playerName,
        String world,
        String blueMapUrl,
        String mapId,
        Integer x,
        Integer y,
        Integer z,
        Integer centerX,
        Integer centerZ,
        Integer rotation,
        String modelUrl,
        String modelName,
        String historyId,
        String state,
        Integer placed,
        Integer total,
        Boolean full,
        List<PlayerInfo> players,
        List<HistoryEntry> history,
        Long timestamp,
        String message
) {

    public static final int PROTOCOL_VERSION = 2;

    public static GameMessage hello(String serverId, String serverName, String blueMapUrl, String mapId) {
        return new GameMessage(ActionType.HELLO, PROTOCOL_VERSION, serverId, serverName,
                null, null, null, null, null, blueMapUrl, mapId,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
    }

    public static GameMessage playerList(String serverId, List<PlayerInfo> players, boolean full) {
        return new GameMessage(ActionType.PLAYER_LIST, PROTOCOL_VERSION, serverId, null,
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, full, players, null, System.currentTimeMillis(), null);
    }

    public static GameMessage queryY(String taskId, String playerName, int x, int z) {
        return new GameMessage(ActionType.QUERY_Y, PROTOCOL_VERSION, null, null,
                taskId, null, null, playerName, null, null, null,
                x, null, z, null, null, null, null, null, null,
                null, null, null, null, null, null, System.currentTimeMillis(), null);
    }

    public static GameMessage responseY(String taskId, int y) {
        return new GameMessage(ActionType.RESPONSE_Y, PROTOCOL_VERSION, null, null,
                taskId, null, null, null, null, null, null,
                null, y, null, null, null, null, null, null, null,
                null, null, null, null, null, null, System.currentTimeMillis(), null);
    }

    public static GameMessage build(String taskId, String playerUuid, String playerName,
                                    int x, int y, int z, String modelUrl, String modelName, int rotation) {
        return new GameMessage(ActionType.BUILD, PROTOCOL_VERSION, null, null,
                taskId, null, playerUuid, playerName, null, null, null,
                x, y, z, null, null, rotation, modelUrl, modelName, null,
                "ACCEPTED", null, null, null, null, null, System.currentTimeMillis(), null);
    }

    public static GameMessage buildStatus(String taskId, String playerUuid, String state,
                                          Integer placed, Integer total, String message) {
        return new GameMessage(ActionType.BUILD_STATUS, PROTOCOL_VERSION, null, null,
                taskId, null, playerUuid, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                state, placed, total, null, null, null, System.currentTimeMillis(), message);
    }

    public static GameMessage historyRequest(String requestId, String playerUuid, String playerName) {
        return new GameMessage(ActionType.HISTORY_REQUEST, PROTOCOL_VERSION, null, null,
                null, requestId, playerUuid, playerName, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, System.currentTimeMillis(), null);
    }

    public static GameMessage historyResponse(String requestId, List<HistoryEntry> history) {
        return new GameMessage(ActionType.HISTORY_RESPONSE, PROTOCOL_VERSION, null, null,
                null, requestId, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, history, System.currentTimeMillis(), null);
    }

    public static GameMessage rollback(String requestId, String playerUuid, String playerName, String historyId) {
        return new GameMessage(ActionType.ROLLBACK, PROTOCOL_VERSION, null, null,
                null, requestId, playerUuid, playerName, null, null, null,
                null, null, null, null, null, null, null, null, historyId,
                null, null, null, null, null, null, System.currentTimeMillis(), null);
    }

    public static GameMessage redo(String requestId, String playerUuid, String playerName, String historyId) {
        return new GameMessage(ActionType.REDO, PROTOCOL_VERSION, null, null,
                null, requestId, playerUuid, playerName, null, null, null,
                null, null, null, null, null, null, null, null, historyId,
                null, null, null, null, null, null, System.currentTimeMillis(), null);
    }

    public static GameMessage connected(String serverId) {
        return new GameMessage(ActionType.CONNECTED, PROTOCOL_VERSION, serverId, null,
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, System.currentTimeMillis(),
                "game server connected: " + serverId);
    }

    public static GameMessage ping() {
        return new GameMessage(ActionType.PING, PROTOCOL_VERSION, null, null,
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, System.currentTimeMillis(), null);
    }

    public static GameMessage pong() {
        return new GameMessage(ActionType.PONG, PROTOCOL_VERSION, null, null,
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, System.currentTimeMillis(), null);
    }

    public static GameMessage error(String taskId, String message) {
        return new GameMessage(ActionType.ERROR, PROTOCOL_VERSION, null, null,
                taskId, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, System.currentTimeMillis(), message);
    }
}
