package com.intellibank.dao;

import com.intellibank.config.DatabaseConfig;
import com.intellibank.model.Customer;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CustomerDao {

    public Optional<Customer> findById(Long id) {
        String sql = "SELECT * FROM customers WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToCustomer(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding customer by ID: " + id, e);
        }
        return Optional.empty();
    }

    public Optional<Customer> findByCustomerNumber(String customerNumber) {
        String sql = "SELECT * FROM customers WHERE customer_number = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, customerNumber);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToCustomer(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding customer by customer_number: " + customerNumber, e);
        }
        return Optional.empty();
    }

    public List<Customer> search(String query, String status, String riskCategory, int offset, int limit) {
        List<Customer> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM customers WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (query != null && !query.trim().isEmpty()) {
            sql.append(" AND (first_name LIKE ? OR last_name LIKE ? OR email LIKE ? OR customer_number LIKE ?)");
            String q = "%" + query.trim() + "%";
            params.add(q); params.add(q); params.add(q); params.add(q);
        }
        if (status != null && !status.trim().isEmpty() && !"ALL".equalsIgnoreCase(status)) {
            sql.append(" AND status = ?");
            params.add(status.trim());
        }
        if (riskCategory != null && !riskCategory.trim().isEmpty() && !"ALL".equalsIgnoreCase(riskCategory)) {
            sql.append(" AND risk_category = ?");
            params.add(riskCategory.trim());
        }

        sql.append(" ORDER BY id DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToCustomer(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error searching customers", e);
        }
        return list;
    }

    public int countSearch(String query, String status, String riskCategory) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM customers WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (query != null && !query.trim().isEmpty()) {
            sql.append(" AND (first_name LIKE ? OR last_name LIKE ? OR email LIKE ? OR customer_number LIKE ?)");
            String q = "%" + query.trim() + "%";
            params.add(q); params.add(q); params.add(q); params.add(q);
        }
        if (status != null && !status.trim().isEmpty() && !"ALL".equalsIgnoreCase(status)) {
            sql.append(" AND status = ?");
            params.add(status.trim());
        }
        if (riskCategory != null && !riskCategory.trim().isEmpty() && !"ALL".equalsIgnoreCase(riskCategory)) {
            sql.append(" AND risk_category = ?");
            params.add(riskCategory.trim());
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
            throw new RuntimeException("Error counting customers", e);
        }
        return 0;
    }

    public Customer create(Customer customer) {
        String sql = "INSERT INTO customers (customer_number, first_name, last_name, email, phone, address, kyc_status, risk_category, status, home_location) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, customer.getCustomerNumber());
            pstmt.setString(2, customer.getFirstName());
            pstmt.setString(3, customer.getLastName());
            pstmt.setString(4, customer.getEmail());
            pstmt.setString(5, customer.getPhone());
            pstmt.setString(6, customer.getAddress());
            pstmt.setString(7, customer.getKycStatus() != null ? customer.getKycStatus() : "VERIFIED");
            pstmt.setString(8, customer.getRiskCategory() != null ? customer.getRiskCategory() : "LOW");
            pstmt.setString(9, customer.getStatus() != null ? customer.getStatus() : "ACTIVE");
            pstmt.setString(10, customer.getHomeLocation() != null ? customer.getHomeLocation() : "New York, US");
            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) customer.setId(rs.getLong(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error creating customer", e);
        }
        return customer;
    }

    public boolean update(Customer customer) {
        String sql = "UPDATE customers SET first_name = ?, last_name = ?, email = ?, phone = ?, address = ?, kyc_status = ?, risk_category = ?, status = ?, home_location = ? WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, customer.getFirstName());
            pstmt.setString(2, customer.getLastName());
            pstmt.setString(3, customer.getEmail());
            pstmt.setString(4, customer.getPhone());
            pstmt.setString(5, customer.getAddress());
            pstmt.setString(6, customer.getKycStatus());
            pstmt.setString(7, customer.getRiskCategory());
            pstmt.setString(8, customer.getStatus());
            pstmt.setString(9, customer.getHomeLocation());
            pstmt.setLong(10, customer.getId());
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Error updating customer", e);
        }
    }

    private Customer mapResultSetToCustomer(ResultSet rs) throws SQLException {
        Customer c = new Customer();
        c.setId(rs.getLong("id"));
        c.setCustomerNumber(rs.getString("customer_number"));
        c.setFirstName(rs.getString("first_name"));
        c.setLastName(rs.getString("last_name"));
        c.setEmail(rs.getString("email"));
        c.setPhone(rs.getString("phone"));
        c.setAddress(rs.getString("address"));
        c.setKycStatus(rs.getString("kyc_status"));
        c.setRiskCategory(rs.getString("risk_category"));
        c.setStatus(rs.getString("status"));
        c.setHomeLocation(rs.getString("home_location"));

        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) c.setCreatedAt(created.toLocalDateTime());
        Timestamp updated = rs.getTimestamp("updated_at");
        if (updated != null) c.setUpdatedAt(updated.toLocalDateTime());

        return c;
    }
}
