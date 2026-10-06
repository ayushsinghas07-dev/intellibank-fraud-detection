package com.intellibank.dao;

import com.intellibank.config.DatabaseConfig;
import com.intellibank.model.RiskScore;

import java.sql.*;
import java.util.Optional;

public class RiskScoreDao {

    public RiskScore create(Connection conn, RiskScore rs) throws SQLException {
        String sql = "INSERT INTO risk_scores (transaction_id, score, level, triggered_rules_json, evidence_json, recommended_action) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, rs.getTransactionId());
            pstmt.setInt(2, rs.getScore());
            pstmt.setString(3, rs.getLevel());
            pstmt.setString(4, rs.getTriggeredRulesJson());
            pstmt.setString(5, rs.getEvidenceJson());
            pstmt.setString(6, rs.getRecommendedAction());
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                if (keys.next()) rs.setId(keys.getLong(1));
            }
        }
        return rs;
    }

    public Optional<RiskScore> findByTransactionId(Long transactionId) {
        String sql = "SELECT * FROM risk_scores WHERE transaction_id = ? ORDER BY id DESC LIMIT 1";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, transactionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    RiskScore r = new RiskScore();
                    r.setId(rs.getLong("id"));
                    r.setTransactionId(rs.getLong("transaction_id"));
                    r.setScore(rs.getInt("score"));
                    r.setLevel(rs.getString("level"));
                    r.setTriggeredRulesJson(rs.getString("triggered_rules_json"));
                    r.setEvidenceJson(rs.getString("evidence_json"));
                    r.setRecommendedAction(rs.getString("recommended_action"));
                    Timestamp ts = rs.getTimestamp("evaluated_at");
                    if (ts != null) r.setEvaluatedAt(ts.toLocalDateTime());
                    return Optional.of(r);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding risk score for transaction: " + transactionId, e);
        }
        return Optional.empty();
    }
}
