package com.structengine.platform.audit;

import com.structengine.platform.auth.PageResponse;
import com.structengine.platform.id.SnowflakeIdGenerator;
import com.structengine.platform.security.AuthPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 高影响操作审计。审计写入失败不能影响主业务。
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditRepository repository;
    private final SnowflakeIdGenerator idGenerator;

    public AuditService(AuditRepository repository, SnowflakeIdGenerator idGenerator) {
        this.repository = repository;
        this.idGenerator = idGenerator;
    }

    public void record(AuthPrincipal actor, String action, Long serverId, Long modelId,
                       Long taskId, Integer x, Integer y, Integer z, String ip,
                       String result, String detail) {
        try {
            repository.insert(new AuditLog(idGenerator.nextId(),
                    actor == null ? "SYSTEM" : actor.type().name(),
                    actor == null ? null : actor.id(),
                    actor == null ? null : actor.username(),
                    action, serverId, modelId, taskId, x, y, z, ip, result, detail,
                    System.currentTimeMillis()));
        } catch (RuntimeException e) {
            log.error("写入审计日志失败 action={} serverId={}", action, serverId, e);
        }
    }

    public PageResponse<AuditView> list(int page, int size, Long serverId, String action) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        long total = repository.count(serverId, action);
        List<AuditView> items = repository.list((safePage - 1) * safeSize, safeSize,
                        serverId, action).stream()
                .map(AuditService::toView).toList();
        return new PageResponse<>(items, safePage, safeSize, total);
    }

    private static AuditView toView(AuditLog log) {
        return new AuditView(Long.toString(log.id()), log.actorType(), log.actorName(),
                log.action(),
                log.serverId() == null ? null : Long.toString(log.serverId()),
                log.modelId() == null ? null : Long.toString(log.modelId()),
                log.taskId() == null ? null : Long.toString(log.taskId()),
                log.x(), log.y(), log.z(), log.ip(), log.result(), log.detail(),
                log.createdAt());
    }
}
