package com.intellibank.dao;

import com.intellibank.config.DatabaseConfig;
import com.intellibank.model.Transaction;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TransactionDao {

    public Transaction create(Connection conn, Transaction t) throws SQLException {
        String sql = "INSERT INTO transactions (reference_number, source_account_id, destination_account_id, amount, transaction_type, channel, status, merchant_name, merchant_category, location, ip_address, device_id, idempotency_key, risk_score, risk_level, fraud_status, transaction_timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, t.getReferenceNumber());
            pstmt.setLong(2, t.getSourceAccountId());
            if (t.getDestinationAccountId() != null) pstmt.setLong(3, t.getDestinationAccountId()); else pstmt.setNull(3, Types.BIGINT);
            pstmt.setBigDecimal(4, t.getAmount());
            pstmt.setString(5, t.getTransactionType());
            pstmt.setString(6, t.getChannel());
            pstmt.setString(7, t.getStatus());
            pstmt.setString(8, t.getMerchantName());
            pstmt.setString(9, t.getMerchantCategory());
            pstmt.setString(10, t.getLocation());
            pstmt.setString(11, t.getIpAddress());
            pstmt.setString(12, t.getDeviceId());
            pstmt.setString(13, t.getIdempotencyKey());
            pstmt.setInt(14, t.getRiskScore());
            pstmt.setString(15, t.getRiskLevel());
            pstmt.setString(16, t.getFraudStatus() != null ? t.getFraudStatus() : "NOT_SUSPICIOUS");
            pstmt.setTimestamp(17, t.getTransactionTimestamp() != null ? Timestamp.valueOf(t.getTransactionTimestamp()) : new Timestamp(System.currentTimeMillis()));
            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) t.setId(rs.getLong(1));
            }
        }
        return t;
    }

    public Optional<Transaction> findById(Long id) {
        String sql = "SELECT t.*, sa.account_number as src_acc, da.account_number as dst_acc, CONCAT(c.first_name, ' ', c.last_name) as cust_name, c.id as cust_id " +
                "FROM transactions t JOIN accounts sa ON t.source_account_id = sa.id LEFT JOIN accounts da ON t.destination_account_id = da.id JOIN customers c ON sa.customer_id = c.id WHERE t.id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapResultSetToTransaction(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding transaction by ID: " + id, e);
        }
        return Optional.empty();
    }

    public Optional<Transaction> findByIdempotencyKey(String key) {
        if (key == null || key.trim().isEmpty()) return Optional.empty();
        String sql = "SELECT t.*, sa.account_number as src_acc, da.account_number as dst_acc, CONCAT(c.first_name, ' ', c.last_name) as cust_name, c.id as cust_id " +
                "FROM transactions t JOIN accounts sa ON t.source_account_id = sa.id LEFT JOIN accounts da ON t.destination_account_id = da.id JOIN customers c ON sa.customer_id = c.id WHERE t.idempotency_key = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, key.trim());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapResultSetToTransaction(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding transaction by idempotency key", e);
        }
        return Optional.empty();
    }

    public List<Transaction> search(String query, String type, String channel, String status, String riskLevel, String startDate, String endDate, int offset, int limit) {
        List<Transaction> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT t.*, sa.account_number as src_acc, da.account_number as dst_acc, CONCAT(c.first_name, ' ', c.last_name) as cust_name, c.id as cust_id " +
                "FROM transactions t JOIN accounts sa ON t.source_account_id = sa.id LEFT JOIN accounts da ON t.destination_account_id = da.id JOIN customers c ON sa.customer_id = c.id WHERE 1=1"
        );
        List<Object> params = new ArrayList<>();

        if (query != null && !query.trim().isEmpty()) {
            sql.append(" AND (t.reference_number LIKE ? OR sa.account_number LIKE ? OR c.first_name LIKE ? OR c.last_name LIKE ? OR t.merchant_name LIKE ?)");
            String q = "%" + query.trim() + "%";
            params.add(q); params.add(q); params.add(q); params.add(q); params.add(q);
        }
        if (type != null && !type.trim().isEmpty() && !"ALL".equalsIgnoreCase(type)) {
            sql.append(" AND t.transaction_type = ?");
            params.add(type.trim());
        }
        if (channel != null && !channel.trim().isEmpty() && !"ALL".equalsIgnoreCase(channel)) {
            sql.append(" AND t.channel = ?");
            params.add(channel.trim());
        }
        if (status != null && !status.trim().isEmpty() && !"ALL".equalsIgnoreCase(status)) {
            sql.append(" AND t.status = ?");
            params.add(status.trim());
        }
        if (riskLevel != null && !riskLevel.trim().isEmpty() && !"ALL".equalsIgnoreCase(riskLevel)) {
            sql.append(" AND t.risk_level = ?");
            params.add(riskLevel.trim());
        }
        if (startDate != null && !startDate.trim().isEmpty()) {
            sql.append(" AND t.transaction_timestamp >= ?");
            params.add(Timestamp.valueOf(startDate + " 00:00:00"));
        }
        if (endDate != null && !endDate.trim().isEmpty()) {
            sql.append(" AND t.transaction_timestamp <= ?");
            params.add(Timestamp.valueOf(endDate + " 23:59:59"));
        }

        sql.append(" ORDER BY t.transaction_timestamp DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) list.add(mapResultSetToTransaction(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error searching transactions", e);
        }
        return list;
    }

    public int countSearch(String query, String type, String channel, String status, String riskLevel, String startDate, String endDate) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM transactions t JOIN accounts sa ON t.source_account_id = sa.id JOIN customers c ON sa.customer_id = c.id WHERE 1=1"
        );
        List<Object> params = new ArrayList<>();

        if (query != null && !query.trim().isEmpty()) {
            sql.append(" AND (t.reference_number LIKE ? OR sa.account_number LIKE ? OR c.first_name LIKE ? OR c.last_name LIKE ? OR t.merchant_name LIKE ?)");
            String q = "%" + query.trim() + "%";
            params.add(q); params.add(q); params.add(q); params.add(q); params.add(q);
        }
        if (type != null && !type.trim().isEmpty() && !"ALL".equalsIgnoreCase(type)) {
            sql.append(" AND t.transaction_type = ?");
            params.add(type.trim());
        }
        if (channel != null && !channel.trim().isEmpty() && !"ALL".equalsIgnoreCase(channel)) {
            sql.append(" AND t.channel = ?");
            params.add(channel.trim());
        }
        if (status != null && !status.trim().isEmpty() && !"ALL".equalsIgnoreCase(status)) {
            sql.append(" AND t.status = ?");
            params.add(status.trim());
        }
        if (riskLevel != null && !riskLevel.trim().isEmpty() && !"ALL".equalsIgnoreCase(riskLevel)) {
            sql.append(" AND t.risk_level = ?");
            params.add(riskLevel.trim());
        }
        if (startDate != null && !startDate.trim().isEmpty()) {
            sql.append(" AND t.transaction_timestamp >= ?");
            params.add(Timestamp.valueOf(startDate + " 00:00:00"));
        }
        if (endDate != null && !endDate.trim().isEmpty()) {
            sql.append(" AND t.transaction_timestamp <= ?");
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
            throw new RuntimeException("Error counting transactions", e);
        }
        return 0;
    }

    public List<Transaction> findByAccountId(Long accountId, int limit) {
        List<Transaction> list = new ArrayList<>();
        String sql = "SELECT t.*, sa.account_number as src_acc, da.account_number as dst_acc, CONCAT(c.first_name, ' ', c.last_name) as cust_name, c.id as cust_id " +
                "FROM transactions t JOIN accounts sa ON t.source_account_id = sa.id LEFT JOIN accounts da ON t.destination_account_id = da.id JOIN customers c ON sa.customer_id = c.id " +
                "WHERE t.source_account_id = ? OR t.destination_account_id = ? ORDER BY t.transaction_timestamp DESC LIMIT ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, accountId);
            pstmt.setLong(2, accountId);
            pstmt.setInt(3, limit);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) list.add(mapResultSetToTransaction(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching transactions for account: " + accountId, e);
        }
        return list;
    }

    public BigDecimal getDailySpendingTotal(Connection conn, Long accountId, LocalDate date) throws SQLException {
        String sql = "SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE source_account_id = ? AND transaction_type IN ('WITHDRAWAL', 'TRANSFER', 'CARD_PAYMENT', 'ONLINE_PAYMENT') AND status IN ('COMPLETED', 'PENDING', 'UNDER_REVIEW') AND DATE(transaction_timestamp) = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, accountId);
            pstmt.setDate(2, Date.valueOf(date));
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getBigDecimal(1);
            }
        }
        return BigDecimal.ZERO;
    }

    public BigDecimal getAverageTransactionAmount(Long accountId, int days) {
        String sql = "SELECT COALESCE(AVG(amount), 0) FROM transactions WHERE source_account_id = ? AND status = 'COMPLETED' AND transaction_timestamp >= DATE_SUB(NOW(), INTERVAL ? DAY)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, accountId);
            pstmt.setInt(2, days);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getBigDecimal(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error calculating average transaction amount", e);
        }
        return BigDecimal.ZERO;
    }

    public boolean updateStatus(Connection conn, Long transactionId, String status, String fraudStatus) throws SQLException {
        String sql = "UPDATE transactions SET status = ?, fraud_status = ? WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status);
            pstmt.setString(2, fraudStatus);
            pstmt.setLong(3, transactionId);
            return pstmt.executeUpdate() > 0;
        }
    }

    private Transaction mapResultSetToTransaction(ResultSet rs) throws SQLException {
        Transaction t = new Transaction();
        t.setId(rs.getLong("id"));
        t.setReferenceNumber(rs.getString("reference_number"));
        t.setSourceAccountId(rs.getLong("source_account_id"));

        long dst = rs.getLong("destination_account_id");
        if (!rs.wasNull()) t.setDestinationAccountId(dst);

        t.setAmount(rs.getBigDecimal("amount"));
        t.setTransactionType(rs.getString("transaction_type"));
        t.setChannel(rs.getString("channel"));
        t.setStatus(rs.getString("status"));
        t.setMerchantName(rs.getString("merchant_name"));
        t.setMerchantCategory(rs.getString("merchant_category"));
        t.setLocation(rs.getString("location"));
        t.setIpAddress(rs.getString("ip_address"));
        t.setDeviceId(rs.getString("device_id"));
        t.setIdempotencyKey(rs.getString("idempotency_key"));
        t.setRiskScore(rs.getInt("risk_score"));
        t.setRiskLevel(rs.getString("risk_level"));
        t.setFraudStatus(rs.getString("fraud_status"));

        Timestamp ts = rs.getTimestamp("transaction_timestamp");
        if (ts != null) t.setTransactionTimestamp(ts.toLocalDateTime());
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) t.setCreatedAt(created.toLocalDateTime());

        try {
            t.setSourceAccountNumber(rs.getString("src_acc"));
            t.setDestinationAccountNumber(rs.getString("dst_acc"));
            t.setCustomerName(rs.getString("cust_name"));
            t.setCustomerId(rs.getLong("cust_id"));
        } catch (SQLException ignored) {}

        return t;
    }
}
