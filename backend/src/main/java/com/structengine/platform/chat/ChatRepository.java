package com.structengine.platform.chat;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ChatRepository {

    private static final RowMapper<ChatMessage> MAPPER = (rs, rowNum) -> new ChatMessage(
            rs.getLong("id"),
            rs.getLong("server_id"),
            rs.getString("session_id"),
            rs.getString("actor_type"),
            rs.getLong("actor_id"),
            rs.getString("role"),
            rs.getString("content"),
            rs.getLong("created_at")
    );

    private final JdbcTemplate jdbc;

    public ChatRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(ChatMessage message) {
        jdbc.update("""
                INSERT INTO chat_message
                (id, server_id, session_id, actor_type, actor_id, role, content, created_at)
                VALUES (?,?,?,?,?,?,?,?)
                """, message.id(), message.serverId(), message.sessionId(),
                message.actorType(), message.actorId(), message.role(),
                message.content(), message.createdAt());
    }

    public List<ChatMessage> list(long serverId, String actorType, long actorId, String sessionId) {
        return jdbc.query("""
                SELECT * FROM chat_message
                 WHERE server_id = ? AND actor_type = ? AND actor_id = ? AND session_id = ?
                 ORDER BY id
                """, MAPPER, serverId, actorType, actorId, sessionId);
    }
}
