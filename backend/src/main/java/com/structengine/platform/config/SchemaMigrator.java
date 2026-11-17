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
 * <p>Phase 1 创建账号表；Phase 2 创建 minecraft_server；Phase 3 把旧模型/任务/对话表
 * 迁移为带归属的 model_artifact/build_task/chat_message。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SchemaMigrator implements ApplicationRunner {

    private static final int CURRENT_VERSION = 3;

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
        if (current < 3) {
            createArtifactTables();
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

    private void createArtifactTables() {
        dropEmptyLegacyTable("model_draft");
        dropEmptyLegacyTable("build_task");
        dropEmptyLegacyTable("chat_message");

        jdbc.execute("""
                CREATE TABLE model_artifact (
                  id              INTEGER PRIMARY KEY,
                  owner_user_id   INTEGER NOT NULL,
                  server_id       INTEGER NOT NULL,
                  source          TEXT    NOT NULL
                                          CHECK (source IN ('MAP_AI', 'REFERENCE_IMAGE')),
                  prompt          TEXT,
                  model_url       TEXT    NOT NULL,
                  block_count     INTEGER NOT NULL,
                  min_x           INTEGER NOT NULL,
                  max_x           INTEGER NOT NULL,
                  min_y           INTEGER NOT NULL,
                  max_y           INTEGER NOT NULL,
                  min_z           INTEGER NOT NULL,
                  max_z           INTEGER NOT NULL,
                  created_at      INTEGER NOT NULL,
                  deleted_at      INTEGER,
                  FOREIGN KEY (owner_user_id) REFERENCES app_user(id) ON DELETE RESTRICT,
                  FOREIGN KEY (server_id) REFERENCES minecraft_server(id) ON DELETE RESTRICT
                )
                """);
        jdbc.execute("""
                CREATE TABLE build_task (
                  id              INTEGER PRIMARY KEY,
                  owner_user_id   INTEGER NOT NULL,
                  server_id       INTEGER NOT NULL,
                  model_id        INTEGER NOT NULL,
                  player_uuid     TEXT,
                  player_name     TEXT    NOT NULL,
                  center_x        INTEGER NOT NULL,
                  center_y        INTEGER NOT NULL,
                  center_z        INTEGER NOT NULL,
                  origin_x        INTEGER NOT NULL,
                  origin_y        INTEGER NOT NULL,
                  origin_z        INTEGER NOT NULL,
                  rotation        INTEGER NOT NULL DEFAULT 0,
                  status          TEXT    NOT NULL DEFAULT 'QUEUED',
                  placed          INTEGER NOT NULL DEFAULT 0,
                  total           INTEGER NOT NULL DEFAULT 0,
                  error           TEXT,
                  created_at      INTEGER NOT NULL,
                  updated_at      INTEGER NOT NULL,
                  FOREIGN KEY (owner_user_id) REFERENCES app_user(id) ON DELETE RESTRICT,
                  FOREIGN KEY (server_id) REFERENCES minecraft_server(id) ON DELETE RESTRICT,
                  FOREIGN KEY (model_id) REFERENCES model_artifact(id) ON DELETE RESTRICT
                )
                """);
        jdbc.execute("""
                CREATE TABLE chat_message (
                  id              INTEGER PRIMARY KEY,
                  server_id       INTEGER NOT NULL,
                  session_id      TEXT    NOT NULL,
                  actor_type      TEXT    NOT NULL CHECK (actor_type IN ('ADMIN', 'USER')),
                  actor_id        INTEGER NOT NULL,
                  role            TEXT    NOT NULL CHECK (role IN ('user', 'assistant')),
                  content         TEXT    NOT NULL,
                  created_at      INTEGER NOT NULL,
                  FOREIGN KEY (server_id) REFERENCES minecraft_server(id) ON DELETE RESTRICT
                )
                """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_model_owner ON model_artifact(owner_user_id, created_at DESC)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_model_server ON model_artifact(server_id, created_at DESC)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_task_owner ON build_task(owner_user_id, created_at DESC)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_task_server ON build_task(server_id, created_at DESC)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_chat_actor_server ON chat_message(actor_type, actor_id, server_id, session_id, created_at)");
    }

    private void dropEmptyLegacyTable(String table) {
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?",
                Integer.class, table);
        if (exists == null || exists == 0) {
            return;
        }
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        if (count != null && count > 0) {
            throw new IllegalStateException("旧表 " + table + " 非空，无法自动迁移到 v3；请先导出/备份数据");
        }
        jdbc.execute("DROP TABLE " + table);
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
