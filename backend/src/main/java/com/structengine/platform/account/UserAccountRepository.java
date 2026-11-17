package com.structengine.platform.account;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * app_user 数据访问。
 */
@Repository
public class UserAccountRepository {

    private static final RowMapper<AppUser> MAPPER = (rs, rowNum) -> new AppUser(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("password_hash"),
            rs.getString("status"),
            rs.getInt("token_version"),
            rs.getLong("created_at"),
            rs.getLong("updated_at"),
            rs.getObject("last_login_at") == null ? null : rs.getLong("last_login_at")
    );

    private final JdbcTemplate jdbc;

    public UserAccountRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AppUser> findById(long id) {
        List<AppUser> rows = jdbc.query("SELECT * FROM app_user WHERE id = ?", MAPPER, id);
        return rows.stream().findFirst();
    }

    public Optional<AppUser> findByUsername(String username) {
        List<AppUser> rows = jdbc.query(
                "SELECT * FROM app_user WHERE username = ? COLLATE NOCASE", MAPPER, username);
        return rows.stream().findFirst();
    }

    public boolean existsByUsername(String username) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM app_user WHERE username = ? COLLATE NOCASE",
                Integer.class, username);
        return count != null && count > 0;
    }

    public void insert(AppUser user) {
        jdbc.update("""
                INSERT INTO app_user
                (id, username, password_hash, status, token_version, created_at, updated_at, last_login_at)
                VALUES (?,?,?,?,?,?,?,?)
                """, user.id(), user.username(), user.passwordHash(), user.status(),
                user.tokenVersion(), user.createdAt(), user.updatedAt(), user.lastLoginAt());
    }

    public List<AppUser> list(int offset, int limit, String keyword) {
        String like = keyword == null || keyword.isBlank()
                ? "%" : "%" + keyword.trim() + "%";
        return jdbc.query("""
                SELECT * FROM app_user
                 WHERE username LIKE ? COLLATE NOCASE
                 ORDER BY created_at DESC, id DESC
                 LIMIT ? OFFSET ?
                """, MAPPER, like, limit, offset);
    }

    public long count(String keyword) {
        String like = keyword == null || keyword.isBlank()
                ? "%" : "%" + keyword.trim() + "%";
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM app_user WHERE username LIKE ? COLLATE NOCASE",
                Long.class, like);
        return count == null ? 0L : count;
    }

    public void updateUsername(long id, String username, long updatedAt) {
        jdbc.update("UPDATE app_user SET username = ?, updated_at = ? WHERE id = ?",
                username, updatedAt, id);
    }

    public void updateStatus(long id, String status, int tokenVersion, long updatedAt) {
        jdbc.update("UPDATE app_user SET status = ?, token_version = ?, updated_at = ? WHERE id = ?",
                status, tokenVersion, updatedAt, id);
    }

    public void updatePassword(long id, String passwordHash, int tokenVersion, long updatedAt) {
        jdbc.update("""
                UPDATE app_user
                   SET password_hash = ?, token_version = ?, updated_at = ?
                 WHERE id = ?
                """, passwordHash, tokenVersion, updatedAt, id);
    }

    public void updateLastLogin(long id, long lastLoginAt) {
        jdbc.update("UPDATE app_user SET last_login_at = ? WHERE id = ?", lastLoginAt, id);
    }

    public void delete(long id) {
        jdbc.update("DELETE FROM app_user WHERE id = ?", id);
    }
}
