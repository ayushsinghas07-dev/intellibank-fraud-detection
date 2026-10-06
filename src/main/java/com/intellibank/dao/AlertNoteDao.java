package com.intellibank.dao;

import com.intellibank.config.DatabaseConfig;
import com.intellibank.model.AlertNote;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AlertNoteDao {

    public List<AlertNote> findByAlertId(Long alertId) {
        List<AlertNote> list = new ArrayList<>();
        String sql = "SELECT an.*, u.username FROM alert_notes an JOIN users u ON an.user_id = u.id WHERE an.alert_id = ? ORDER BY an.created_at ASC";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, alertId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    AlertNote note = new AlertNote();
                    note.setId(rs.getLong("id"));
                    note.setAlertId(rs.getLong("alert_id"));
                    note.setUserId(rs.getLong("user_id"));
                    note.setNoteText(rs.getString("note_text"));
                    Timestamp ts = rs.getTimestamp("created_at");
                    if (ts != null) note.setCreatedAt(ts.toLocalDateTime());
                    note.setUsername(rs.getString("username"));
                    list.add(note);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching notes for alert: " + alertId, e);
        }
        return list;
    }

    public AlertNote create(Connection conn, AlertNote note) throws SQLException {
        String sql = "INSERT INTO alert_notes (alert_id, user_id, note_text) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, note.getAlertId());
            pstmt.setLong(2, note.getUserId());
            pstmt.setString(3, note.getNoteText());
            pstmt.executeUpdate();
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) note.setId(rs.getLong(1));
            }
        }
        return note;
    }
}
