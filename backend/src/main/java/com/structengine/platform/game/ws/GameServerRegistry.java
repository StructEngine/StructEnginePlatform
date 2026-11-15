package com.structengine.platform.game.ws;

import com.structengine.platform.game.protocol.GameMessage;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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

    public record PendingHeight(String serverId, CompletableFuture<Integer> future) {
    }

    /** 注册连接；同 serverId 的旧连接会被替换，旧连接上的未完成查询立即失败。 */
    public void register(GameServerChannel channel) {
        GameServerChannel previous = channels.put(channel.serverId(), channel);
        if (previous != null && previous != channel) {
            log.info("游戏服 {} 重新连接，关闭旧会话", channel.serverId());
            previous.close("replaced by new connection");
            failPendingForServer(channel.serverId(), new IllegalStateException("游戏服连接被新会话替换"));
        }
        log.info("游戏服 {} 已连接，当前在线数={}", channel.serverId(), channels.size());
    }

    /** 仅当传入连接仍是当前连接时才移除，防止旧会话关闭事件误删新会话。 */
    public boolean unregister(String serverId, GameServerChannel channel) {
        if (!channels.remove(serverId, channel)) {
            return false;
        }
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
        futureMap.values().forEach(pending ->
                pending.future().completeExceptionally(new IllegalStateException("Platform 正在关闭")));
        futureMap.clear();
    }
}
