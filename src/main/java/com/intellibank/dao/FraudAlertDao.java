package com.intellibank.dao;

import com.intellibank.config.DatabaseConfig;
import com.intellibank.model.FraudAlert;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FraudAlertDao {

    public FraudAlert create(Connection conn, FraudAlert alert) throws SQLException {
        String sql = "INSERT INTO fraud_alerts (alert_number, transaction_id, customer_id, risk_score, risk_level, status, assigned_to_user_id, triggered_rules_json, evidence_json) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, alert.getAlertNumber());
            pstmt.setLong(2, alert.getTransactionId());
            pstmt.setLong(3, alert.getCustomerId());
            pstmt.setInt(4, alert.getRiskScore());
            pstmt.setString(5, alert.getRiskLevel());
            pstmt.setString(6, alert.getStatus() != null ? alert.getStatus() : "OPEN");
            if (alert.getAssignedToUserId() != null) pstmt.setLong(7, alert.getAssignedToUserId()); else pstmt.setNull(7, Types.BIGINT);
            pstmt.setString(8, alert.getTriggeredRulesJson());
            pstmt.setString(9, alert.getEvidenceJson());
            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) alert.setId(rs.getLong(1));
            }
        }
        return alert;
    }

    public Optional<FraudAlert> findById(Long id) {
        String sql = "SELECT fa.*, CONCAT(c.first_name, ' ', c.last_name) as cust_name, t.reference_number as txn_ref, u.username as assigned_user " +
                "FROM fraud_alerts fa JOIN customers c ON fa.customer_id = c.id JOIN transactions t ON fa.transaction_id = t.id LEFT JOIN users u ON fa.assigned_to_user_id = u.id WHERE fa.id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapResultSetToAlert(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding fraud alert by ID: " + id, e);
        }
        return Optional.empty();
    }

    public List<FraudAlert> search(String query, String status, String riskLevel, int offset, int limit) {
        List<FraudAlert> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT fa.*, CONCAT(c.first_name, ' ', c.last_name) as cust_name, t.reference_number as txn_ref, u.username as assigned_user " +
                "FROM fraud_alerts fa JOIN customers c ON fa.customer_id = c.id JOIN transactions t ON fa.transaction_id = t.id LEFT JOIN users u ON fa.assigned_to_user_id = u.id WHERE 1=1"
        );
        List<Object> params = new ArrayList<>();

        if (query != null && !query.trim().isEmpty()) {
            sql.append(" AND (fa.alert_number LIKE ? OR c.first_name LIKE ? OR c.last_name LIKE ? OR t.reference_number LIKE ?)");
            String q = "%" + query.trim() + "%";
            params.add(q); params.add(q); params.add(q); params.add(q);
        }
        if (status != null && !status.trim().isEmpty() && !"ALL".equalsIgnoreCase(status)) {
            sql.append(" AND fa.status = ?");
            params.add(status.trim());
        }
        if (riskLevel != null && !riskLevel.trim().isEmpty() && !"ALL".equalsIgnoreCase(riskLevel)) {
            sql.append(" AND fa.risk_level = ?");
            params.add(riskLevel.trim());
        }

        sql.append(" ORDER BY fa.created_at DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) list.add(mapResultSetToAlert(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error searching fraud alerts", e);
        }
        return list;
    }

    public int countSearch(String query, String status, String riskLevel) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM fraud_alerts fa JOIN customers c ON fa.customer_id = c.id JOIN transactions t ON fa.transaction_id = t.id WHERE 1=1"
        );
        List<Object> params = new ArrayList<>();

        if (query != null && !query.trim().isEmpty()) {
            sql.append(" AND (fa.alert_number LIKE ? OR c.first_name LIKE ? OR c.last_name LIKE ? OR t.reference_number LIKE ?)");
            String q = "%" + query.trim() + "%";
            params.add(q); params.add(q); params.add(q); params.add(q);
        }
        if (status != null && !status.trim().isEmpty() && !"ALL".equalsIgnoreCase(status)) {
            sql.append(" AND fa.status = ?");
            params.add(status.trim());
        }
        if (riskLevel != null && !riskLevel.trim().isEmpty() && !"ALL".equalsIgnoreCase(riskLevel)) {
            sql.append(" AND fa.risk_level = ?");
            params.add(riskLevel.trim());
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
            throw new RuntimeException("Error counting fraud alerts", e);
        }
        return 0;
    }

    public Map<String, Integer> getStatusCounts() {
        Map<String, Integer> map = new HashMap<>();
        String sql = "SELECT status, COUNT(*) FROM fraud_alerts GROUP BY status";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                map.put(rs.getString(1), rs.getInt(2));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching fraud alert status counts", e);
        }
        return map;
    }

    public boolean updateStatus(Connection conn, Long alertId, String newStatus, Long userId) throws SQLException {
        String sql = "UPDATE fraud_alerts SET status = ?, assigned_to_user_id = COALESCE(?, assigned_to_user_id), " +
                "resolved_at = CASE WHEN ? IN ('RESOLVED', 'CONFIRMED_FRAUD', 'FALSE_POSITIVE') THEN CURRENT_TIMESTAMP ELSE resolved_at END " +
                "WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newStatus);
            if (userId != null) pstmt.setLong(2, userId); else pstmt.setNull(2, Types.BIGINT);
            pstmt.setString(3, newStatus);
            pstmt.setLong(4, alertId);
            return pstmt.executeUpdate() > 0;
        }
    }

    private FraudAlert mapResultSetToAlert(ResultSet rs) throws SQLException {
        FraudAlert fa = new FraudAlert();
        fa.setId(rs.getLong("id"));
        fa.setAlertNumber(rs.getString("alert_number"));
        fa.setTransactionId(rs.getLong("transaction_id"));
        fa.setCustomerId(rs.getLong("customer_id"));
        fa.setRiskScore(rs.getInt("risk_score"));
        fa.setRiskLevel(rs.getString("risk_level"));
        fa.setStatus(rs.getString("status"));

        long user = rs.getLong("assigned_to_user_id");
        if (!rs.wasNull()) fa.setAssignedToUserId(user);

        fa.setTriggeredRulesJson(rs.getString("triggered_rules_json"));
        fa.setEvidenceJson(rs.getString("evidence_json"));

        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) fa.setCreatedAt(created.toLocalDateTime());
        Timestamp updated = rs.getTimestamp("updated_at");
        if (updated != null) fa.setUpdatedAt(updated.toLocalDateTime());
        Timestamp resolved = rs.getTimestamp("resolved_at");
        if (resolved != null) fa.setResolvedAt(resolved.toLocalDateTime());

        try {
            fa.setCustomerName(rs.getString("cust_name"));
            fa.setTransactionReference(rs.getString("txn_ref"));
            fa.setAssignedUsername(rs.getString("assigned_user"));
        } catch (SQLException ignored) {}

        return fa;
    }
}
