package com.structengine.platform.game.service;

import com.structengine.platform.common.BusinessException;
import com.structengine.platform.common.ErrorCode;
import com.structengine.platform.config.AppProperties;
import com.structengine.platform.game.protocol.ActionType;
import com.structengine.platform.game.support.FakeGameServerChannel;
import com.structengine.platform.game.ws.GameServerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeightRpcServiceTest {

    private AppProperties props;
    private GameServerRegistry registry;
    private HeightRpcService service;
    private FakeGameServerChannel channel;

    @BeforeEach
    void setUp() {
        props = new AppProperties();
        props.setGameRpcTimeoutSeconds(1);
        registry = new GameServerRegistry();
        service = new HeightRpcService(registry, props);
        channel = new FakeGameServerChannel("survival");
        registry.register(channel);
    }

    @Test
    void returnsYFromResponse() {
        channel.onSend(message -> {
            if (message.action() == ActionType.QUERY_Y) {
                registry.completeHeight(message.taskId(), 72);
            }
        });

        int y = service.queryY("survival", "t-1", "Steve", 10, -20);

        assertEquals(72, y);
        assertEquals(ActionType.QUERY_Y, channel.lastSent().action());
        assertEquals(10, channel.lastSent().x());
        assertEquals(-20, channel.lastSent().z());
        assertEquals(0, registry.pendingCount());
    }

    @Test
    void timesOutAndCleansPendingFuture() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.queryY("survival", "t-timeout", "Steve", 0, 0));

        assertEquals(ErrorCode.GAME_SERVER_TIMEOUT, error.errorCode());
        assertTrue(error.message().contains("1 秒"));
        assertEquals(0, registry.pendingCount());
    }

    @Test
    void sendFailureMapsToUnavailable() {
        channel.sendResult(false);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.queryY("survival", "t-2", "Steve", 0, 0));

        assertEquals(ErrorCode.GAME_SERVER_UNAVAILABLE, error.errorCode());
        assertEquals(0, registry.pendingCount());
    }

    @Test
    void disconnectFailsWaitImmediately() {
        channel.onSend(message -> registry.unregister("survival", channel));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.queryY("survival", "t-3", "Steve", 0, 0));

        assertEquals(ErrorCode.GAME_SERVER_UNAVAILABLE, error.errorCode());
        assertTrue(error.message().contains("高度查询失败"));
        assertEquals(0, registry.pendingCount());
    }

    @Test
    void worksWhenCallerRunsOnVirtualThread() throws Exception {
        channel.onSend(message -> registry.completeHeight(message.taskId(), 66));
        int[] result = new int[1];
        Thread thread = Thread.ofVirtual().start(() ->
                result[0] = service.queryY("survival", "t-virtual", "Steve", 1, 2));
        thread.join(2000);

        assertEquals(66, result[0]);
        assertTrue(thread.isVirtual());
    }
}
