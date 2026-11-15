package com.structengine.platform.game.protocol;

/**
 * Platform 与游戏服插件之间的 WebSocket 消息类型。
 *
 * <p>业务主链路：QUERY_Y → RESPONSE_Y → BUILD；PING/PONG 用于插件心跳，
 * ERROR 让插件把主线程查询/下载失败原因回传给 Platform。</p>
 */
public enum ActionType {
    QUERY_Y,
    RESPONSE_Y,
    BUILD,
    CONNECTED,
    PING,
    PONG,
    ERROR
}
