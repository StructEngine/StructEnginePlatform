package com.structengine.platform.game.web;

import com.structengine.platform.common.ApiResponse;
import com.structengine.platform.game.model.GameServerInfo;
import com.structengine.platform.game.protocol.HistoryEntry;
import com.structengine.platform.game.ws.GameServerRegistry;
import com.structengine.platform.service.HistoryRpcService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * 新 Web 使用的服务器/玩家/历史接口。
 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/servers")
public class GameServerController {

    private final GameServerRegistry registry;
    private final HistoryRpcService historyRpcService;

    public GameServerController(GameServerRegistry registry, HistoryRpcService historyRpcService) {
        this.registry = registry;
        this.historyRpcService = historyRpcService;
    }

    @GetMapping
    public ApiResponse<List<GameServerInfo>> servers() {
        return ApiResponse.ok(registry.serverInfos());
    }

    @GetMapping("/{serverId}/history")
    public ApiResponse<List<HistoryEntry>> history(@PathVariable String serverId,
                                                   @RequestParam String playerUuid,
                                                   @RequestParam String playerName) {
        return ApiResponse.ok(historyRpcService.history(serverId, playerUuid, playerName));
    }

    @PostMapping("/{serverId}/rollback")
    public ApiResponse<HistoryAction> rollback(@PathVariable String serverId,
                                               @RequestBody HistoryActionRequest request) {
        String requestId = historyRpcService.rollback(serverId, request.playerUuid(),
                request.playerName(), request.historyId());
        return ApiResponse.ok(new HistoryAction(requestId));
    }

    @PostMapping("/{serverId}/redo")
    public ApiResponse<HistoryAction> redo(@PathVariable String serverId,
                                           @RequestBody HistoryActionRequest request) {
        String requestId = historyRpcService.redo(serverId, request.playerUuid(),
                request.playerName(), request.historyId());
        return ApiResponse.ok(new HistoryAction(requestId));
    }

    public record HistoryActionRequest(@NotBlank String playerUuid,
                                       @NotBlank String playerName,
                                       @NotBlank String historyId) {
    }

    public record HistoryAction(String requestId) {
    }
}
