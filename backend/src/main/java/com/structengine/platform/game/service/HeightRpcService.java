package com.structengine.platform.game.service;

import com.structengine.platform.common.BusinessException;
import com.structengine.platform.common.ErrorCode;
import com.structengine.platform.config.AppProperties;
import com.structengine.platform.game.protocol.GameMessage;
import com.structengine.platform.game.ws.GameServerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 高度查询 RPC：发送 QUERY_Y 后同步等待 RESPONSE_Y。
 *
 * <p>Spring Boot 已开启 {@code spring.threads.virtual.enabled=true}，Controller 运行在
 * JDK 21 虚拟线程上；此处的 {@code future.get(timeout)} 是阻塞式调用，但载体是虚拟线程，
 * 等待期间不占用操作系统平台线程，适合大量并发的“云端等待边缘端”场景。</p>
 */
@Service
public class HeightRpcService {

    private static final Logger log = LoggerFactory.getLogger(HeightRpcService.class);

    private final GameServerRegistry registry;
    private final AppProperties props;

    public HeightRpcService(GameServerRegistry registry, AppProperties props) {
        this.registry = registry;
        this.props = props;
    }

    /**
     * 向指定游戏服查询 {@code (x, z)} 的地表高度，阻塞等待最多
     * {@link AppProperties#getGameRpcTimeoutSeconds()} 秒。
     *
     * @return 插件主线程查询到的 Y 值
     * @throws BusinessException 游戏服未连接、超时、断开或插件返回错误
     */
    public int queryY(String serverId, String taskId, String playerName, int x, int z) {
        CompletableFuture<Integer> future = registry.expectHeight(taskId, serverId);
        try {
            boolean sent = registry.send(serverId, GameMessage.queryY(taskId, playerName, x, z));
            if (!sent) {
                throw new BusinessException(ErrorCode.GAME_SERVER_UNAVAILABLE,
                        "游戏服务器 " + serverId + " 未连接或消息发送失败");
            }

            log.debug("等待游戏服 {} 返回高度 taskId={} virtualThread={}",
                    serverId, taskId, Thread.currentThread().isVirtual());
            return future.get(props.getGameRpcTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new BusinessException(ErrorCode.GAME_SERVER_TIMEOUT,
                    "游戏服务器 " + serverId + " 在 " + props.getGameRpcTimeoutSeconds() + " 秒内未返回高度");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "高度查询被中断");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new BusinessException(ErrorCode.GAME_SERVER_UNAVAILABLE,
                    "高度查询失败: " + rootMessage(cause));
        } finally {
            registry.removePending(taskId);
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() != null ? current.getMessage() : current.getClass().getSimpleName();
    }
}
