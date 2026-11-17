package com.structengine.platform.server;

import com.structengine.platform.common.BusinessException;
import com.structengine.platform.common.ErrorCode;
import com.structengine.platform.game.model.GameServerInfo;
import com.structengine.platform.game.protocol.PlayerInfo;
import com.structengine.platform.game.ws.GameServerRegistry;
import com.structengine.platform.id.SnowflakeIdGenerator;
import com.structengine.platform.security.AuthPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 服务器归属、secret 生命周期与展示信息聚合。
 */
@Service
public class ServerService {

    private final MinecraftServerRepository repository;
    private final GameServerRegistry registry;
    private final SnowflakeIdGenerator idGenerator;

    public ServerService(MinecraftServerRepository repository, GameServerRegistry registry,
                         SnowflakeIdGenerator idGenerator) {
        this.repository = repository;
        this.registry = registry;
        this.idGenerator = idGenerator;
    }

    public List<GameServerInfo> list(AuthPrincipal principal) {
        List<MinecraftServer> servers = principal.admin()
                ? repository.listAll()
                : repository.listByOwner(principal.id());
        return servers.stream().map(this::toInfo).toList();
    }

    public GameServerInfo get(AuthPrincipal principal, long serverId) {
        return toInfo(requireAccessible(principal, serverId));
    }

    @Transactional
    public ServerCreated create(AuthPrincipal principal, String name, String platformBaseUrl) {
        if (principal.admin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "管理员不拥有服务器，请使用普通用户账号创建");
        }
        String safeName = normalizeName(name);
        String secret = ServerSecrets.newSecret();
        long now = System.currentTimeMillis();
        MinecraftServer server = new MinecraftServer(idGenerator.nextId(), principal.id(), safeName,
                ServerSecrets.hash(secret), "PENDING", null, null, null, null, null, null,
                now, now);
        repository.insert(server);
        return new ServerCreated(toInfo(server), secret, wsUrl(platformBaseUrl));
    }

    @Transactional
    public GameServerInfo rename(AuthPrincipal principal, long serverId, String name) {
        MinecraftServer server = requireAccessible(principal, serverId);
        repository.updateName(server.id(), normalizeName(name), System.currentTimeMillis());
        return toInfo(repository.findById(serverId).orElseThrow());
    }

    @Transactional
    public ServerSecretRotated rotateSecret(AuthPrincipal principal, long serverId,
                                            String platformBaseUrl) {
        MinecraftServer server = requireAccessible(principal, serverId);
        String secret = ServerSecrets.newSecret();
        long now = System.currentTimeMillis();
        repository.updateSecret(server.id(), ServerSecrets.hash(secret), now, now);
        registry.disconnect(Long.toString(server.id()), "secret rotated");
        String nextStatus = server.registeredAt() == null ? "PENDING" : "OFFLINE";
        repository.updateStatus(server.id(), nextStatus, now);
        return new ServerSecretRotated(Long.toString(server.id()), secret, wsUrl(platformBaseUrl));
    }

    @Transactional
    public GameServerInfo disable(AuthPrincipal principal, long serverId) {
        MinecraftServer server = requireAccessible(principal, serverId);
        repository.updateStatus(server.id(), "DISABLED", System.currentTimeMillis());
        registry.disconnect(Long.toString(server.id()), "server disabled");
        return toInfo(repository.findById(serverId).orElseThrow());
    }

    @Transactional
    public GameServerInfo enable(AuthPrincipal principal, long serverId) {
        MinecraftServer server = requireAccessible(principal, serverId);
        String nextStatus = server.registeredAt() == null ? "PENDING" : "OFFLINE";
        repository.updateStatus(server.id(), nextStatus, System.currentTimeMillis());
        return toInfo(repository.findById(serverId).orElseThrow());
    }

    @Transactional
    public void delete(AuthPrincipal principal, long serverId) {
        MinecraftServer server = requireAccessible(principal, serverId);
        repository.softDelete(server.id(), System.currentTimeMillis());
        registry.disconnect(Long.toString(server.id()), "server deleted");
    }

    /** WS 握手：按 secret 哈希反查可用服务器。 */
    public Optional<MinecraftServer> findBySecret(String secret) {
        if (secret == null || secret.isBlank()) {
            return Optional.empty();
        }
        return repository.findBySecretHash(ServerSecrets.hash(secret))
                .filter(server -> !server.deleted() && !server.disabled());
    }

    public void markOnline(long serverId, String blueMapUrl, String mapId) {
        long now = System.currentTimeMillis();
        repository.updateRegistration(serverId, blueMapUrl, mapId, now, now, now);
    }

    public void markOffline(long serverId) {
        repository.findById(serverId)
                .filter(server -> !server.disabled() && !server.deleted())
                .ifPresent(server -> repository.updateStatus(serverId, "OFFLINE",
                        System.currentTimeMillis()));
    }

    public void touch(long serverId) {
        repository.touch(serverId, System.currentTimeMillis());
    }

    public void markAllOnlineOffline() {
        repository.markAllOnlineOffline(System.currentTimeMillis());
    }

    public GameServerInfo info(long serverId) {
        MinecraftServer server = repository.findById(serverId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "服务器不存在"));
        return toInfo(server);
    }

    private MinecraftServer requireAccessible(AuthPrincipal principal, long serverId) {
        MinecraftServer server = repository.findById(serverId)
                .filter(value -> !value.deleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "服务器不存在"));
        if (!principal.admin() && server.ownerUserId() != principal.id()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "服务器不存在");
        }
        return server;
    }

    private GameServerInfo toInfo(MinecraftServer server) {
        String serverId = Long.toString(server.id());
        boolean connected = registry.isConnected(serverId) && !server.disabled();
        List<PlayerInfo> players = connected ? registry.players(serverId) : List.of();
        String status = server.disabled() ? "DISABLED" : (connected ? "ONLINE" : server.status());
        return new GameServerInfo(
                serverId,
                server.name(),
                server.blueMapUrl(),
                server.mapId() == null ? "world" : server.mapId(),
                status,
                Long.toString(server.ownerUserId()),
                players.size(),
                connected,
                players,
                server.lastSeenAt(),
                server.registeredAt(),
                server.createdAt()
        );
    }

    private static String normalizeName(String name) {
        String value = name == null ? "" : name.trim();
        if (value.isEmpty() || value.length() > 64) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "服务器名称需为 1~64 个字符");
        }
        return value;
    }

    /** 把 Platform 的 http(s) base URL 转成插件可用的 ws(s) 地址。 */
    public static String wsUrl(String platformBaseUrl) {
        String base = platformBaseUrl == null ? "" : platformBaseUrl.trim();
        if (base.isEmpty()) {
            return "/ws/game";
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.startsWith("https://")) {
            base = "wss://" + base.substring("https://".length());
        } else if (base.startsWith("http://")) {
            base = "ws://" + base.substring("http://".length());
        }
        return base + "/ws/game";
    }
}
