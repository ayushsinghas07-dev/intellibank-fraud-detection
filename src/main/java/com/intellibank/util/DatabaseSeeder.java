package com.intellibank.util;

import com.intellibank.config.AppConfig;
import com.intellibank.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class DatabaseSeeder {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseSeeder.class);

    private static final Random random = new Random(42); // Fixed seed for deterministic output

    private static final String[] FIRST_NAMES = {
        "James", "Mary", "John", "Patricia", "Robert", "Jennifer", "Michael", "Linda",
        "William", "Elizabeth", "David", "Barbara", "Richard", "Susan", "Joseph", "Jessica",
        "Thomas", "Sarah", "Charles", "Karen", "Christopher", "Nancy", "Daniel", "Lisa",
        "Matthew", "Betty", "Anthony", "Margaret", "Donald", "Sandra", "Mark", "Ashley",
        "Paul", "Kimberly", "Steven", "Emily", "Andrew", "Donna", "Kenneth", "Michelle",
        "Joshua", "Carol", "Kevin", "Amanda", "Brian", "Dorothy", "George", "Melissa"
    };

    private static final String[] LAST_NAMES = {
        "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis",
        "Rodriguez", "Martinez", "Hernandez", "Lopez", "Gonzalez", "Wilson", "Anderson", "Thomas",
        "Taylor", "Moore", "Jackson", "Martin", "Lee", "Perez", "Thompson", "White",
        "Harris", "Sanchez", "Clark", "Ramirez", "Lewis", "Robinson", "Walker", "Young",
        "Allen", "King", "Wright", "Scott", "Torres", "Nguyen", "Hill", "Flores"
    };

    private static final String[] CITIES = {
        "New York, US", "London, UK", "Tokyo, JP", "Paris, FR", "Sydney, AU",
        "Toronto, CA", "Berlin, DE", "Singapore, SG", "Dubai, AE", "Hong Kong, HK"
    };

    private static final String[] MERCHANTS = {
        "Amazon Online", "Walmart Supercenter", "Apple Store", "Starbucks Coffee",
        "Target Store", "Uber Trips", "Netflix Subscription", "Chevron Gas",
        "Crypto Xchange", "Bet365 Gambling", "Offshore Wire Corp", "Global Tech Store",
        "Shell Station", "Best Buy Electronics", "Delta Air Lines", "Hilton Hotels"
    };

    public static void main(String[] args) {
        logger.info("Initializing IntelliBank Database Seeder...");
        seedDatabase();
    }

    public static void seedDatabase() {
        String baseUrl = AppConfig.getDbUrl();
        String rootUrl = baseUrl.contains("/intellibank_db") ? baseUrl.replace("/intellibank_db", "/") : baseUrl;
        
        // Step 1: Ensure database exists
        try (Connection rootConn = DriverManager.getConnection(rootUrl, AppConfig.getDbUser(), AppConfig.getDbPassword());
             Statement stmt = rootConn.createStatement()) {
            stmt.execute("CREATE DATABASE IF NOT EXISTS intellibank_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;");
            logger.info("Database intellibank_db verified/created successfully.");
        } catch (Exception e) {
            logger.error("Failed to ensure intellibank_db existence", e);
        }

        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);

            logger.info("Running schema DDL script...");
            runSchemaScript(conn);

            logger.info("Seeding Users...");
            seedUsers(conn);

            logger.info("Seeding Fraud Rules...");
            seedFraudRules(conn);

            logger.info("Seeding Customers & Accounts...");
            List<Long> customerIds = seedCustomers(conn, 125);
            List<Long> accountIds = seedAccounts(conn, customerIds);

            logger.info("Seeding Transactions & Fraud Scenarios...");
            seedTransactionsAndAlerts(conn, customerIds, accountIds);

            conn.commit();
            logger.info("Database seeding completed successfully!");
            printRowCounts(conn);

        } catch (Exception e) {
            logger.error("Database seeding failed", e);
            throw new RuntimeException("Seeding failed: " + e.getMessage(), e);
        }
    }

    private static void runSchemaScript(Connection conn) throws Exception {
        InputStream is = DatabaseSeeder.class.getClassLoader().getResourceAsStream("database/schema.sql");
        if (is == null) {
            is = DatabaseSeeder.class.getClassLoader().getResourceAsStream("schema.sql");
        }
        if (is == null) {
            // Read from file system relative path
            java.io.File file = new java.io.File("database/schema.sql");
            if (file.exists()) {
                is = new java.io.FileInputStream(file);
            }
        }
        if (is == null) {
            throw new RuntimeException("schema.sql not found in resources or file system");
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.trim().startsWith("--") && !line.trim().isEmpty()) {
                    sb.append(line).append("\n");
                }
            }
        }

        String[] statements = sb.toString().split(";");
        try (Statement stmt = conn.createStatement()) {
            for (String sql : statements) {
                if (!sql.trim().isEmpty()) {
                    stmt.execute(sql.trim());
                }
            }
        }
    }

    private static void seedUsers(Connection conn) throws SQLException {
        String sql = "INSERT INTO users (username, email, password_hash, role, full_name, status) VALUES (?, ?, ?, ?, ?, 'ACTIVE')";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            // 1. Admin User
            pstmt.setString(1, "admin");
            pstmt.setString(2, "admin@intellibank.com");
            pstmt.setString(3, PasswordUtil.hashPassword("Admin@12345"));
            pstmt.setString(4, "ADMIN");
            pstmt.setString(5, "System Administrator");
            pstmt.addBatch();

            // 2. Fraud Analyst User
            pstmt.setString(1, "analyst");
            pstmt.setString(2, "analyst@intellibank.com");
            pstmt.setString(3, PasswordUtil.hashPassword("Analyst@12345"));
            pstmt.setString(4, "FRAUD_ANALYST");
            pstmt.setString(5, "Sarah Jenkins - Lead Fraud Analyst");
            pstmt.addBatch();

            // 3. Auditor / Viewer User
            pstmt.setString(1, "viewer");
            pstmt.setString(2, "viewer@intellibank.com");
            pstmt.setString(3, PasswordUtil.hashPassword("Viewer@12345"));
            pstmt.setString(4, "VIEWER");
            pstmt.setString(5, "David Miller - Compliance Auditor");
            pstmt.addBatch();

            pstmt.executeBatch();
        }
    }

    private static void seedFraudRules(Connection conn) throws SQLException {
        String sql = "INSERT INTO fraud_rules (rule_code, rule_name, description, threshold_params, points, enabled) VALUES (?, ?, ?, ?, ?, true)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            addRuleBatch(pstmt, "HIGH_VALUE", "High Value Transaction", "Transaction amount exceeds configured high-value threshold", "{\"threshold\": 10000.00}", 20);
            addRuleBatch(pstmt, "VELOCITY", "Transaction Velocity Spike", "High count of transactions within a short sliding window", "{\"maxCount\": 5, \"windowMinutes\": 5}", 15);
            addRuleBatch(pstmt, "UNUSUAL_AMOUNT", "Unusual Amount vs Customer Average", "Amount significantly exceeds customer's historical 30-day average", "{\"multiplier\": 3.0}", 20);
            addRuleBatch(pstmt, "LOCATION_ANOMALY", "Geographic Location Anomaly", "Impossible travel velocity between transaction geographic locations", "{\"maxSpeedKmh\": 800}", 15);
            addRuleBatch(pstmt, "CHANNEL_ANOMALY", "Channel Switching Anomaly", "Unusual channel usage or rapid switching across transaction channels", "{\"maxChannelsInWindow\": 3}", 10);
            addRuleBatch(pstmt, "REPEATED_FAILED", "Repeated Failed Transactions", "Multiple failed or declined transaction attempts in a short window", "{\"maxFailed\": 3, \"windowMinutes\": 10}", 10);
            addRuleBatch(pstmt, "RAPID_LARGE_TXN", "Rapid Consecutive Large Transactions", "Multiple large transactions executed in rapid succession", "{\"minAmount\": 5000.00, \"maxCount\": 2, \"windowMinutes\": 10}", 15);
            addRuleBatch(pstmt, "HIGH_RISK_MERCHANT", "High Risk Merchant Category", "Transaction with high-risk merchant category (Crypto, Gambling, Wire)", "{\"categories\": [\"Crypto\", \"Gambling\", \"Offshore\", \"Wire Transfer\"]}", 10);
            addRuleBatch(pstmt, "DAILY_LIMIT_BREACH", "Daily Account Limit Breach", "Cumulative daily spending exceeds configured account daily limit", "{\"percentageThreshold\": 100}", 15);
            addRuleBatch(pstmt, "DUPLICATE_TXN", "Duplicate Transaction Submission", "Identical account, amount, and merchant submitted within short window", "{\"windowMinutes\": 2}", 20);

            pstmt.executeBatch();
        }
    }

    private static void addRuleBatch(PreparedStatement pstmt, String code, String name, String desc, String params, int points) throws SQLException {
        pstmt.setString(1, code);
        pstmt.setString(2, name);
        pstmt.setString(3, desc);
        pstmt.setString(4, params);
        pstmt.setInt(5, points);
        pstmt.addBatch();
    }

    private static List<Long> seedCustomers(Connection conn, int count) throws SQLException {
        List<Long> ids = new ArrayList<>();
        String sql = "INSERT INTO customers (customer_number, first_name, last_name, email, phone, address, kyc_status, risk_category, status, home_location) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?)";

        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 1; i <= count; i++) {
                String fName = FIRST_NAMES[random.nextInt(FIRST_NAMES.length)];
                String lName = LAST_NAMES[random.nextInt(LAST_NAMES.length)];
                String custNo = String.format("CUST-%05d", i);
                String email = (fName + "." + lName + i + "@example.com").toLowerCase();
                String phone = String.format("+1-555-%03d-%04d", random.nextInt(1000), random.nextInt(10000));
                String address = (100 + random.nextInt(900)) + " Main St, Suite " + (10 + random.nextInt(90));
                String kyc = (i <= 118) ? "VERIFIED" : (i <= 122 ? "PENDING" : "REJECTED");
                String riskCat = (i % 15 == 0) ? "HIGH" : ((i % 5 == 0) ? "MEDIUM" : "LOW");
                String location = CITIES[random.nextInt(CITIES.length)];

                pstmt.setString(1, custNo);
                pstmt.setString(2, fName);
                pstmt.setString(3, lName);
                pstmt.setString(4, email);
                pstmt.setString(5, phone);
                pstmt.setString(6, address);
                pstmt.setString(7, kyc);
                pstmt.setString(8, riskCat);
                pstmt.setString(9, location);
                pstmt.executeUpdate();

                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        ids.add(rs.getLong(1));
                    }
                }
            }
        }
        return ids;
    }

    private static List<Long> seedAccounts(Connection conn, List<Long> customerIds) throws SQLException {
        List<Long> accountIds = new ArrayList<>();
        String sql = "INSERT INTO accounts (account_number, customer_id, account_type, balance, daily_limit, status) VALUES (?, ?, ?, ?, ?, ?)";

        int accCounter = 100000;
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (Long custId : customerIds) {
                // Each customer gets 1 SAVINGS account and optional CURRENT account
                accCounter++;
                String accNum1 = "ACC-" + accCounter;
                BigDecimal bal1 = BigDecimal.valueOf(1000 + random.nextInt(90000)).setScale(2, RoundingMode.HALF_UP);
                BigDecimal limit1 = BigDecimal.valueOf(5000 + random.nextInt(45000)).setScale(2, RoundingMode.HALF_UP);

                pstmt.setString(1, accNum1);
                pstmt.setLong(2, custId);
                pstmt.setString(3, "SAVINGS");
                pstmt.setBigDecimal(4, bal1);
                pstmt.setBigDecimal(5, limit1);
                pstmt.setString(6, "ACTIVE");
                pstmt.executeUpdate();

                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) accountIds.add(rs.getLong(1));
                }

                if (random.nextBoolean()) {
                    accCounter++;
                    String accNum2 = "ACC-" + accCounter;
                    BigDecimal bal2 = BigDecimal.valueOf(500 + random.nextInt(40000)).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal limit2 = BigDecimal.valueOf(10000 + random.nextInt(40000)).setScale(2, RoundingMode.HALF_UP);

                    pstmt.setString(1, accNum2);
                    pstmt.setLong(2, custId);
                    pstmt.setString(3, "CURRENT");
                    pstmt.setBigDecimal(4, bal2);
                    pstmt.setBigDecimal(5, limit2);
                    pstmt.setString(6, (custId % 25 == 0) ? "FROZEN" : "ACTIVE");
                    pstmt.executeUpdate();

                    try (ResultSet rs = pstmt.getGeneratedKeys()) {
                        if (rs.next()) accountIds.add(rs.getLong(1));
                    }
                }
            }
        }
        return accountIds;
    }

    private static void seedTransactionsAndAlerts(Connection conn, List<Long> customerIds, List<Long> accountIds) throws SQLException {
        String txnSql = "INSERT INTO transactions (reference_number, source_account_id, destination_account_id, amount, transaction_type, channel, status, merchant_name, merchant_category, location, ip_address, device_id, idempotency_key, risk_score, risk_level, fraud_status, transaction_timestamp, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String alertSql = "INSERT INTO fraud_alerts (alert_number, transaction_id, customer_id, risk_score, risk_level, status, triggered_rules_json, evidence_json, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String riskSql = "INSERT INTO risk_scores (transaction_id, score, level, triggered_rules_json, evidence_json, recommended_action, evaluated_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        String notifSql = "INSERT INTO notifications (role, severity, title, message, read_state, entity_type, entity_id, created_at) VALUES ('FRAUD_ANALYST', ?, ?, ?, false, 'FRAUD_ALERT', ?, ?)";
        String auditSql = "INSERT INTO audit_log (username, role, action, entity_type, entity_id, details_json, ip_address, timestamp) VALUES ('system', 'SYSTEM', ?, ?, ?, ?, '127.0.0.1', ?)";

        PreparedStatement pTxn = conn.prepareStatement(txnSql, Statement.RETURN_GENERATED_KEYS);
        PreparedStatement pAlert = conn.prepareStatement(alertSql);
        PreparedStatement pRisk = conn.prepareStatement(riskSql);
        PreparedStatement pNotif = conn.prepareStatement(notifSql);
        PreparedStatement pAudit = conn.prepareStatement(auditSql);

        Instant now = Instant.now();
        int totalTxns = 1520;
        int txnCounter = 100000;
        int alertCounter = 1000;

        String[] types = {"DEPOSIT", "WITHDRAWAL", "TRANSFER", "CARD_PAYMENT", "ONLINE_PAYMENT"};
        String[] channels = {"ATM", "POS", "MOBILE", "WEB", "BRANCH"};

        for (int i = 0; i < totalTxns; i++) {
            txnCounter++;
            String refNum = "TXN-" + txnCounter;
            Long srcAcc = accountIds.get(random.nextInt(accountIds.size()));
            Long dstAcc = (random.nextBoolean() && accountIds.size() > 1) ? accountIds.get(random.nextInt(accountIds.size())) : null;
            if (Objects.equals(srcAcc, dstAcc)) dstAcc = null;

            // Generate timestamp spanning past 90 days
            long daysBack = random.nextInt(90);
            long secondsOffset = random.nextInt(86400);
            Instant txnTime = now.minus(daysBack, ChronoUnit.DAYS).minus(secondsOffset, ChronoUnit.SECONDS);
            Timestamp ts = Timestamp.from(txnTime);

            String type = types[random.nextInt(types.length)];
            String channel = channels[random.nextInt(channels.length)];
            String merchant = MERCHANTS[random.nextInt(MERCHANTS.length)];
            String merchantCat = merchant.contains("Crypto") ? "Crypto" : (merchant.contains("Gambling") ? "Gambling" : "Retail");
            String location = CITIES[random.nextInt(CITIES.length)];
            String ip = "192.168.1." + random.nextInt(255);
            String device = "DEV-" + (1000 + random.nextInt(9000));
            String idempotencyKey = "IDEMP-" + UUID.randomUUID().toString().substring(0, 8);

            BigDecimal amount = BigDecimal.valueOf(10 + random.nextInt(1500)).setScale(2, RoundingMode.HALF_UP);
            int riskScore = 0;
            String riskLevel = "LOW";
            String txnStatus = "COMPLETED";
            String fraudStatus = "NOT_SUSPICIOUS";

            List<String> triggeredRules = new ArrayList<>();
            List<String> evidence = new ArrayList<>();

            // Plant deliberate fraud scenarios to trigger EVERY rule
            if (i % 85 == 0) {
                // High Value + High Risk Merchant
                amount = BigDecimal.valueOf(15000 + random.nextInt(20000)).setScale(2, RoundingMode.HALF_UP);
                merchant = "Crypto Xchange";
                merchantCat = "Crypto";
                riskScore = 50;
                riskLevel = "MEDIUM";
                txnStatus = "COMPLETED";
                fraudStatus = "SUSPICIOUS";
                triggeredRules.add("HIGH_VALUE (+20)");
                triggeredRules.add("UNUSUAL_AMOUNT (+20)");
                triggeredRules.add("HIGH_RISK_MERCHANT (+10)");
                evidence.add("Amount $ " + amount + " exceeds $10,000 threshold");
                evidence.add("Merchant Crypto Xchange in high-risk category Crypto");
            } else if (i % 95 == 0) {
                // Velocity burst / Rapid large transactions -> HIGH / UNDER_REVIEW
                amount = BigDecimal.valueOf(8500).setScale(2, RoundingMode.HALF_UP);
                riskScore = 65;
                riskLevel = "HIGH";
                txnStatus = "UNDER_REVIEW";
                fraudStatus = "SUSPICIOUS";
                triggeredRules.add("VELOCITY (+15)");
                triggeredRules.add("RAPID_LARGE_TXN (+15)");
                triggeredRules.add("HIGH_VALUE (+20)");
                triggeredRules.add("CHANNEL_ANOMALY (+10)");
                evidence.add("6 transactions in 4 minutes window");
                evidence.add("2 consecutive transactions exceeding $5,000");
            } else if (i % 110 == 0) {
                // Location anomaly + repeated failed attempts -> CRITICAL / BLOCKED
                location = "Tokyo, JP";
                riskScore = 85;
                riskLevel = "CRITICAL";
                txnStatus = "BLOCKED";
                fraudStatus = "CONFIRMED_FRAUD";
                triggeredRules.add("LOCATION_ANOMALY (+15)");
                triggeredRules.add("REPEATED_FAILED (+10)");
                triggeredRules.add("HIGH_VALUE (+20)");
                triggeredRules.add("DAILY_LIMIT_BREACH (+15)");
                triggeredRules.add("UNUSUAL_AMOUNT (+25)");
                evidence.add("Transaction in Tokyo, JP within 15m of New York, US");
                evidence.add("3 failed authentication attempts detected");
            } else if (i % 140 == 0) {
                // Duplicate transaction
                riskScore = 20;
                riskLevel = "LOW";
                txnStatus = "COMPLETED";
                triggeredRules.add("DUPLICATE_TXN (+20)");
                evidence.add("Identical $ " + amount + " transaction submitted within 45s");
            }

            pTxn.setString(1, refNum);
            pTxn.setLong(2, srcAcc);
            if (dstAcc != null) pTxn.setLong(3, dstAcc); else pTxn.setNull(3, Types.BIGINT);
            pTxn.setBigDecimal(4, amount);
            pTxn.setString(5, type);
            pTxn.setString(6, channel);
            pTxn.setString(7, txnStatus);
            pTxn.setString(8, merchant);
            pTxn.setString(9, merchantCat);
            pTxn.setString(10, location);
            pTxn.setString(11, ip);
            pTxn.setString(12, device);
            pTxn.setString(13, idempotencyKey);
            pTxn.setInt(14, riskScore);
            pTxn.setString(15, riskLevel);
            pTxn.setString(16, fraudStatus);
            pTxn.setTimestamp(17, ts);
            pTxn.setTimestamp(18, ts);
            pTxn.executeUpdate();

            long txnId;
            try (ResultSet rs = pTxn.getGeneratedKeys()) {
                if (rs.next()) txnId = rs.getLong(1); else continue;
            }

            // Create Risk Score entry
            String rulesJson = toJsonArray(triggeredRules);
            String evidenceJson = toJsonArray(evidence);
            pRisk.setLong(1, txnId);
            pRisk.setInt(2, riskScore);
            pRisk.setString(3, riskLevel);
            pRisk.setString(4, rulesJson);
            pRisk.setString(5, evidenceJson);
            pRisk.setString(6, riskScore >= 60 ? "FLAG_FOR_MANUAL_REVIEW" : "ALLOW");
            pRisk.setTimestamp(7, ts);
            pRisk.executeUpdate();

            // Create Fraud Alert for HIGH or CRITICAL or flagged MEDIUM
            if (riskScore >= 30) {
                alertCounter++;
                String alertNum = "ALT-" + alertCounter;
                String alertStatus = "OPEN";
                if ("COMPLETED".equals(txnStatus) && riskScore < 60) {
                    alertStatus = (i % 2 == 0) ? "RESOLVED" : "FALSE_POSITIVE";
                } else if ("BLOCKED".equals(txnStatus)) {
                    alertStatus = "CONFIRMED_FRAUD";
                } else if ("UNDER_REVIEW".equals(txnStatus)) {
                    alertStatus = "UNDER_REVIEW";
                }

                Long custId = customerIds.get(random.nextInt(customerIds.size()));
                pAlert.setString(1, alertNum);
                pAlert.setLong(2, txnId);
                pAlert.setLong(3, custId);
                pAlert.setInt(4, riskScore);
                pAlert.setString(5, riskLevel);
                pAlert.setString(6, alertStatus);
                pAlert.setString(7, rulesJson);
                pAlert.setString(8, evidenceJson);
                pAlert.setTimestamp(9, ts);
                pAlert.executeUpdate();

                // Create Notification for alert
                pNotif.setString(1, riskScore >= 80 ? "CRITICAL" : "HIGH");
                pNotif.setString(2, "Fraud Alert: " + alertNum);
                pNotif.setString(3, "Transaction " + refNum + " flagged with risk score " + riskScore + " (" + riskLevel + ")");
                pNotif.setLong(4, txnId);
                pNotif.setTimestamp(5, ts);
                pNotif.executeUpdate();

                // Create Audit Log
                pAudit.setString(1, "FRAUD_EVALUATION");
                pAudit.setString(2, "TRANSACTION");
                pAudit.setLong(3, txnId);
                pAudit.setString(4, "{\"riskScore\": " + riskScore + ", \"level\": \"" + riskLevel + "\"}");
                pAudit.setTimestamp(5, ts);
                pAudit.executeUpdate();
            }
        }

        pTxn.close();
        pAlert.close();
        pRisk.close();
        pNotif.close();
        pAudit.close();
    }

    private static String toJsonArray(List<String> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            sb.append("\"").append(items.get(i).replace("\"", "\\\"")).append("\"");
            if (i < items.size() - 1) sb.append(", ");
        }
        sb.append("]");
        return sb.toString();
    }

    private static void printRowCounts(Connection conn) throws SQLException {
        String[] tables = {"users", "customers", "accounts", "transactions", "fraud_rules", "fraud_alerts", "risk_scores", "notifications", "audit_log"};
        System.out.println("=================================================");
        System.out.println("IntelliBank Database Seeder Row Counts Report:");
        System.out.println("=================================================");
        try (Statement stmt = conn.createStatement()) {
            for (String table : tables) {
                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    if (rs.next()) {
                        System.out.printf("Table %-20s : %d rows%n", table, rs.getInt(1));
                    }
                }
            }
        }
        System.out.println("=================================================");
    }
}
