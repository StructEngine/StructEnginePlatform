package com.structengine.platform.game.ws;

import com.structengine.platform.game.protocol.GameMessage;
import com.structengine.platform.game.support.FakeGameServerChannel;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameServerRegistryTest {

    @Test
    void registersSendsAndListsServers() {
        GameServerRegistry registry = new GameServerRegistry();
        FakeGameServerChannel survival = new FakeGameServerChannel("survival");
        registry.register(survival);

        assertTrue(registry.isConnected("survival"));
        assertEquals(java.util.List.of("survival"), registry.serverIds());
        assertTrue(registry.send("survival", GameMessage.ping()));
        assertEquals(GameMessage.ping().action(), survival.lastSent().action());

        registry.unregister("survival", survival);
        assertFalse(registry.isConnected("survival"));
        assertFalse(registry.send("survival", GameMessage.ping()));
    }

    @Test
    void completesAndRemovesHeightFuture() {
        GameServerRegistry registry = new GameServerRegistry();
        CompletableFuture<Integer> future = registry.expectHeight("t-1", "survival");
        assertEquals(1, registry.pendingCount());

        assertTrue(registry.completeHeight("t-1", 72));
        assertEquals(72, future.join());
        assertFalse(registry.completeHeight("unknown", 1));

        registry.removePending("t-1");
        assertEquals(0, registry.pendingCount());
    }

    @Test
    void disconnectFailsPendingQueriesImmediately() {
        GameServerRegistry registry = new GameServerRegistry();
        FakeGameServerChannel channel = new FakeGameServerChannel("survival");
        registry.register(channel);
        CompletableFuture<Integer> future = registry.expectHeight("t-1", "survival");

        registry.unregister("survival", channel);

        assertTrue(future.isCompletedExceptionally());
        assertTrue(channel.lastSent() == null);
    }

    @Test
    void staleSessionCloseDoesNotRemoveNewSession() {
        GameServerRegistry registry = new GameServerRegistry();
        FakeGameServerChannel oldChannel = new FakeGameServerChannel("survival");
        FakeGameServerChannel newChannel = new FakeGameServerChannel("survival");
        registry.register(oldChannel);
        registry.register(newChannel);

        assertFalse(registry.unregister("survival", oldChannel));
        assertTrue(registry.isConnected("survival"));
        assertTrue(registry.unregister("survival", newChannel));
    }

    @Test
    void duplicateTaskIdIsRejected() {
        GameServerRegistry registry = new GameServerRegistry();
        registry.expectHeight("t-1", "survival");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> registry.expectHeight("t-1", "survival"));
    }
}
