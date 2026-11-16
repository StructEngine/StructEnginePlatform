package com.structengine.platform.game.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameMessageCodecTest {

    private final GameMessageCodec codec = new GameMessageCodec();

    @Test
    void encodesQueryYWithoutNullFields() {
        String json = codec.encode(GameMessage.queryY("task-1", "Steve", 10, -20));
        assertTrue(json.contains("\"action\":\"QUERY_Y\""));
        assertTrue(json.contains("\"taskId\":\"task-1\""));
        assertTrue(json.contains("\"x\":10"));
        assertTrue(json.contains("\"z\":-20"));
        assertFalse(json.contains("\"y\""));
        assertFalse(json.contains("\"modelUrl\""));
    }

    @Test
    void decodesResponseY() {
        GameMessage message = codec.decode("{\"action\":\"RESPONSE_Y\",\"taskId\":\"t-1\",\"y\":64}");
        assertEquals(ActionType.RESPONSE_Y, message.action());
        assertEquals("t-1", message.taskId());
        assertEquals(64, message.y());
    }

    @Test
    void ignoresUnknownFieldsForForwardCompatibility() {
        GameMessage message = codec.decode(
                "{\"action\":\"BUILD\",\"taskId\":\"t\",\"x\":1,\"y\":2,\"z\":3,\"modelUrl\":\"u\",\"extra\":true}");
        assertEquals(ActionType.BUILD, message.action());
        assertEquals("u", message.modelUrl());
    }

    @Test
    void rejectsMalformedJson() {
        assertThrows(IllegalArgumentException.class, () -> codec.decode("not-json"));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(""));
    }

    @Test
    void buildMessageCarriesFullCoordinatesAndUrl() {
        String json = codec.encode(GameMessage.build("t", "uuid-alex", "Alex", 1, 65, -3, "http://x/m.json", "小屋", 0));
        assertTrue(json.contains("\"playerName\":\"Alex\""));
        assertTrue(json.contains("\"y\":65"));
        assertTrue(json.contains("\"modelUrl\":\"http://x/m.json\""));
    }
}
