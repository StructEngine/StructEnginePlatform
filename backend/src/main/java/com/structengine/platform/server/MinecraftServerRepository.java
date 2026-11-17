package com.structengine.platform.server;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * minecraft_server 数据访问。
 */
@Repository
public class MinecraftServerRepository {

    private static final RowMapper<MinecraftServer> MAPPER = (rs, rowNum) -> new MinecraftServer(
            rs.getLong("id"),
            rs.getLong("owner_user_id"),
            rs.getString("name"),
            rs.getString("secret_hash"),
            rs.getString("status"),
            rs.getString("blue_map_url"),
            rs.getString("map_id"),
            nullableLong(rs.getObject("last_seen_at")),
            nullableLong(rs.getObject("registered_at")),
            nullableLong(rs.getObject("secret_rotated_at")),
            nullableLong(rs.getObject("deleted_at")),
            rs.getLong("created_at"),
            rs.getLong("updated_at")
    );

    private final JdbcTemplate jdbc;

    public MinecraftServerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(MinecraftServer server) {
        jdbc.update("""
                INSERT INTO minecraft_server
                (id, owner_user_id, name, secret_hash, status, blue_map_url, map_id,
                 last_seen_at, registered_at, secret_rotated_at, deleted_at, created_at, updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, server.id(), server.ownerUserId(), server.name(), server.secretHash(),
                server.status(), server.blueMapUrl(), server.mapId(), server.lastSeenAt(),
                server.registeredAt(), server.secretRotatedAt(), server.deletedAt(),
                server.createdAt(), server.updatedAt());
    }

    public Optional<MinecraftServer> findById(long id) {
        List<MinecraftServer> rows = jdbc.query(
                "SELECT * FROM minecraft_server WHERE id = ?", MAPPER, id);
        return rows.stream().findFirst();
    }

    public Optional<MinecraftServer> findBySecretHash(String secretHash) {
        List<MinecraftServer> rows = jdbc.query(
                "SELECT * FROM minecraft_server WHERE secret_hash = ?", MAPPER, secretHash);
        return rows.stream().findFirst();
    }

    public List<MinecraftServer> listByOwner(long ownerUserId) {
        return jdbc.query("""
                SELECT * FROM minecraft_server
                 WHERE owner_user_id = ? AND deleted_at IS NULL
                 ORDER BY created_at DESC, id DESC
                """, MAPPER, ownerUserId);
    }

    public List<MinecraftServer> listAll() {
        return jdbc.query("""
                SELECT * FROM minecraft_server
                 WHERE deleted_at IS NULL
                 ORDER BY created_at DESC, id DESC
                """, MAPPER);
    }

    public void updateName(long id, String name, long updatedAt) {
        jdbc.update("UPDATE minecraft_server SET name = ?, updated_at = ? WHERE id = ?",
                name, updatedAt, id);
    }

    public void updateStatus(long id, String status, long updatedAt) {
        jdbc.update("UPDATE minecraft_server SET status = ?, updated_at = ? WHERE id = ?",
                status, updatedAt, id);
    }

    public void updateRegistration(long id, String blueMapUrl, String mapId,
                                   long registeredAt, long lastSeenAt, long updatedAt) {
        jdbc.update("""
                UPDATE minecraft_server
                   SET blue_map_url = ?, map_id = ?, status = 'ONLINE',
                       registered_at = COALESCE(registered_at, ?), last_seen_at = ?, updated_at = ?
                 WHERE id = ?
                """, blueMapUrl, mapId, registeredAt, lastSeenAt, updatedAt, id);
    }

    public void touch(long id, long lastSeenAt) {
        jdbc.update("UPDATE minecraft_server SET last_seen_at = ? WHERE id = ?", lastSeenAt, id);
    }

    public void updateSecret(long id, String secretHash, long rotatedAt, long updatedAt) {
        jdbc.update("""
                UPDATE minecraft_server
                   SET secret_hash = ?, secret_rotated_at = ?, updated_at = ?
                 WHERE id = ?
                """, secretHash, rotatedAt, updatedAt, id);
    }

    public void softDelete(long id, long updatedAt) {
        jdbc.update("""
                UPDATE minecraft_server
                   SET deleted_at = ?, status = 'DISABLED', updated_at = ?
                 WHERE id = ?
                """, updatedAt, updatedAt, id);
    }

    public void markAllOnlineOffline(long updatedAt) {
        jdbc.update("""
                UPDATE minecraft_server
                   SET status = 'OFFLINE', updated_at = ?
                 WHERE status = 'ONLINE' AND deleted_at IS NULL
                """, updatedAt);
    }

    public long countByOwner(long ownerUserId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM minecraft_server WHERE owner_user_id = ? AND deleted_at IS NULL",
                Long.class, ownerUserId);
        return count == null ? 0L : count;
    }

    private static Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
