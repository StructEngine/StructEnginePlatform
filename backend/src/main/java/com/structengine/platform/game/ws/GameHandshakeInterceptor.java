package com.structengine.platform.game.ws;

import com.structengine.platform.config.AppProperties;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * 游戏服 WebSocket 握手鉴权。
 *
 * <p>连接地址：{@code ws://platform:8080/ws/game?serverId=survival}；
 * 若配置了 {@code STRUCTENGINE_GAME_TOKEN}，插件需通过
 * {@code X-StructEngine-Token} 请求头或 {@code Authorization: Bearer ...} 携带令牌。</p>
 */
@Component
public class GameHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(GameHandshakeInterceptor.class);

    public static final String ATTR_SERVER_ID = "structengine.serverId";
    private static final String SERVER_ID_PATTERN = "[A-Za-z0-9._-]{1,64}";

    private final AppProperties props;

    public GameHandshakeInterceptor(AppProperties props) {
        this.props = props;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String serverId = firstNonBlank(
                queryParam(request, "serverId"),
                request.getHeaders().getFirst("X-StructEngine-Server-Id")
        );
        if (serverId == null || !serverId.matches(SERVER_ID_PATTERN)) {
            log.warn("拒绝游戏服连接：serverId 缺失或非法");
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }

        String expected = props.getGameToken();
        if (StringUtils.hasText(expected)) {
            String token = firstNonBlank(
                    request.getHeaders().getFirst("X-StructEngine-Token"),
                    bearerToken(request)
            );
            if (!constantTimeEquals(expected, token)) {
                log.warn("拒绝游戏服 {} 连接：令牌不匹配", serverId);
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
        }

        attributes.put(ATTR_SERVER_ID, serverId);
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

    private static String bearerToken(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst("Authorization");
        if (authorization == null) {
            return null;
        }
        String prefix = "Bearer ";
        if (!authorization.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return null;
        }
        return authorization.substring(prefix.length()).trim();
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
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
