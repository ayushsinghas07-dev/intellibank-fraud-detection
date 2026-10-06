package com.intellibank.dao;

import com.intellibank.config.DatabaseConfig;
import com.intellibank.model.Account;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AccountDao {

    public Optional<Account> findById(Long id) {
        String sql = "SELECT a.*, CONCAT(c.first_name, ' ', c.last_name) as customer_name, c.email as customer_email " +
                "FROM accounts a JOIN customers c ON a.customer_id = c.id WHERE a.id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapResultSetToAccount(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding account by ID: " + id, e);
        }
        return Optional.empty();
    }

    public Optional<Account> findByAccountNumber(String accountNumber) {
        String sql = "SELECT a.*, CONCAT(c.first_name, ' ', c.last_name) as customer_name, c.email as customer_email " +
                "FROM accounts a JOIN customers c ON a.customer_id = c.id WHERE a.account_number = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, accountNumber);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapResultSetToAccount(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding account by account_number: " + accountNumber, e);
        }
        return Optional.empty();
    }

    public List<Account> findByCustomerId(Long customerId) {
        List<Account> list = new ArrayList<>();
        String sql = "SELECT a.*, CONCAT(c.first_name, ' ', c.last_name) as customer_name, c.email as customer_email " +
                "FROM accounts a JOIN customers c ON a.customer_id = c.id WHERE a.customer_id = ? ORDER BY a.id ASC";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, customerId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) list.add(mapResultSetToAccount(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding accounts for customer: " + customerId, e);
        }
        return list;
    }

    public List<Account> search(String query, String status, String accountType, int offset, int limit) {
        List<Account> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT a.*, CONCAT(c.first_name, ' ', c.last_name) as customer_name, c.email as customer_email " +
                "FROM accounts a JOIN customers c ON a.customer_id = c.id WHERE 1=1"
        );
        List<Object> params = new ArrayList<>();

        if (query != null && !query.trim().isEmpty()) {
            sql.append(" AND (a.account_number LIKE ? OR c.first_name LIKE ? OR c.last_name LIKE ?)");
            String q = "%" + query.trim() + "%";
            params.add(q); params.add(q); params.add(q);
        }
        if (status != null && !status.trim().isEmpty() && !"ALL".equalsIgnoreCase(status)) {
            sql.append(" AND a.status = ?");
            params.add(status.trim());
        }
        if (accountType != null && !accountType.trim().isEmpty() && !"ALL".equalsIgnoreCase(accountType)) {
            sql.append(" AND a.account_type = ?");
            params.add(accountType.trim());
        }

        sql.append(" ORDER BY a.id DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) list.add(mapResultSetToAccount(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error searching accounts", e);
        }
        return list;
    }

    public int countSearch(String query, String status, String accountType) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM accounts a JOIN customers c ON a.customer_id = c.id WHERE 1=1"
        );
        List<Object> params = new ArrayList<>();

        if (query != null && !query.trim().isEmpty()) {
            sql.append(" AND (a.account_number LIKE ? OR c.first_name LIKE ? OR c.last_name LIKE ?)");
            String q = "%" + query.trim() + "%";
            params.add(q); params.add(q); params.add(q);
        }
        if (status != null && !status.trim().isEmpty() && !"ALL".equalsIgnoreCase(status)) {
            sql.append(" AND a.status = ?");
            params.add(status.trim());
        }
        if (accountType != null && !accountType.trim().isEmpty() && !"ALL".equalsIgnoreCase(accountType)) {
            sql.append(" AND a.account_type = ?");
            params.add(accountType.trim());
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
            throw new RuntimeException("Error counting accounts", e);
        }
        return 0;
    }

    /**
     * Locks account rows FOR UPDATE in ascending Account ID order to guarantee deadlock freedom.
     */
    public List<Account> lockAccountsForUpdate(Connection conn, Long accountId1, Long accountId2) throws SQLException {
        List<Long> idsToLock = new ArrayList<>();
        if (accountId1 != null) idsToLock.add(accountId1);
        if (accountId2 != null && !accountId2.equals(accountId1)) idsToLock.add(accountId2);

        // ALWAYS sort account IDs ascending to prevent deadlocks
        idsToLock.sort(Long::compareTo);

        List<Account> lockedAccounts = new ArrayList<>();
        String sql = "SELECT a.*, CONCAT(c.first_name, ' ', c.last_name) as customer_name, c.email as customer_email " +
                "FROM accounts a JOIN customers c ON a.customer_id = c.id WHERE a.id = ? FOR UPDATE";

        for (Long id : idsToLock) {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setLong(1, id);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        lockedAccounts.add(mapResultSetToAccount(rs));
                    } else {
                        throw new SQLException("Account ID " + id + " not found during lock acquisition");
                    }
                }
            }
        }
        return lockedAccounts;
    }

    public Account create(Account account) {
        String sql = "INSERT INTO accounts (account_number, customer_id, account_type, balance, daily_limit, status) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, account.getAccountNumber());
            pstmt.setLong(2, account.getCustomerId());
            pstmt.setString(3, account.getAccountType());
            pstmt.setBigDecimal(4, account.getBalance());
            pstmt.setBigDecimal(5, account.getDailyLimit());
            pstmt.setString(6, account.getStatus() != null ? account.getStatus() : "ACTIVE");
            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) account.setId(rs.getLong(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error creating account", e);
        }
        return account;
    }

    public boolean updateBalance(Connection conn, Long accountId, BigDecimal newBalance) throws SQLException {
        String sql = "UPDATE accounts SET balance = ? WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setBigDecimal(1, newBalance);
            pstmt.setLong(2, accountId);
            return pstmt.executeUpdate() > 0;
        }
    }

    public boolean updateStatus(Long accountId, String status) {
        String sql = "UPDATE accounts SET status = ? WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status);
            pstmt.setLong(2, accountId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Error updating account status", e);
        }
    }

    private Account mapResultSetToAccount(ResultSet rs) throws SQLException {
        Account a = new Account();
        a.setId(rs.getLong("id"));
        a.setAccountNumber(rs.getString("account_number"));
        a.setCustomerId(rs.getLong("customer_id"));
        a.setAccountType(rs.getString("account_type"));
        a.setBalance(rs.getBigDecimal("balance"));
        a.setDailyLimit(rs.getBigDecimal("daily_limit"));
        a.setStatus(rs.getString("status"));

        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) a.setCreatedAt(created.toLocalDateTime());
        Timestamp updated = rs.getTimestamp("updated_at");
        if (updated != null) a.setUpdatedAt(updated.toLocalDateTime());

        try {
            a.setCustomerName(rs.getString("customer_name"));
            a.setCustomerEmail(rs.getString("customer_email"));
        } catch (SQLException ignored) {}

        return a;
    }
}
