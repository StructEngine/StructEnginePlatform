package com.structengine.platform.account;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * admin_account 数据访问。
 */
@Repository
public class AdminAccountRepository {

    private static final RowMapper<AdminAccount> MAPPER = (rs, rowNum) -> new AdminAccount(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("password_hash"),
            rs.getInt("token_version"),
            rs.getInt("must_change_password") != 0,
            rs.getLong("created_at"),
            rs.getLong("updated_at"),
            rs.getObject("last_login_at") == null ? null : rs.getLong("last_login_at")
    );

    private final JdbcTemplate jdbc;

    public AdminAccountRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AdminAccount> findByUsername(String username) {
        List<AdminAccount> rows = jdbc.query(
                "SELECT * FROM admin_account WHERE username = ? COLLATE NOCASE",
                MAPPER, username);
        return rows.stream().findFirst();
    }

    public Optional<AdminAccount> findById(long id) {
        List<AdminAccount> rows = jdbc.query("SELECT * FROM admin_account WHERE id = ?", MAPPER, id);
        return rows.stream().findFirst();
    }

    public boolean existsAny() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM admin_account", Integer.class);
        return count != null && count > 0;
    }

    public void insert(AdminAccount account) {
        jdbc.update("""
                INSERT INTO admin_account
                (id, username, password_hash, token_version, must_change_password,
                 created_at, updated_at, last_login_at)
                VALUES (?,?,?,?,?,?,?,?)
                """, account.id(), account.username(), account.passwordHash(), account.tokenVersion(),
                account.mustChangePassword() ? 1 : 0, account.createdAt(), account.updatedAt(),
                account.lastLoginAt());
    }

    public void updatePassword(long id, String passwordHash, boolean mustChangePassword,
                               int tokenVersion, long updatedAt) {
        jdbc.update("""
                UPDATE admin_account
                   SET password_hash = ?, must_change_password = ?, token_version = ?, updated_at = ?
                 WHERE id = ?
                """, passwordHash, mustChangePassword ? 1 : 0, tokenVersion, updatedAt, id);
    }

    public void updateLastLogin(long id, long lastLoginAt) {
        jdbc.update("UPDATE admin_account SET last_login_at = ? WHERE id = ?", lastLoginAt, id);
    }
}
