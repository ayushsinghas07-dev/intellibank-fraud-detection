package com.intellibank.dao;

import com.intellibank.config.DatabaseConfig;
import com.intellibank.model.FraudRule;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FraudRuleDao {

    public List<FraudRule> findAll() {
        List<FraudRule> list = new ArrayList<>();
        String sql = "SELECT * FROM fraud_rules ORDER BY id ASC";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) list.add(mapResultSetToRule(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching fraud rules", e);
        }
        return list;
    }

    public Optional<FraudRule> findByCode(String code) {
        String sql = "SELECT * FROM fraud_rules WHERE rule_code = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, code);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapResultSetToRule(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding fraud rule by code: " + code, e);
        }
        return Optional.empty();
    }

    public boolean updateRule(String ruleCode, int points, boolean enabled, String thresholdParams) {
        String sql = "UPDATE fraud_rules SET points = ?, enabled = ?, threshold_params = ? WHERE rule_code = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, points);
            pstmt.setBoolean(2, enabled);
            pstmt.setString(3, thresholdParams);
            pstmt.setString(4, ruleCode);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Error updating fraud rule: " + ruleCode, e);
        }
    }

    private FraudRule mapResultSetToRule(ResultSet rs) throws SQLException {
        FraudRule r = new FraudRule();
        r.setId(rs.getLong("id"));
        r.setRuleCode(rs.getString("rule_code"));
        r.setRuleName(rs.getString("rule_name"));
        r.setDescription(rs.getString("description"));
        r.setThresholdParams(rs.getString("threshold_params"));
        r.setPoints(rs.getInt("points"));
        r.setEnabled(rs.getBoolean("enabled"));
        Timestamp updated = rs.getTimestamp("updated_at");
        if (updated != null) r.setUpdatedAt(updated.toLocalDateTime());
        return r;
    }
}
