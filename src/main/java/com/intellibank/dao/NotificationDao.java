package com.intellibank.dao;

import com.intellibank.config.DatabaseConfig;
import com.intellibank.model.Notification;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class NotificationDao {

    public Notification create(Connection conn, Notification n) throws SQLException {
        String sql = "INSERT INTO notifications (user_id, role, severity, title, message, read_state, entity_type, entity_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (n.getUserId() != null) pstmt.setLong(1, n.getUserId()); else pstmt.setNull(1, Types.BIGINT);
            pstmt.setString(2, n.getRole());
            pstmt.setString(3, n.getSeverity());
            pstmt.setString(4, n.getTitle());
            pstmt.setString(5, n.getMessage());
            pstmt.setBoolean(6, n.isReadState());
            pstmt.setString(7, n.getEntityType());
            if (n.getEntityId() != null) pstmt.setLong(8, n.getEntityId()); else pstmt.setNull(8, Types.BIGINT);
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                if (keys.next()) n.setId(keys.getLong(1));
            }
        }
        return n;
    }

    public List<Notification> findForUserOrRole(Long userId, String role, int limit) {
        List<Notification> list = new ArrayList<>();
        String sql = "SELECT * FROM notifications WHERE (user_id = ? OR role = ? OR (user_id IS NULL AND role IS NULL)) ORDER BY created_at DESC LIMIT ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (userId != null) pstmt.setLong(1, userId); else pstmt.setNull(1, Types.BIGINT);
            pstmt.setString(2, role);
            pstmt.setInt(3, limit);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) list.add(mapResultSetToNotification(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching notifications", e);
        }
        return list;
    }

    public int countUnreadForUserOrRole(Long userId, String role) {
        String sql = "SELECT COUNT(*) FROM notifications WHERE read_state = false AND (user_id = ? OR role = ? OR (user_id IS NULL AND role IS NULL))";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (userId != null) pstmt.setLong(1, userId); else pstmt.setNull(1, Types.BIGINT);
            pstmt.setString(2, role);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error counting unread notifications", e);
        }
        return 0;
    }

    public boolean markAsRead(Long id) {
        String sql = "UPDATE notifications SET read_state = true WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Error marking notification as read", e);
        }
    }

    public boolean markAllAsReadForUserOrRole(Long userId, String role) {
        String sql = "UPDATE notifications SET read_state = true WHERE read_state = false AND (user_id = ? OR role = ? OR (user_id IS NULL AND role IS NULL))";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (userId != null) pstmt.setLong(1, userId); else pstmt.setNull(1, Types.BIGINT);
            pstmt.setString(2, role);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Error marking all notifications as read", e);
        }
    }

    private Notification mapResultSetToNotification(ResultSet rs) throws SQLException {
        Notification n = new Notification();
        n.setId(rs.getLong("id"));
        long uid = rs.getLong("user_id");
        if (!rs.wasNull()) n.setUserId(uid);
        n.setRole(rs.getString("role"));
        n.setSeverity(rs.getString("severity"));
        n.setTitle(rs.getString("title"));
        n.setMessage(rs.getString("message"));
        n.setReadState(rs.getBoolean("read_state"));
        n.setEntityType(rs.getString("entity_type"));
        long eid = rs.getLong("entity_id");
        if (!rs.wasNull()) n.setEntityId(eid);

        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) n.setCreatedAt(ts.toLocalDateTime());
        return n;
    }
}
