package com.structengine.platform.model;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * model_artifact 数据访问。
 */
@Repository
public class ModelArtifactRepository {

    private static final RowMapper<ModelArtifact> MAPPER = (rs, rowNum) -> new ModelArtifact(
            rs.getLong("id"),
            rs.getLong("owner_user_id"),
            rs.getLong("server_id"),
            rs.getString("source"),
            rs.getString("prompt"),
            rs.getString("model_url"),
            rs.getInt("block_count"),
            rs.getInt("min_x"), rs.getInt("max_x"),
            rs.getInt("min_y"), rs.getInt("max_y"),
            rs.getInt("min_z"), rs.getInt("max_z"),
            rs.getLong("created_at"),
            rs.getObject("deleted_at") == null ? null : rs.getLong("deleted_at")
    );

    private final JdbcTemplate jdbc;

    public ModelArtifactRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(ModelArtifact model) {
        jdbc.update("""
                INSERT INTO model_artifact
                (id, owner_user_id, server_id, source, prompt, model_url, block_count,
                 min_x, max_x, min_y, max_y, min_z, max_z, created_at, deleted_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, model.id(), model.ownerUserId(), model.serverId(), model.source(),
                model.prompt(), model.modelUrl(), model.blockCount(),
                model.minX(), model.maxX(), model.minY(), model.maxY(),
                model.minZ(), model.maxZ(), model.createdAt(), model.deletedAt());
    }

    public Optional<ModelArtifact> findById(long id) {
        List<ModelArtifact> rows = jdbc.query(
                "SELECT * FROM model_artifact WHERE id = ?", MAPPER, id);
        return rows.stream().findFirst();
    }

    public List<ModelArtifact> listByServer(long serverId, String source, int offset, int limit) {
        if (source == null || source.isBlank()) {
            return jdbc.query("""
                    SELECT * FROM model_artifact
                     WHERE server_id = ? AND deleted_at IS NULL
                     ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?
                    """, MAPPER, serverId, limit, offset);
        }
        return jdbc.query("""
                SELECT * FROM model_artifact
                 WHERE server_id = ? AND source = ? AND deleted_at IS NULL
                 ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?
                """, MAPPER, serverId, source, limit, offset);
    }

    public long countByServer(long serverId, String source) {
        Long count;
        if (source == null || source.isBlank()) {
            count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM model_artifact WHERE server_id = ? AND deleted_at IS NULL",
                    Long.class, serverId);
        } else {
            count = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM model_artifact
                     WHERE server_id = ? AND source = ? AND deleted_at IS NULL
                    """, Long.class, serverId, source);
        }
        return count == null ? 0L : count;
    }

    public List<ModelArtifact> listAll(String source, int offset, int limit) {
        if (source == null || source.isBlank()) {
            return jdbc.query("""
                    SELECT * FROM model_artifact WHERE deleted_at IS NULL
                     ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?
                    """, MAPPER, limit, offset);
        }
        return jdbc.query("""
                SELECT * FROM model_artifact WHERE source = ? AND deleted_at IS NULL
                 ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?
                """, MAPPER, source, limit, offset);
    }

    public long countAll(String source) {
        Long count;
        if (source == null || source.isBlank()) {
            count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM model_artifact WHERE deleted_at IS NULL", Long.class);
        } else {
            count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM model_artifact WHERE source = ? AND deleted_at IS NULL",
                    Long.class, source);
        }
        return count == null ? 0L : count;
    }

    public void softDelete(long id, long deletedAt) {
        jdbc.update("UPDATE model_artifact SET deleted_at = ? WHERE id = ?", deletedAt, id);
    }
}
