package com.structengine.platform.game;

import com.structengine.platform.game.ws.GameServerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端验证：真实 Spring WebSocket 端点 + 模拟插件 + REST 构建请求。
 *
 * <p>覆盖 QUERY_Y → RESPONSE_Y → Mock 模型生成 → 静态 URL 分发 → BUILD 的完整链路。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "structengine.ai-api-url=http://localhost:1",
        "structengine.ai-api-key=test",
        "structengine.ai-model=test-model",
        "structengine.game-token=secret-token"
})
class GameWebSocketIntegrationTest {

    private static final Path DATA_DIR;

    static {
        try {
            DATA_DIR = Files.createTempDirectory("structengine-it-");
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void dataDir(DynamicPropertyRegistry registry) {
        registry.add("structengine.data-dir", DATA_DIR::toString);
    }

    @LocalServerPort
    int port;

    @Autowired
    Environment environment;

    @Autowired
    GameServerRegistry gameServerRegistry;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void fullQueryGenerateAndBuildFlow() throws Exception {
        assertEquals(Boolean.TRUE, environment.getProperty("spring.threads.virtual.enabled", Boolean.class));

        PluginListener listener = new PluginListener(mapper);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        WebSocket webSocket = client.newWebSocketBuilder()
                .header("X-StructEngine-Token", "secret-token")
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(URI.create("ws://localhost:" + port + "/ws/game?serverId=it"), listener)
                .get(5, TimeUnit.SECONDS);

        listener.connected.get(5, TimeUnit.SECONDS);
        assertTrue(gameServerRegistry.isConnected("it"));

        String requestBody = mapper.writeValueAsString(new java.util.LinkedHashMap<String, Object>() {{
            put("playerName", "Steve");
            put("x", 12);
            put("z", -8);
            put("prompt", "红石城堡");
            put("serverId", "it");
        }});
        HttpRequest buildRequest = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/map/build"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(buildRequest, HttpResponse.BodyHandlers.ofString());
        JsonNode payload = mapper.readTree(response.body());
        assertEquals(200, payload.get("code").asInt(), response.body());

        JsonNode data = payload.get("data");
        assertEquals(70, data.get("y").asInt());
        assertEquals(12, data.get("x").asInt());
        assertEquals(-8, data.get("z").asInt());
        String modelUrl = data.get("modelUrl").asText();
        assertTrue(modelUrl.startsWith("http://localhost:" + port + "/models/"));

        JsonNode buildMessage = listener.builds.poll(5, TimeUnit.SECONDS);
        assertNotNull(buildMessage, "插件应收到 BUILD 指令");
        assertEquals("BUILD", buildMessage.get("action").asText());
        assertEquals(data.get("taskId").asText(), buildMessage.get("taskId").asText());
        assertEquals(70, buildMessage.get("y").asInt());
        assertEquals(modelUrl, buildMessage.get("modelUrl").asText());

        HttpResponse<String> modelResponse = client.send(
                HttpRequest.newBuilder(URI.create(modelUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, modelResponse.statusCode());
        assertTrue(modelResponse.body().startsWith("[["));
        assertTrue(modelResponse.body().contains("minecraft:"));

        Path modelFile = DATA_DIR.resolve("models").resolve(data.get("taskId").asText() + ".json");
        assertTrue(Files.exists(modelFile), "模型应落盘到 " + modelFile);
        assertEquals(modelResponse.body(), Files.readString(modelFile));
        assertEquals(0, gameServerRegistry.pendingCount());

        HttpResponse<String> serversResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/map/servers"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertTrue(serversResponse.body().contains("\"serverId\":\"it\""), serversResponse.body());

        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
    }

    @Test
    void rejectsHandshakeWithoutToken() {
        HttpClient client = HttpClient.newHttpClient();
        ExecutionException error = assertThrows(ExecutionException.class, () -> client.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(URI.create("ws://localhost:" + port + "/ws/game?serverId=bad"), new PluginListener(mapper))
                .get(5, TimeUnit.SECONDS));
        assertTrue(error.getCause() != null);
    }

    @Test
    void rejectsHandshakeWithInvalidServerId() {
        HttpClient client = HttpClient.newHttpClient();
        assertThrows(ExecutionException.class, () -> client.newWebSocketBuilder()
                .header("X-StructEngine-Token", "secret-token")
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(URI.create("ws://localhost:" + port + "/ws/game?serverId=bad%24id"), new PluginListener(mapper))
                .get(5, TimeUnit.SECONDS));
    }

    private static final class PluginListener implements WebSocket.Listener {

        private final ObjectMapper mapper;
        private final StringBuilder textBuffer = new StringBuilder();
        final CompletableFuture<Void> connected = new CompletableFuture<>();
        final BlockingQueue<JsonNode> builds = new LinkedBlockingQueue<>();

        private PluginListener(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            textBuffer.append(data);
            if (last) {
                String text = textBuffer.toString();
                textBuffer.setLength(0);
                handle(text, webSocket);
            }
            webSocket.request(1);
            return null;
        }

        private void handle(String text, WebSocket webSocket) {
            try {
                JsonNode message = mapper.readTree(text);
                String action = message.get("action").asText();
                if ("CONNECTED".equals(action)) {
                    connected.complete(null);
                } else if ("QUERY_Y".equals(action)) {
                    String taskId = message.get("taskId").asText();
                    String response = "{\"action\":\"RESPONSE_Y\",\"taskId\":\"" + taskId + "\",\"y\":70}";
                    webSocket.sendText(response, true);
                } else if ("BUILD".equals(action)) {
                    builds.add(message);
                }
            } catch (RuntimeException e) {
                connected.completeExceptionally(e);
            }
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            connected.completeExceptionally(error);
        }
    }
}
