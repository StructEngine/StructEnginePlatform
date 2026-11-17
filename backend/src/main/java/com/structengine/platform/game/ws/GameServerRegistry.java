package com.structengine.platform.game.ws;

import com.structengine.platform.game.model.GameServerInfo;
import com.structengine.platform.game.protocol.GameMessage;
import com.structengine.platform.game.protocol.HistoryEntry;
import com.structengine.platform.game.protocol.PlayerInfo;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 游戏服连接注册表 + 高度查询 Future 挂载点。
 *
 * <p>taskId 在全局唯一，因此 futureMap 以 taskId 为键；同时记录 serverId，
 * 连接断开时可以把该服所有未完成查询统一置为异常，避免 Controller 线程空等到超时。</p>
 */
@Component
public class GameServerRegistry {

    private static final Logger log = LoggerFactory.getLogger(GameServerRegistry.class);

    private final Map<String, GameServerChannel> channels = new ConcurrentHashMap<>();
    private final Map<String, PendingHeight> futureMap = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<List<HistoryEntry>>> historyMap = new ConcurrentHashMap<>();
    private final Map<String, ServerState> states = new ConcurrentHashMap<>();

    public record PendingHeight(String serverId, CompletableFuture<Integer> future) {
    }

    private static final class ServerState {
        volatile String serverName;
        volatile String blueMapUrl;
        volatile String mapId;
        volatile List<PlayerInfo> players = List.of();
        volatile long connectedAt = System.currentTimeMillis();
        volatile long lastSeen = System.currentTimeMillis();
    }

    /** 注册连接；同 serverId 的旧连接会被替换，旧连接上的未完成查询立即失败。 */
    public void register(GameServerChannel channel) {
        GameServerChannel previous = channels.put(channel.serverId(), channel);
        if (previous != null && previous != channel) {
            log.info("游戏服 {} 重新连接，关闭旧会话", channel.serverId());
            previous.close("replaced by new connection");
            failPendingForServer(channel.serverId(), new IllegalStateException("游戏服连接被新会话替换"));
        }
        states.computeIfAbsent(channel.serverId(), id -> new ServerState());
        log.info("游戏服 {} 已连接，当前在线数={}", channel.serverId(), channels.size());
    }

    /** 记录插件 HELLO 上报的 BlueMap / 服务器元数据。 */
    public void updateHello(String serverId, String serverName, String blueMapUrl, String mapId) {
        ServerState state = states.computeIfAbsent(serverId, id -> new ServerState());
        if (serverName != null && !serverName.isBlank()) {
            state.serverName = serverName;
        }
        if (blueMapUrl != null && !blueMapUrl.isBlank()) {
            state.blueMapUrl = blueMapUrl;
        }
        if (mapId != null && !mapId.isBlank()) {
            state.mapId = mapId;
        }
        state.lastSeen = System.currentTimeMillis();
    }

    /**
     * 更新玩家列表。full=true 时整体替换；否则按 UUID 合并。
     */
    public void updatePlayers(String serverId, List<PlayerInfo> players, boolean full) {
        ServerState state = states.computeIfAbsent(serverId, id -> new ServerState());
        List<PlayerInfo> safe = players == null ? List.of() : List.copyOf(players);
        if (full) {
            state.players = safe;
        } else {
            Map<String, PlayerInfo> merged = new LinkedHashMap<>();
            for (PlayerInfo existing : state.players) {
                merged.put(existing.uuid(), existing);
            }
            for (PlayerInfo player : safe) {
                merged.put(player.uuid(), player);
            }
            state.players = new ArrayList<>(merged.values());
        }
        state.lastSeen = System.currentTimeMillis();
    }

    public List<GameServerInfo> serverInfos() {
        List<GameServerInfo> result = new ArrayList<>();
        for (Map.Entry<String, GameServerChannel> entry : channels.entrySet()) {
            String serverId = entry.getKey();
            ServerState state = states.get(serverId);
            if (state == null) {
                state = new ServerState();
            }
            result.add(new GameServerInfo(
                    serverId,
                    state.serverName == null ? serverId : state.serverName,
                    state.blueMapUrl,
                    state.mapId == null ? "world" : state.mapId,
                    entry.getValue().isOpen() ? "ONLINE" : "OFFLINE",
                    null,
                    state.players.size(),
                    entry.getValue().isOpen(),
                    state.players,
                    state.lastSeen,
                    state.connectedAt,
                    state.connectedAt
            ));
        }
        result.sort((a, b) -> a.serverId().compareTo(b.serverId()));
        return result;
    }

    public List<PlayerInfo> players(String serverId) {
        ServerState state = states.get(serverId);
        return state == null ? List.of() : List.copyOf(state.players);
    }

    /** 仅当传入连接仍是当前连接时才移除，防止旧会话关闭事件误删新会话。 */
    public boolean unregister(String serverId, GameServerChannel channel) {
        if (!channels.remove(serverId, channel)) {
            return false;
        }
        states.remove(serverId);
        log.info("游戏服 {} 已断开，当前在线数={}", serverId, channels.size());
        failPendingForServer(serverId, new IllegalStateException("游戏服连接已断开"));
        return true;
    }

    public List<String> serverIds() {
        return channels.keySet().stream().sorted().toList();
    }

    public boolean isConnected(String serverId) {
        GameServerChannel channel = channels.get(serverId);
        return channel != null && channel.isOpen();
    }

    /** 主动断开某服连接（secret 轮换/禁用/删除时使用）。 */
    public void disconnect(String serverId, String reason) {
        GameServerChannel channel = channels.get(serverId);
        if (channel != null) {
            log.info("主动断开游戏服 {}：{}", serverId, reason);
            channel.close(reason);
        }
    }

    public boolean send(String serverId, GameMessage message) {
        GameServerChannel channel = channels.get(serverId);
        if (channel == null) {
            return false;
        }
        return channel.send(message);
    }

    public CompletableFuture<Integer> expectHeight(String taskId, String serverId) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        PendingHeight previous = futureMap.putIfAbsent(taskId, new PendingHeight(serverId, future));
        if (previous != null) {
            throw new IllegalStateException("重复的 taskId: " + taskId);
        }
        return future;
    }

    public boolean completeHeight(String taskId, int y) {
        PendingHeight pending = futureMap.get(taskId);
        if (pending == null) {
            return false;
        }
        return pending.future().complete(y);
    }

    public boolean failHeight(String taskId, Throwable error) {
        PendingHeight pending = futureMap.get(taskId);
        if (pending == null) {
            return false;
        }
        return pending.future().completeExceptionally(error);
    }

    public void removePending(String taskId) {
        futureMap.remove(taskId);
    }

    public CompletableFuture<List<HistoryEntry>> expectHistory(String requestId) {
        CompletableFuture<List<HistoryEntry>> future = new CompletableFuture<>();
        if (historyMap.putIfAbsent(requestId, future) != null) {
            throw new IllegalStateException("重复的 history requestId: " + requestId);
        }
        return future;
    }

    public boolean completeHistory(String requestId, List<HistoryEntry> history) {
        CompletableFuture<List<HistoryEntry>> future = historyMap.get(requestId);
        return future != null && future.complete(history == null ? List.of() : history);
    }

    public void removeHistory(String requestId) {
        historyMap.remove(requestId);
    }

    /** 当前等待 RESPONSE_Y 的任务数，供监控/测试使用。 */
    public int pendingCount() {
        return futureMap.size();
    }

    private void failPendingForServer(String serverId, Throwable error) {
        futureMap.forEach((taskId, pending) -> {
            if (pending.serverId().equals(serverId)) {
                pending.future().completeExceptionally(error);
            }
        });
    }

    @PreDestroy
    public void closeAll() {
        channels.values().forEach(channel -> channel.close("platform shutdown"));
        channels.clear();
        states.clear();
        futureMap.values().forEach(pending ->
                pending.future().completeExceptionally(new IllegalStateException("Platform 正在关闭")));
        futureMap.clear();
        historyMap.values().forEach(future ->
                future.completeExceptionally(new IllegalStateException("Platform 正在关闭")));
        historyMap.clear();
    }
}
