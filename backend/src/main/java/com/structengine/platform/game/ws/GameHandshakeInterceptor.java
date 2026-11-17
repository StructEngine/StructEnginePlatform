package com.structengine.platform.game.ws;

import com.structengine.platform.server.MinecraftServer;
import com.structengine.platform.server.ServerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * 游戏服 WebSocket 握手鉴权：使用每服独立 secret 反查服务器并绑定 serverId。
 *
 * <p>secret 可通过 {@code X-StructEngine-Secret} 请求头或 {@code ?secret=} 查询参数携带。
 * 平台不信任客户端自报的 serverId，serverId 完全由 secret 推导。</p>
 */
@Component
public class GameHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(GameHandshakeInterceptor.class);

    public static final String ATTR_SERVER_ID = "structengine.serverId";
    public static final String ATTR_SECRET_HASH = "structengine.secretHash";

    private final ServerService serverService;

    public GameHandshakeInterceptor(ServerService serverService) {
        this.serverService = serverService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String secret = firstNonBlank(
                request.getHeaders().getFirst("X-StructEngine-Secret"),
                queryParam(request, "secret")
        );
        if (!StringUtils.hasText(secret)) {
            log.warn("拒绝游戏服连接：缺少 secret");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        MinecraftServer server = serverService.findBySecret(secret).orElse(null);
        if (server == null) {
            log.warn("拒绝游戏服连接：secret 无效、服务器已禁用或已删除");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        attributes.put(ATTR_SERVER_ID, Long.toString(server.id()));
        attributes.put(ATTR_SECRET_HASH, server.secretHash());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    private static String queryParam(ServerHttpRequest request, String name) {
        return UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst(name);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
