package com.intellibank.dao;

import com.intellibank.config.DatabaseConfig;
import com.intellibank.model.AuditLog;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AuditLogDao {

    public AuditLog create(Connection conn, AuditLog log) throws SQLException {
        String sql = "INSERT INTO audit_log (user_id, username, role, action, entity_type, entity_id, details_json, ip_address) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (log.getUserId() != null) pstmt.setLong(1, log.getUserId()); else pstmt.setNull(1, Types.BIGINT);
            pstmt.setString(2, log.getUsername());
            pstmt.setString(3, log.getRole());
            pstmt.setString(4, log.getAction());
            pstmt.setString(5, log.getEntityType());
            if (log.getEntityId() != null) pstmt.setLong(6, log.getEntityId()); else pstmt.setNull(6, Types.BIGINT);
            pstmt.setString(7, log.getDetailsJson());
            pstmt.setString(8, log.getIpAddress() != null ? log.getIpAddress() : "127.0.0.1");
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                if (keys.next()) log.setId(keys.getLong(1));
            }
        }
        return log;
    }

    public List<AuditLog> search(String username, String action, String entityType, String startDate, String endDate, int offset, int limit) {
        List<AuditLog> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM audit_log WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (username != null && !username.trim().isEmpty()) {
            sql.append(" AND username LIKE ?");
            params.add("%" + username.trim() + "%");
        }
        if (action != null && !action.trim().isEmpty() && !"ALL".equalsIgnoreCase(action)) {
            sql.append(" AND action = ?");
            params.add(action.trim());
        }
        if (entityType != null && !entityType.trim().isEmpty() && !"ALL".equalsIgnoreCase(entityType)) {
            sql.append(" AND entity_type = ?");
            params.add(entityType.trim());
        }
        if (startDate != null && !startDate.trim().isEmpty()) {
            sql.append(" AND timestamp >= ?");
            params.add(Timestamp.valueOf(startDate + " 00:00:00"));
        }
        if (endDate != null && !endDate.trim().isEmpty()) {
            sql.append(" AND timestamp <= ?");
            params.add(Timestamp.valueOf(endDate + " 23:59:59"));
        }

        sql.append(" ORDER BY timestamp DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) list.add(mapResultSetToAuditLog(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error searching audit log", e);
        }
        return list;
    }

    public int countSearch(String username, String action, String entityType, String startDate, String endDate) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM audit_log WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (username != null && !username.trim().isEmpty()) {
            sql.append(" AND username LIKE ?");
            params.add("%" + username.trim() + "%");
        }
        if (action != null && !action.trim().isEmpty() && !"ALL".equalsIgnoreCase(action)) {
            sql.append(" AND action = ?");
            params.add(action.trim());
        }
        if (entityType != null && !entityType.trim().isEmpty() && !"ALL".equalsIgnoreCase(entityType)) {
            sql.append(" AND entity_type = ?");
            params.add(entityType.trim());
        }
        if (startDate != null && !startDate.trim().isEmpty()) {
            sql.append(" AND timestamp >= ?");
            params.add(Timestamp.valueOf(startDate + " 00:00:00"));
        }
        if (endDate != null && !endDate.trim().isEmpty()) {
            sql.append(" AND timestamp <= ?");
            params.add(Timestamp.valueOf(endDate + " 23:59:59"));
        }

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error counting audit log", e);
        }
        return 0;
    }

    private AuditLog mapResultSetToAuditLog(ResultSet rs) throws SQLException {
        AuditLog al = new AuditLog();
        al.setId(rs.getLong("id"));
        long uid = rs.getLong("user_id");
        if (!rs.wasNull()) al.setUserId(uid);
        al.setUsername(rs.getString("username"));
        al.setRole(rs.getString("role"));
        al.setAction(rs.getString("action"));
        al.setEntityType(rs.getString("entity_type"));
        long eid = rs.getLong("entity_id");
        if (!rs.wasNull()) al.setEntityId(eid);
        al.setDetailsJson(rs.getString("details_json"));
        al.setIpAddress(rs.getString("ip_address"));

        Timestamp ts = rs.getTimestamp("timestamp");
        if (ts != null) al.setTimestamp(ts.toLocalDateTime());
        return al;
    }
}
