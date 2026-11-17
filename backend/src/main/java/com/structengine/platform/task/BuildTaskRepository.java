package com.structengine.platform.task;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class BuildTaskRepository {

    private static final RowMapper<BuildTask> MAPPER = (rs, rowNum) -> new BuildTask(
            rs.getLong("id"),
            rs.getLong("owner_user_id"),
            rs.getLong("server_id"),
            rs.getLong("model_id"),
            rs.getString("player_uuid"),
            rs.getString("player_name"),
            rs.getInt("center_x"), rs.getInt("center_y"), rs.getInt("center_z"),
            rs.getInt("origin_x"), rs.getInt("origin_y"), rs.getInt("origin_z"),
            rs.getInt("rotation"),
            rs.getString("status"),
            rs.getInt("placed"),
            rs.getInt("total"),
            rs.getString("error"),
            rs.getLong("created_at"),
            rs.getLong("updated_at")
    );

    private final JdbcTemplate jdbc;

    public BuildTaskRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(BuildTask task) {
        jdbc.update("""
                INSERT INTO build_task
                (id, owner_user_id, server_id, model_id, player_uuid, player_name,
                 center_x, center_y, center_z, origin_x, origin_y, origin_z,
                 rotation, status, placed, total, error, created_at, updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, task.id(), task.ownerUserId(), task.serverId(), task.modelId(),
                task.playerUuid(), task.playerName(), task.centerX(), task.centerY(),
                task.centerZ(), task.originX(), task.originY(), task.originZ(),
                task.rotation(), task.status(), task.placed(), task.total(),
                task.error(), task.createdAt(), task.updatedAt());
    }

    public Optional<BuildTask> findById(long id) {
        List<BuildTask> rows = jdbc.query("SELECT * FROM build_task WHERE id = ?", MAPPER, id);
        return rows.stream().findFirst();
    }

    public void updateStatus(long id, String status, int placed, int total, String error,
                             long updatedAt) {
        jdbc.update("""
                UPDATE build_task SET status = ?, placed = ?, total = ?, error = ?, updated_at = ?
                 WHERE id = ?
                """, status, placed, total, error, updatedAt, id);
    }

    public int markStaleFailed(long cutoff, long updatedAt, String message) {
        return jdbc.update("""
                UPDATE build_task
                   SET status = 'FAILED', error = ?, updated_at = ?
                 WHERE status IN ('QUEUED', 'DOWNLOADING', 'BUILDING') AND updated_at < ?
                """, message, updatedAt, cutoff);
    }

    public int markInterrupted(long updatedAt, String message) {
        return jdbc.update("""
                UPDATE build_task
                   SET status = 'ABORTED', error = ?, updated_at = ?
                 WHERE status IN ('QUEUED', 'DOWNLOADING', 'BUILDING')
                """, message, updatedAt);
    }

    public List<BuildTask> listByServer(long serverId, String playerUuid, int limit) {
        if (playerUuid == null || playerUuid.isBlank()) {
            return jdbc.query("""
                    SELECT * FROM build_task WHERE server_id = ?
                     ORDER BY created_at DESC, id DESC LIMIT ?
                    """, MAPPER, serverId, limit);
        }
        return jdbc.query("""
                SELECT * FROM build_task WHERE server_id = ? AND player_uuid = ?
                 ORDER BY created_at DESC, id DESC LIMIT ?
                """, MAPPER, serverId, playerUuid, limit);
    }
}
