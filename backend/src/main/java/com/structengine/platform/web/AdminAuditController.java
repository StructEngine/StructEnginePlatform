package com.structengine.platform.web;

import com.structengine.platform.audit.AuditService;
import com.structengine.platform.audit.AuditView;
import com.structengine.platform.auth.PageResponse;
import com.structengine.platform.common.ApiResponse;
import com.structengine.platform.common.BusinessException;
import com.structengine.platform.common.ErrorCode;
import com.structengine.platform.id.SnowflakeIdGenerator;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员审计日志查询。
 */
@RestController
@RequestMapping("/api/admin/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditController {

    private final AuditService auditService;

    public AdminAuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AuditView>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String serverId,
            @RequestParam(required = false) String action) {
        Long parsedServerId = null;
        if (serverId != null && !serverId.isBlank()) {
            try {
                parsedServerId = SnowflakeIdGenerator.parse(serverId);
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "服务器 ID 格式非法");
            }
        }
        return ApiResponse.ok(auditService.list(page, size, parsedServerId, action));
    }
}
