package com.structengine.platform.service;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * SQLite 持久化：模型草稿、部署任务、AI 对话。
 */
@Service
public class PlatformStore {

    private final JdbcTemplate jdbc;

    public PlatformStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void init() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS model_draft (
                  id TEXT PRIMARY KEY,
                  server_id TEXT,
                  player_uuid TEXT,
                  player_name TEXT,
                  prompt TEXT,
                  model_url TEXT,
                  block_count INTEGER,
                  min_x INTEGER, max_x INTEGER,
                  min_y INTEGER, max_y INTEGER,
                  min_z INTEGER, max_z INTEGER,
                  created_at INTEGER
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS build_task (
                  id TEXT PRIMARY KEY,
                  server_id TEXT,
                  player_uuid TEXT,
                  player_name TEXT,
                  model_id TEXT,
                  center_x INTEGER, center_y INTEGER, center_z INTEGER,
                  origin_x INTEGER, origin_y INTEGER, origin_z INTEGER,
                  rotation INTEGER,
                  status TEXT,
                  placed INTEGER,
                  total INTEGER,
                  error TEXT,
                  created_at INTEGER,
                  updated_at INTEGER
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS chat_message (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  session_id TEXT NOT NULL,
                  role TEXT NOT NULL,
                  content TEXT NOT NULL,
                  created_at INTEGER
                )
                """);
    }

    public void saveModel(ModelRecord model) {
        jdbc.update("""
                INSERT OR REPLACE INTO model_draft
                (id, server_id, player_uuid, player_name, prompt, model_url, block_count,
                 min_x, max_x, min_y, max_y, min_z, max_z, created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, model.id(), model.serverId(), model.playerUuid(), model.playerName(), model.prompt(),
                model.modelUrl(), model.blockCount(), model.minX(), model.maxX(), model.minY(),
                model.maxY(), model.minZ(), model.maxZ(), model.createdAt());
    }

    public Optional<ModelRecord> findModel(String id) {
        List<ModelRecord> rows = jdbc.query("SELECT * FROM model_draft WHERE id=?", (rs, n) ->
                new ModelRecord(rs.getString("id"), rs.getString("server_id"), rs.getString("player_uuid"),
                        rs.getString("player_name"), rs.getString("prompt"), rs.getString("model_url"),
                        rs.getInt("block_count"), rs.getInt("min_x"), rs.getInt("max_x"),
                        rs.getInt("min_y"), rs.getInt("max_y"), rs.getInt("min_z"),
                        rs.getInt("max_z"), rs.getLong("created_at")), id);
        return rows.stream().findFirst();
    }

    public void saveTask(TaskRecord task) {
        jdbc.update("""
                INSERT OR REPLACE INTO build_task
                (id, server_id, player_uuid, player_name, model_id,
                 center_x, center_y, center_z, origin_x, origin_y, origin_z, rotation,
                 status, placed, total, error, created_at, updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, task.id(), task.serverId(), task.playerUuid(), task.playerName(), task.modelId(),
                task.centerX(), task.centerY(), task.centerZ(), task.originX(), task.originY(),
                task.originZ(), task.rotation(), task.status(), task.placed(), task.total(),
                task.error(), task.createdAt(), task.updatedAt());
    }

    public void updateTask(String id, String status, int placed, int total, String error) {
        jdbc.update("UPDATE build_task SET status=?, placed=?, total=?, error=?, updated_at=? WHERE id=?",
                status, placed, total, error, System.currentTimeMillis(), id);
    }

    public Optional<TaskRecord> findTask(String id) {
        List<TaskRecord> rows = jdbc.query("SELECT * FROM build_task WHERE id=?", (rs, n) ->
                new TaskRecord(rs.getString("id"), rs.getString("server_id"), rs.getString("player_uuid"),
                        rs.getString("player_name"), rs.getString("model_id"),
                        rs.getInt("center_x"), rs.getInt("center_y"), rs.getInt("center_z"),
                        rs.getInt("origin_x"), rs.getInt("origin_y"), rs.getInt("origin_z"),
                        rs.getInt("rotation"), rs.getString("status"), rs.getInt("placed"),
                        rs.getInt("total"), rs.getString("error"), rs.getLong("created_at"),
                        rs.getLong("updated_at")), id);
        return rows.stream().findFirst();
    }

    public List<TaskRecord> listTasks(String serverId, String playerUuid) {
        String sql = "SELECT * FROM build_task WHERE server_id=? AND player_uuid=? ORDER BY created_at DESC LIMIT 50";
        return jdbc.query(sql, (rs, n) ->
                new TaskRecord(rs.getString("id"), rs.getString("server_id"), rs.getString("player_uuid"),
                        rs.getString("player_name"), rs.getString("model_id"),
                        rs.getInt("center_x"), rs.getInt("center_y"), rs.getInt("center_z"),
                        rs.getInt("origin_x"), rs.getInt("origin_y"), rs.getInt("origin_z"),
                        rs.getInt("rotation"), rs.getString("status"), rs.getInt("placed"),
                        rs.getInt("total"), rs.getString("error"), rs.getLong("created_at"),
                        rs.getLong("updated_at")), serverId, playerUuid);
    }

    public void appendChat(String sessionId, String role, String content) {
        jdbc.update("INSERT INTO chat_message(session_id, role, content, created_at) VALUES (?,?,?,?)",
                sessionId, role, content, System.currentTimeMillis());
    }

    public List<ChatRecord> chatHistory(String sessionId) {
        return jdbc.query("SELECT role, content, created_at FROM chat_message WHERE session_id=? ORDER BY id",
                (rs, n) -> new ChatRecord(rs.getString("role"), rs.getString("content"),
                        rs.getLong("created_at")), sessionId);
    }

    public record ModelRecord(String id, String serverId, String playerUuid, String playerName,
                              String prompt, String modelUrl, int blockCount,
                              int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
                              long createdAt) {
    }

    public record TaskRecord(String id, String serverId, String playerUuid, String playerName,
                             String modelId, int centerX, int centerY, int centerZ,
                             int originX, int originY, int originZ, int rotation,
                             String status, int placed, int total, String error,
                             long createdAt, long updatedAt) {
    }

    public record ChatRecord(String role, String content, long createdAt) {
    }
}
