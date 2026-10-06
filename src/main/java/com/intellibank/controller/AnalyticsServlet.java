package com.intellibank.controller;
import com.intellibank.util.JsonUtil;

import com.google.gson.Gson;
import com.intellibank.config.DatabaseConfig;
import com.intellibank.dto.ApiResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class AnalyticsServlet extends HttpServlet {
    private static final Gson gson = JsonUtil.getGson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        String path = req.getPathInfo();

        try (Connection conn = DatabaseConfig.getConnection()) {
            if ("/kpis".equalsIgnoreCase(path)) {
                resp.getWriter().write(gson.toJson(ApiResponse.success(getKpis(conn))));
            } else if ("/volume-value".equalsIgnoreCase(path)) {
                resp.getWriter().write(gson.toJson(ApiResponse.success(getVolumeAndValue(conn))));
            } else if ("/risk-distribution".equalsIgnoreCase(path)) {
                resp.getWriter().write(gson.toJson(ApiResponse.success(getRiskDistribution(conn))));
            } else if ("/channels".equalsIgnoreCase(path)) {
                resp.getWriter().write(gson.toJson(ApiResponse.success(getChannelBreakdown(conn))));
            } else if ("/types".equalsIgnoreCase(path)) {
                resp.getWriter().write(gson.toJson(ApiResponse.success(getTypeBreakdown(conn))));
            } else if ("/high-risk-accounts".equalsIgnoreCase(path)) {
                resp.getWriter().write(gson.toJson(ApiResponse.success(getHighRiskAccounts(conn))));
            } else {
                Map<String, Object> all = new HashMap<>();
                all.put("kpis", getKpis(conn));
                all.put("volumeValue", getVolumeAndValue(conn));
                all.put("riskDistribution", getRiskDistribution(conn));
                all.put("channels", getChannelBreakdown(conn));
                all.put("types", getTypeBreakdown(conn));
                all.put("highRiskAccounts", getHighRiskAccounts(conn));
                resp.getWriter().write(gson.toJson(ApiResponse.success(all)));
            }
        } catch (SQLException e) {
            resp.setStatus(500);
            resp.getWriter().write(gson.toJson(ApiResponse.error("ANALYTICS_ERROR", e.getMessage())));
        }
    }

    private Map<String, Object> getKpis(Connection conn) throws SQLException {
        Map<String, Object> kpis = new HashMap<>();

        try (PreparedStatement pstmt = conn.prepareStatement("SELECT COUNT(*) FROM customers WHERE status = 'ACTIVE'");
             ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) kpis.put("totalCustomers", rs.getInt(1));
        }
        try (PreparedStatement pstmt = conn.prepareStatement("SELECT COUNT(*) FROM accounts WHERE status = 'ACTIVE'");
             ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) kpis.put("activeAccounts", rs.getInt(1));
        }
        try (PreparedStatement pstmt = conn.prepareStatement("SELECT COUNT(*), COALESCE(SUM(amount),0) FROM transactions WHERE DATE(transaction_timestamp) = CURDATE()");
             ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) {
                kpis.put("transactionsToday", rs.getInt(1));
                kpis.put("valueToday", rs.getBigDecimal(2));
            }
        }
        try (PreparedStatement pstmt = conn.prepareStatement("SELECT COUNT(*) FROM transactions WHERE risk_level IN ('HIGH', 'CRITICAL')");
             ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) kpis.put("highRiskCount", rs.getInt(1));
        }
        try (PreparedStatement pstmt = conn.prepareStatement("SELECT COUNT(*) FROM transactions WHERE fraud_status = 'SUSPICIOUS'");
             ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) kpis.put("suspiciousCount", rs.getInt(1));
        }
        try (PreparedStatement pstmt = conn.prepareStatement("SELECT COUNT(*) FROM fraud_alerts WHERE status = 'OPEN'");
             ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) kpis.put("openAlerts", rs.getInt(1));
        }
        try (PreparedStatement pstmt = conn.prepareStatement("SELECT COUNT(*) FROM transactions WHERE status = 'UNDER_REVIEW'");
             ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) kpis.put("underReviewCount", rs.getInt(1));
        }

        return kpis;
    }

    private List<Map<String, Object>> getVolumeAndValue(Connection conn) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT DATE(transaction_timestamp) as txn_date, COUNT(*) as txn_count, SUM(amount) as total_value " +
                "FROM transactions GROUP BY DATE(transaction_timestamp) ORDER BY txn_date ASC LIMIT 30";
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> m = new HashMap<>();
                m.put("date", rs.getString("txn_date"));
                m.put("count", rs.getInt("txn_count"));
                m.put("value", rs.getBigDecimal("total_value"));
                list.add(m);
            }
        }
        return list;
    }

    private Map<String, Object> getRiskDistribution(Connection conn) throws SQLException {
        Map<String, Object> map = new HashMap<>();
        String sql = "SELECT risk_level, COUNT(*) as cnt FROM transactions GROUP BY risk_level";
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                map.put(rs.getString("risk_level"), rs.getInt("cnt"));
            }
        }
        return map;
    }

    private Map<String, Object> getChannelBreakdown(Connection conn) throws SQLException {
        Map<String, Object> map = new HashMap<>();
        String sql = "SELECT channel, COUNT(*) as cnt FROM transactions GROUP BY channel";
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) map.put(rs.getString("channel"), rs.getInt("cnt"));
        }
        return map;
    }

    private Map<String, Object> getTypeBreakdown(Connection conn) throws SQLException {
        Map<String, Object> map = new HashMap<>();
        String sql = "SELECT transaction_type, COUNT(*) as cnt FROM transactions GROUP BY transaction_type";
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) map.put(rs.getString("transaction_type"), rs.getInt("cnt"));
        }
        return map;
    }

    private List<Map<String, Object>> getHighRiskAccounts(Connection conn) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT a.account_number, CONCAT(c.first_name, ' ', c.last_name) as customer_name, COUNT(t.id) as high_risk_txns " +
                "FROM transactions t JOIN accounts a ON t.source_account_id = a.id JOIN customers c ON a.customer_id = c.id " +
                "WHERE t.risk_level IN ('HIGH', 'CRITICAL') GROUP BY a.account_number, customer_name ORDER BY high_risk_txns DESC LIMIT 5";
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> m = new HashMap<>();
                m.put("accountNumber", rs.getString("account_number"));
                m.put("customerName", rs.getString("customer_name"));
                m.put("highRiskTxns", rs.getInt("high_risk_txns"));
                list.add(m);
            }
        }
        return list;
    }
}
