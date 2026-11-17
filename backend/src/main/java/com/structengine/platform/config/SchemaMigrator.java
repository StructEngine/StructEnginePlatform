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
 * <p>当前 Phase 1 只创建账号表；后续 Phase 2/3 在同一入口追加 server/model/task
 * 等表并把 {@code user_version} 递增。旧业务表由 {@code PlatformStore} 继续维护，
 * 待服务器归属改造时再统一迁移。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SchemaMigrator implements ApplicationRunner {

    private static final int CURRENT_VERSION = 1;

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
        jdbc.execute("PRAGMA user_version = " + CURRENT_VERSION);
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
