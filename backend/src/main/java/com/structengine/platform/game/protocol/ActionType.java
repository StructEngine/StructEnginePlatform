package com.structengine.platform.game.protocol;

/**
 * Platform 与游戏服插件之间的 WebSocket 消息类型（协议 v2）。
 *
 * <p>QUERY_Y / RESPONSE_Y / BUILD 保持旧协议兼容；新增 HELLO、PLAYER_LIST、
 * BUILD_STATUS、HISTORY_*、ROLLBACK、REDO 支撑 BlueMap 页面与 Web 操作。</p>
 */
public enum ActionType {
    HELLO,
    PLAYER_LIST,
    QUERY_Y,
    RESPONSE_Y,
    BUILD,
    BUILD_STATUS,
    HISTORY_REQUEST,
    HISTORY_RESPONSE,
    ROLLBACK,
    REDO,
    CONNECTED,
    PING,
    PONG,
    ERROR
}
