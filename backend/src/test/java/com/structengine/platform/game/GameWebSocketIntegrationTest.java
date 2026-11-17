package com.structengine.platform.game;

import com.structengine.platform.account.AccountType;
import com.structengine.platform.account.AdminAccount;
import com.structengine.platform.account.AdminAccountRepository;
import com.structengine.platform.account.AppUser;
import com.structengine.platform.account.UserAccountRepository;
import com.structengine.platform.game.ws.GameServerRegistry;
import com.structengine.platform.id.SnowflakeIdGenerator;
import com.structengine.platform.security.JwtService;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 协议 v2 端到端验证：真实 WebSocket、HELLO、玩家列表、管理员会话与历史查询。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "structengine.ai-api-url=http://localhost:1",
        "structengine.ai-api-key=test",
        "structengine.ai-model=test-model",
        "structengine.game-token=secret-token",
        "structengine.jwt-secret=integration-test-secret-key-0123456789abcdef",
        "structengine.admin-initial-password=admin-test"
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

    @Autowired
    AdminAccountRepository adminAccountRepository;

    @Autowired
    JwtService jwtService;

    @Autowired
    UserAccountRepository userAccountRepository;

    @Autowired
    SnowflakeIdGenerator idGenerator;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void registersHelloPlayerListAndExposesHistory() throws Exception {
        assertEquals(Boolean.TRUE, environment.getProperty("spring.threads.virtual.enabled", Boolean.class));

        PluginListener listener = new PluginListener(mapper);
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        WebSocket socket = http.newWebSocketBuilder()
                .header("X-StructEngine-Token", "secret-token")
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(URI.create("ws://localhost:" + port + "/ws/game?serverId=it"), listener)
                .get(5, TimeUnit.SECONDS);
        listener.connected.get(5, TimeUnit.SECONDS);
        socket.sendText("""
                {"action":"HELLO","protocolVersion":2,"serverId":"it","serverName":"Integration",
                 "blueMapUrl":"http://localhost:8100/structengine-map.html","mapId":"world"}
                """, true).join();
        socket.sendText("""
                {"action":"PLAYER_LIST","serverId":"it","full":true,"players":[
                  {"uuid":"00000000-0000-0000-0000-000000000001","name":"Steve","world":"world",
                   "x":1.5,"y":64,"z":-2.5,"online":true}
                ]}
                """, true).join();

        await(() -> gameServerRegistry.serverInfos().stream()
                .anyMatch(info -> "it".equals(info.serverId())
                        && "http://localhost:8100/structengine-map.html".equals(info.blueMapUrl())
                        && info.playerCount() == 1));

        HttpClient web = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build();

        HttpResponse<String> unauth = web.send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/servers")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, unauth.statusCode());

        AdminAccount admin = adminAccountRepository.findByUsername("admin").orElseThrow();
        String token = jwtService.issue(AccountType.ADMIN, admin.id(), admin.username(),
                "ADMIN", admin.tokenVersion());

        HttpResponse<String> servers = web.send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/servers"))
                        .header("Authorization", "Bearer " + token)
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, servers.statusCode(), servers.body());
        assertTrue(servers.body().contains("\"serverName\":\"Integration\""), servers.body());
        assertTrue(servers.body().contains("\"name\":\"Steve\""), servers.body());

        HttpResponse<String> history = web.send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port
                                + "/api/servers/it/history?playerUuid=00000000-0000-0000-0000-000000000001&playerName=Steve"))
                        .header("Authorization", "Bearer " + token)
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, history.statusCode(), history.body());
        assertTrue(history.body().contains("\"historyId\":\"h-1\""), history.body());

        socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
    }

    @Test
    void enforcesAdminOnlyAccessForRegisteredUser() throws Exception {
        long userId = idGenerator.nextId();
        long now = System.currentTimeMillis();
        userAccountRepository.insert(new AppUser(userId, "UserA", "unused-hash",
                "ACTIVE", 0, now, now, null));
        String userToken = jwtService.issue(AccountType.USER, userId, "UserA", "USER", 0);

        HttpClient web = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

        HttpResponse<String> me = web.send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/auth/me"))
                        .header("Authorization", "Bearer " + userToken)
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, me.statusCode(), me.body());
        assertTrue(me.body().contains("\"username\":\"UserA\""), me.body());

        HttpResponse<String> adminUsers = web.send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/admin/users"))
                        .header("Authorization", "Bearer " + userToken)
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(403, adminUsers.statusCode(), adminUsers.body());

        HttpResponse<String> servers = web.send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/servers"))
                        .header("Authorization", "Bearer " + userToken)
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(403, servers.statusCode(), servers.body());
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

    private static void await(Check check) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (check.ok()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("condition not met before timeout");
    }

    private interface Check {
        boolean ok();
    }

    private static final class PluginListener implements WebSocket.Listener {

        private final ObjectMapper mapper;
        private final StringBuilder textBuffer = new StringBuilder();
        final CompletableFuture<Void> connected = new CompletableFuture<>();

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
                try {
                    JsonNode message = mapper.readTree(text);
                    String action = message.get("action").asText();
                    if ("CONNECTED".equals(action)) {
                        connected.complete(null);
                    } else if ("HISTORY_REQUEST".equals(action)) {
                        String requestId = message.get("requestId").asText();
                        webSocket.sendText("""
                                {"action":"HISTORY_RESPONSE","requestId":"%s","history":[
                                  {"historyId":"h-1","taskId":"t-1","modelName":"测试模型",
                                   "createdAt":1700000000000,"blockCount":42,
                                   "canRollback":true,"canRedo":false}
                                ]}
                                """.formatted(requestId), true);
                    }
                } catch (RuntimeException e) {
                    connected.completeExceptionally(e);
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            connected.completeExceptionally(error);
        }
    }
}
