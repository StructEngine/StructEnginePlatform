package com.structengine.platform.game.web;

import com.structengine.platform.common.ApiResponse;
import com.structengine.platform.common.BusinessException;
import com.structengine.platform.common.ErrorCode;
import com.structengine.platform.game.model.GameServerInfo;
import com.structengine.platform.game.service.BaseUrlResolver;
import com.structengine.platform.id.SnowflakeIdGenerator;
import com.structengine.platform.security.AuthPrincipal;
import com.structengine.platform.server.CreateServerRequest;
import com.structengine.platform.server.ServerCreated;
import com.structengine.platform.server.ServerSecretRotated;
import com.structengine.platform.server.ServerService;
import com.structengine.platform.server.UpdateServerRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 服务器管理接口（用户自己的服务器，管理员可管理全部）。
 */
@RestController
@RequestMapping("/api/servers")
public class GameServerController {

    private final ServerService serverService;
    private final BaseUrlResolver baseUrlResolver;

    public GameServerController(ServerService serverService, BaseUrlResolver baseUrlResolver) {
        this.serverService = serverService;
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

    private static long parseId(String value) {
        try {
            return SnowflakeIdGenerator.parse(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "服务器 ID 格式非法");
        }
    }
}
