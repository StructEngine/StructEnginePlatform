package com.structengine.platform.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AuditRepository {

    private static final RowMapper<AuditLog> MAPPER = (rs, rowNum) -> new AuditLog(
            rs.getLong("id"),
            rs.getString("actor_type"),
            rs.getObject("actor_id") == null ? null : rs.getLong("actor_id"),
            rs.getString("actor_name"),
            rs.getString("action"),
            rs.getObject("server_id") == null ? null : rs.getLong("server_id"),
            rs.getObject("model_id") == null ? null : rs.getLong("model_id"),
            rs.getObject("task_id") == null ? null : rs.getLong("task_id"),
            rs.getObject("x") == null ? null : rs.getInt("x"),
            rs.getObject("y") == null ? null : rs.getInt("y"),
            rs.getObject("z") == null ? null : rs.getInt("z"),
            rs.getString("ip"),
            rs.getString("result"),
            rs.getString("detail"),
            rs.getLong("created_at")
    );

    private final JdbcTemplate jdbc;

    public AuditRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(AuditLog log) {
        jdbc.update("""
                INSERT INTO audit_log
                (id, actor_type, actor_id, actor_name, action, server_id, model_id, task_id,
                 x, y, z, ip, result, detail, created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, log.id(), log.actorType(), log.actorId(), log.actorName(), log.action(),
                log.serverId(), log.modelId(), log.taskId(), log.x(), log.y(), log.z(),
                log.ip(), log.result(), log.detail(), log.createdAt());
    }

    public List<AuditLog> list(int offset, int limit, Long serverId, String action) {
        if (serverId != null && action != null && !action.isBlank()) {
            return jdbc.query("""
                    SELECT * FROM audit_log WHERE server_id = ? AND action = ?
                     ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?
                    """, MAPPER, serverId, action, limit, offset);
        }
        if (serverId != null) {
            return jdbc.query("""
                    SELECT * FROM audit_log WHERE server_id = ?
                     ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?
                    """, MAPPER, serverId, limit, offset);
        }
        if (action != null && !action.isBlank()) {
            return jdbc.query("""
                    SELECT * FROM audit_log WHERE action = ?
                     ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?
                    """, MAPPER, action, limit, offset);
        }
        return jdbc.query("""
                SELECT * FROM audit_log ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?
                """, MAPPER, limit, offset);
    }

    public long count(Long serverId, String action) {
        if (serverId != null && action != null && !action.isBlank()) {
            Long count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM audit_log WHERE server_id = ? AND action = ?",
                    Long.class, serverId, action);
            return count == null ? 0L : count;
        }
        if (serverId != null) {
            Long count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM audit_log WHERE server_id = ?", Long.class, serverId);
            return count == null ? 0L : count;
        }
        if (action != null && !action.isBlank()) {
            Long count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM audit_log WHERE action = ?", Long.class, action);
            return count == null ? 0L : count;
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM audit_log", Long.class);
        return count == null ? 0L : count;
    }
}
