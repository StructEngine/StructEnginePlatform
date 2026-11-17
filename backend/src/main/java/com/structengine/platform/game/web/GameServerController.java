package com.structengine.platform.game.web;

import com.structengine.platform.audit.AuditService;
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
    private final AuditService auditService;

    public GameServerController(ServerService serverService, BaseUrlResolver baseUrlResolver,
                                AuditService auditService) {
        this.serverService = serverService;
        this.baseUrlResolver = baseUrlResolver;
        this.auditService = auditService;
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
        ServerCreated created = serverService.create(principal, request.name(),
                baseUrlResolver.resolve(httpRequest));
        auditService.record(principal, "SERVER_CREATE", Long.parseLong(created.server().serverId()),
                null, null, null, null, null, ip(httpRequest), "SUCCESS", request.name());
        return ApiResponse.ok(created);
    }

    @GetMapping("/{serverId}")
    public ApiResponse<GameServerInfo> detail(@AuthenticationPrincipal AuthPrincipal principal,
                                              @PathVariable String serverId) {
        return ApiResponse.ok(serverService.get(principal, parseId(serverId)));
    }

    @PutMapping("/{serverId}")
    public ApiResponse<GameServerInfo> rename(@AuthenticationPrincipal AuthPrincipal principal,
                                              @PathVariable String serverId,
                                              @Valid @RequestBody UpdateServerRequest request,
                                              HttpServletRequest httpRequest) {
        GameServerInfo info = serverService.rename(principal, parseId(serverId), request.name());
        auditService.record(principal, "SERVER_RENAME", parseId(serverId), null, null,
                null, null, null, ip(httpRequest), "SUCCESS", request.name());
        return ApiResponse.ok(info);
    }

    @PostMapping("/{serverId}/secret/rotate")
    public ApiResponse<ServerSecretRotated> rotateSecret(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String serverId,
            HttpServletRequest httpRequest) {
        ServerSecretRotated rotated = serverService.rotateSecret(principal, parseId(serverId),
                baseUrlResolver.resolve(httpRequest));
        auditService.record(principal, "SERVER_SECRET_ROTATE", parseId(serverId), null, null,
                null, null, null, ip(httpRequest), "SUCCESS", null);
        return ApiResponse.ok(rotated);
    }

    @PostMapping("/{serverId}/disable")
    public ApiResponse<GameServerInfo> disable(@AuthenticationPrincipal AuthPrincipal principal,
                                               @PathVariable String serverId,
                                               HttpServletRequest httpRequest) {
        GameServerInfo info = serverService.disable(principal, parseId(serverId));
        auditService.record(principal, "SERVER_DISABLE", parseId(serverId), null, null,
                null, null, null, ip(httpRequest), "SUCCESS", null);
        return ApiResponse.ok(info);
    }

    @PostMapping("/{serverId}/enable")
    public ApiResponse<GameServerInfo> enable(@AuthenticationPrincipal AuthPrincipal principal,
                                              @PathVariable String serverId,
                                              HttpServletRequest httpRequest) {
        GameServerInfo info = serverService.enable(principal, parseId(serverId));
        auditService.record(principal, "SERVER_ENABLE", parseId(serverId), null, null,
                null, null, null, ip(httpRequest), "SUCCESS", null);
        return ApiResponse.ok(info);
    }

    @DeleteMapping("/{serverId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthPrincipal principal,
                                    @PathVariable String serverId,
                                    HttpServletRequest httpRequest) {
        serverService.delete(principal, parseId(serverId));
        auditService.record(principal, "SERVER_DELETE", parseId(serverId), null, null,
                null, null, null, ip(httpRequest), "SUCCESS", null);
        return ApiResponse.ok(null);
    }

    private static String ip(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static long parseId(String value) {
        try {
            return SnowflakeIdGenerator.parse(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "服务器 ID 格式非法");
        }
    }
}
