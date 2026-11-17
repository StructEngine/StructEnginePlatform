package com.structengine.platform.game.web;

import com.structengine.platform.common.ApiResponse;
import com.structengine.platform.common.BusinessException;
import com.structengine.platform.common.ErrorCode;
import com.structengine.platform.game.model.GameServerInfo;
import com.structengine.platform.game.protocol.HistoryEntry;
import com.structengine.platform.game.service.BaseUrlResolver;
import com.structengine.platform.id.SnowflakeIdGenerator;
import com.structengine.platform.security.AuthPrincipal;
import com.structengine.platform.server.CreateServerRequest;
import com.structengine.platform.server.ServerCreated;
import com.structengine.platform.server.ServerSecretRotated;
import com.structengine.platform.server.ServerService;
import com.structengine.platform.server.UpdateServerRequest;
import com.structengine.platform.service.HistoryRpcService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 服务器管理接口（用户自己的服务器，管理员可管理全部）与构建历史代理。
 */
@RestController
@RequestMapping("/api/servers")
public class GameServerController {

    private final ServerService serverService;
    private final HistoryRpcService historyRpcService;
    private final BaseUrlResolver baseUrlResolver;

    public GameServerController(ServerService serverService, HistoryRpcService historyRpcService,
                                BaseUrlResolver baseUrlResolver) {
        this.serverService = serverService;
        this.historyRpcService = historyRpcService;
        this.baseUrlResolver = baseUrlResolver;
    }

    @GetMapping
    public ApiResponse<List<GameServerInfo>> servers(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(serverService.list(principal));
    }

    @PostMapping
    public ApiResponse<ServerCreated> create(@AuthenticationPrincipal AuthPrincipal principal,
                                             @Valid @RequestBody CreateServerRequest request,
                                             HttpServletRequest httpRequest) {
        return ApiResponse.ok(serverService.create(principal, request.name(),
                baseUrlResolver.resolve(httpRequest)));
    }

    @GetMapping("/{serverId}")
    public ApiResponse<GameServerInfo> detail(@AuthenticationPrincipal AuthPrincipal principal,
                                              @PathVariable String serverId) {
        return ApiResponse.ok(serverService.get(principal, parseId(serverId)));
    }

    @PutMapping("/{serverId}")
    public ApiResponse<GameServerInfo> rename(@AuthenticationPrincipal AuthPrincipal principal,
                                              @PathVariable String serverId,
                                              @Valid @RequestBody UpdateServerRequest request) {
        return ApiResponse.ok(serverService.rename(principal, parseId(serverId), request.name()));
    }

    @PostMapping("/{serverId}/secret/rotate")
    public ApiResponse<ServerSecretRotated> rotateSecret(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String serverId,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(serverService.rotateSecret(principal, parseId(serverId),
                baseUrlResolver.resolve(httpRequest)));
    }

    @PostMapping("/{serverId}/disable")
    public ApiResponse<GameServerInfo> disable(@AuthenticationPrincipal AuthPrincipal principal,
                                               @PathVariable String serverId) {
        return ApiResponse.ok(serverService.disable(principal, parseId(serverId)));
    }

    @PostMapping("/{serverId}/enable")
    public ApiResponse<GameServerInfo> enable(@AuthenticationPrincipal AuthPrincipal principal,
                                              @PathVariable String serverId) {
        return ApiResponse.ok(serverService.enable(principal, parseId(serverId)));
    }

    @DeleteMapping("/{serverId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthPrincipal principal,
                                    @PathVariable String serverId) {
        serverService.delete(principal, parseId(serverId));
        return ApiResponse.ok(null);
    }

    @GetMapping("/{serverId}/history")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<HistoryEntry>> history(@PathVariable String serverId,
                                                   @RequestParam String playerUuid,
                                                   @RequestParam String playerName) {
        return ApiResponse.ok(historyRpcService.history(serverId, playerUuid, playerName));
    }

    @PostMapping("/{serverId}/rollback")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<HistoryAction> rollback(@PathVariable String serverId,
                                               @Valid @RequestBody HistoryActionRequest request) {
        String requestId = historyRpcService.rollback(serverId, request.playerUuid(),
                request.playerName(), request.historyId());
        return ApiResponse.ok(new HistoryAction(requestId));
    }

    @PostMapping("/{serverId}/redo")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<HistoryAction> redo(@PathVariable String serverId,
                                           @Valid @RequestBody HistoryActionRequest request) {
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

    private static long parseId(String value) {
        try {
            return SnowflakeIdGenerator.parse(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "服务器 ID 格式非法");
        }
    }
}
