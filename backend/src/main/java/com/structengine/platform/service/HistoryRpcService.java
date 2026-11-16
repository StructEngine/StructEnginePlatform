package com.structengine.platform.service;

import com.structengine.platform.common.BusinessException;
import com.structengine.platform.common.ErrorCode;
import com.structengine.platform.config.AppProperties;
import com.structengine.platform.game.protocol.GameMessage;
import com.structengine.platform.game.protocol.HistoryEntry;
import com.structengine.platform.game.ws.GameServerRegistry;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 构建历史查询/回滚/恢复的 WS RPC。
 */
@Service
public class HistoryRpcService {

    private final GameServerRegistry registry;
    private final AppProperties props;

    public HistoryRpcService(GameServerRegistry registry, AppProperties props) {
        this.registry = registry;
        this.props = props;
    }

    public List<HistoryEntry> history(String serverId, String playerUuid, String playerName) {
        String requestId = UUID.randomUUID().toString();
        var future = registry.expectHistory(requestId);
        try {
            if (!registry.send(serverId, GameMessage.historyRequest(requestId, playerUuid, playerName))) {
                throw new BusinessException(ErrorCode.GAME_SERVER_UNAVAILABLE, "游戏服未连接");
            }
            return future.get(props.getGameRpcTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new BusinessException(ErrorCode.GAME_SERVER_TIMEOUT, "查询构建历史超时");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "查询构建历史被中断");
        } catch (ExecutionException e) {
            throw new BusinessException(ErrorCode.GAME_SERVER_UNAVAILABLE,
                    "查询构建历史失败: " + e.getCause());
        } finally {
            registry.removeHistory(requestId);
        }
    }

    public String rollback(String serverId, String playerUuid, String playerName, String historyId) {
        String requestId = UUID.randomUUID().toString();
        if (!registry.send(serverId, GameMessage.rollback(requestId, playerUuid, playerName, historyId))) {
            throw new BusinessException(ErrorCode.GAME_SERVER_UNAVAILABLE, "游戏服未连接");
        }
        return requestId;
    }

    public String redo(String serverId, String playerUuid, String playerName, String historyId) {
        String requestId = UUID.randomUUID().toString();
        if (!registry.send(serverId, GameMessage.redo(requestId, playerUuid, playerName, historyId))) {
            throw new BusinessException(ErrorCode.GAME_SERVER_UNAVAILABLE, "游戏服未连接");
        }
        return requestId;
    }
}
