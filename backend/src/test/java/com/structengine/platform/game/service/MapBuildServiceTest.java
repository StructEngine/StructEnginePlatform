package com.structengine.platform.game.service;

import com.structengine.platform.common.BusinessException;
import com.structengine.platform.common.ErrorCode;
import com.structengine.platform.config.AppProperties;
import com.structengine.platform.game.model.BuildRequest;
import com.structengine.platform.game.model.BuildResult;
import com.structengine.platform.game.protocol.ActionType;
import com.structengine.platform.game.support.FakeGameServerChannel;
import com.structengine.platform.game.ws.GameServerRegistry;
import com.structengine.platform.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapBuildServiceTest {

    @TempDir
    Path tempDir;

    private AppProperties props;
    private GameServerRegistry registry;
    private FakeGameServerChannel channel;
    private MapBuildService service;

    @BeforeEach
    void setUp() {
        props = new AppProperties();
        props.setDataDir(tempDir.toString());
        props.setGameRpcTimeoutSeconds(1);

        registry = new GameServerRegistry();
        channel = new FakeGameServerChannel("survival");
        channel.onSend(message -> {
            if (message.action() == ActionType.QUERY_Y) {
                registry.completeHeight(message.taskId(), 70);
            }
        });
        registry.register(channel);

        service = new MapBuildService(
                registry,
                new HeightRpcService(registry, props),
                new MockStructureGenerator(),
                new FileStorageService(props)
        );
    }

    @Test
    void queriesHeightGeneratesModelPersistsAndDispatchesBuild() throws Exception {
        BuildResult result = service.build(request("Steve", 10, -20, "小木屋", "survival"),
                "http://platform.test/");

        assertEquals(70, result.y());
        assertEquals("survival", result.serverId());
        assertEquals("http://platform.test/models/" + result.taskId() + ".json", result.modelUrl());
        assertTrue(result.modelUrl().contains(result.taskId()));

        Path modelFile = tempDir.resolve("models").resolve(result.taskId() + ".json");
        assertTrue(Files.exists(modelFile), "模型必须落盘到绝对 data 目录");
        assertTrue(Files.readString(modelFile).startsWith("[["));

        assertEquals(ActionType.BUILD, channel.lastSent().action());
        assertEquals(result.taskId(), channel.lastSent().taskId());
        assertEquals(70, channel.lastSent().y());
        assertEquals(result.modelUrl(), channel.lastSent().modelUrl());
        assertEquals(0, registry.pendingCount());
    }

    @Test
    void autoSelectsSingleConnectedServer() {
        BuildResult result = service.build(request("Steve", 0, 0, "塔", null), "http://x");
        assertEquals("survival", result.serverId());
    }

    @Test
    void rejectsMultipleServersWhenServerIdMissing() {
        registry.register(new FakeGameServerChannel("creative"));
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.build(request("Steve", 0, 0, "塔", null), "http://x"));
        assertEquals(ErrorCode.BAD_REQUEST, error.errorCode());
    }

    @Test
    void rejectsExplicitUnknownServer() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.build(request("Steve", 0, 0, "塔", "unknown"), "http://x"));
        assertEquals(ErrorCode.GAME_SERVER_UNAVAILABLE, error.errorCode());
    }

    @Test
    void rejectsWhenNoServerConnected() {
        registry.unregister("survival", channel);
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.build(request("Steve", 0, 0, "塔", null), "http://x"));
        assertEquals(ErrorCode.GAME_SERVER_UNAVAILABLE, error.errorCode());
    }

    @Test
    void validatesPlayerPromptAndCoordinates() {
        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(BusinessException.class,
                () -> service.build(request("a b", 0, 0, "塔", "survival"), "http://x")).errorCode());
        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(BusinessException.class,
                () -> service.build(request("Steve", 0, 0, "  ", "survival"), "http://x")).errorCode());
        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(BusinessException.class,
                () -> service.build(request("Steve", 30_000_001, 0, "塔", "survival"), "http://x")).errorCode());
        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(BusinessException.class,
                () -> service.build(request("Steve", 0, 0, "x".repeat(1001), "survival"), "http://x")).errorCode());
    }

    private static BuildRequest request(String player, int x, int z, String prompt, String serverId) {
        return new BuildRequest(player, x, z, prompt, serverId);
    }
}
