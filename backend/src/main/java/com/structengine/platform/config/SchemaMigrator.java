package com.structengine.platform.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * SQLite schema 版本迁移。
 *
 * <p>Phase 1 创建账号表；Phase 2 创建 minecraft_server；后续 Phase 3 继续追加
 * model/task/chat/audit 表。旧业务表由 {@code PlatformStore} 继续维护，
 * 待模型产物改造时再统一迁移。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SchemaMigrator implements ApplicationRunner {

    private static final int CURRENT_VERSION = 2;

    private final JdbcTemplate jdbc;

    public SchemaMigrator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbc.execute("PRAGMA foreign_keys = ON");
        Integer version = jdbc.queryForObject("PRAGMA user_version", Integer.class);
        int current = version == null ? 0 : version;
        if (current >= CURRENT_VERSION) {
            return;
        }
        if (current < 1) {
            createAccountTables();
        }
        if (current < 2) {
            createServerTable();
        }
        jdbc.execute("PRAGMA user_version = " + CURRENT_VERSION);
    }

    private void createServerTable() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS minecraft_server (
                  id                 INTEGER PRIMARY KEY,
                  owner_user_id      INTEGER NOT NULL,
                  name               TEXT    NOT NULL,
                  secret_hash        TEXT    NOT NULL UNIQUE,
                  status             TEXT    NOT NULL DEFAULT 'PENDING'
                                              CHECK (status IN ('PENDING', 'ONLINE', 'OFFLINE', 'DISABLED')),
                  blue_map_url       TEXT,
                  map_id             TEXT,
                  last_seen_at       INTEGER,
                  registered_at      INTEGER,
                  secret_rotated_at  INTEGER,
                  deleted_at         INTEGER,
                  created_at         INTEGER NOT NULL,
                  updated_at         INTEGER NOT NULL,
                  FOREIGN KEY (owner_user_id) REFERENCES app_user(id) ON DELETE RESTRICT
                )
                """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_server_owner ON minecraft_server(owner_user_id)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_server_status ON minecraft_server(status)");
    }

    private void createAccountTables() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS admin_account (
                  id                   INTEGER PRIMARY KEY,
                  username             TEXT    NOT NULL COLLATE NOCASE UNIQUE,
                  password_hash        TEXT    NOT NULL,
                  token_version        INTEGER NOT NULL DEFAULT 0,
                  must_change_password INTEGER NOT NULL DEFAULT 0,
                  created_at           INTEGER NOT NULL,
                  updated_at           INTEGER NOT NULL,
                  last_login_at        INTEGER
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS app_user (
                  id                   INTEGER PRIMARY KEY,
                  username             TEXT    NOT NULL COLLATE NOCASE UNIQUE,
                  password_hash        TEXT    NOT NULL,
                  status               TEXT    NOT NULL DEFAULT 'ACTIVE'
                                               CHECK (status IN ('ACTIVE', 'DISABLED')),
                  token_version        INTEGER NOT NULL DEFAULT 0,
                  created_at           INTEGER NOT NULL,
                  updated_at           INTEGER NOT NULL,
                  last_login_at        INTEGER
                )
                """);
    }
}
