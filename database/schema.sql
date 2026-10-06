-- IntelliBank Database Schema
-- Database: intellibank_db
-- Version: 1.0.0

CREATE DATABASE IF NOT EXISTS `intellibank_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `intellibank_db`;

-- Drop existing tables in reverse dependency order
DROP TABLE IF EXISTS `audit_log`;
DROP TABLE IF EXISTS `notifications`;
DROP TABLE IF EXISTS `risk_scores`;
DROP TABLE IF EXISTS `alert_notes`;
DROP TABLE IF EXISTS `fraud_alerts`;
DROP TABLE IF EXISTS `fraud_rules`;
DROP TABLE IF EXISTS `transactions`;
DROP TABLE IF EXISTS `accounts`;
DROP TABLE IF EXISTS `customers`;
DROP TABLE IF EXISTS `users`;

-- 1. Users Table
CREATE TABLE `users` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `username` VARCHAR(50) NOT NULL UNIQUE,
    `email` VARCHAR(100) NOT NULL UNIQUE,
    `password_hash` VARCHAR(255) NOT NULL,
    `role` ENUM('ADMIN', 'FRAUD_ANALYST', 'VIEWER') NOT NULL,
    `full_name` VARCHAR(100) NOT NULL,
    `status` ENUM('ACTIVE', 'INACTIVE', 'LOCKED') NOT NULL DEFAULT 'ACTIVE',
    `failed_login_attempts` INT NOT NULL DEFAULT 0,
    `lockout_until` TIMESTAMP NULL,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `last_login_at` TIMESTAMP NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Customers Table
CREATE TABLE `customers` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `customer_number` VARCHAR(20) NOT NULL UNIQUE,
    `first_name` VARCHAR(50) NOT NULL,
    `last_name` VARCHAR(50) NOT NULL,
    `email` VARCHAR(100) NOT NULL UNIQUE,
    `phone` VARCHAR(20) NOT NULL,
    `address` VARCHAR(255) NOT NULL,
    `kyc_status` ENUM('VERIFIED', 'PENDING', 'REJECTED') NOT NULL DEFAULT 'VERIFIED',
    `risk_category` ENUM('LOW', 'MEDIUM', 'HIGH') NOT NULL DEFAULT 'LOW',
    `status` ENUM('ACTIVE', 'SUSPENDED', 'DEACTIVATED') NOT NULL DEFAULT 'ACTIVE',
    `home_location` VARCHAR(100) DEFAULT 'New York, US',
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_cust_email` (`email`),
    INDEX `idx_cust_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Accounts Table
CREATE TABLE `accounts` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `account_number` VARCHAR(30) NOT NULL UNIQUE,
    `customer_id` BIGINT NOT NULL,
    `account_type` ENUM('SAVINGS', 'CURRENT') NOT NULL,
    `balance` DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    `daily_limit` DECIMAL(15,2) NOT NULL DEFAULT 10000.00,
    `status` ENUM('ACTIVE', 'FROZEN', 'CLOSED') NOT NULL DEFAULT 'ACTIVE',
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (`customer_id`) REFERENCES `customers`(`id`) ON DELETE RESTRICT,
    CONSTRAINT `chk_balance_non_negative` CHECK (`balance` >= 0.00),
    CONSTRAINT `chk_daily_limit_positive` CHECK (`daily_limit` > 0.00),
    INDEX `idx_acc_number` (`account_number`),
    INDEX `idx_acc_cust` (`customer_id`),
    INDEX `idx_acc_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Transactions Table
CREATE TABLE `transactions` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `reference_number` VARCHAR(50) NOT NULL UNIQUE,
    `source_account_id` BIGINT NOT NULL,
    `destination_account_id` BIGINT NULL,
    `amount` DECIMAL(15,2) NOT NULL,
    `transaction_type` ENUM('DEPOSIT', 'WITHDRAWAL', 'TRANSFER', 'CARD_PAYMENT', 'ONLINE_PAYMENT') NOT NULL,
    `channel` ENUM('ATM', 'POS', 'MOBILE', 'WEB', 'BRANCH') NOT NULL,
    `status` ENUM('COMPLETED', 'PENDING', 'BLOCKED', 'FAILED', 'UNDER_REVIEW') NOT NULL DEFAULT 'PENDING',
    `merchant_name` VARCHAR(100) NULL,
    `merchant_category` VARCHAR(50) NULL,
    `location` VARCHAR(100) NULL,
    `ip_address` VARCHAR(45) NULL,
    `device_id` VARCHAR(100) NULL,
    `idempotency_key` VARCHAR(100) NULL UNIQUE,
    `risk_score` INT NOT NULL DEFAULT 0,
    `risk_level` ENUM('LOW', 'MEDIUM', 'HIGH', 'CRITICAL') NOT NULL DEFAULT 'LOW',
    `fraud_status` ENUM('NOT_SUSPICIOUS', 'SUSPICIOUS', 'CONFIRMED_FRAUD', 'FALSE_POSITIVE') NOT NULL DEFAULT 'NOT_SUSPICIOUS',
    `transaction_timestamp` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (`source_account_id`) REFERENCES `accounts`(`id`),
    FOREIGN KEY (`destination_account_id`) REFERENCES `accounts`(`id`),
    CONSTRAINT `chk_amount_positive` CHECK (`amount` > 0.00),
    INDEX `idx_txn_ref` (`reference_number`),
    INDEX `idx_txn_src_acc` (`source_account_id`),
    INDEX `idx_txn_dst_acc` (`destination_account_id`),
    INDEX `idx_txn_ts` (`transaction_timestamp`),
    INDEX `idx_txn_status` (`status`),
    INDEX `idx_txn_type` (`transaction_type`),
    INDEX `idx_txn_channel` (`channel`),
    INDEX `idx_txn_risk` (`risk_level`),
    INDEX `idx_txn_fraud` (`fraud_status`),
    INDEX `idx_txn_src_ts` (`source_account_id`, `transaction_timestamp`),
    INDEX `idx_txn_velocity` (`source_account_id`, `transaction_timestamp`, `amount`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Fraud Rules Table
CREATE TABLE `fraud_rules` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `rule_code` VARCHAR(50) NOT NULL UNIQUE,
    `rule_name` VARCHAR(100) NOT NULL,
    `description` TEXT NOT NULL,
    `threshold_params` JSON NOT NULL,
    `points` INT NOT NULL,
    `enabled` BOOLEAN NOT NULL DEFAULT TRUE,
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_rule_code` (`rule_code`),
    INDEX `idx_rule_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Fraud Alerts Table
CREATE TABLE `fraud_alerts` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `alert_number` VARCHAR(50) NOT NULL UNIQUE,
    `transaction_id` BIGINT NOT NULL,
    `customer_id` BIGINT NOT NULL,
    `risk_score` INT NOT NULL,
    `risk_level` ENUM('LOW', 'MEDIUM', 'HIGH', 'CRITICAL') NOT NULL,
    `status` ENUM('OPEN', 'UNDER_REVIEW', 'CONFIRMED_FRAUD', 'FALSE_POSITIVE', 'RESOLVED') NOT NULL DEFAULT 'OPEN',
    `assigned_to_user_id` BIGINT NULL,
    `triggered_rules_json` JSON NOT NULL,
    `evidence_json` JSON NOT NULL,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `resolved_at` TIMESTAMP NULL,
    FOREIGN KEY (`transaction_id`) REFERENCES `transactions`(`id`),
    FOREIGN KEY (`customer_id`) REFERENCES `customers`(`id`),
    FOREIGN KEY (`assigned_to_user_id`) REFERENCES `users`(`id`),
    INDEX `idx_alerts_status` (`status`),
    INDEX `idx_alerts_risk` (`risk_level`),
    INDEX `idx_alerts_cust` (`customer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. Alert Notes Table
CREATE TABLE `alert_notes` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `alert_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `note_text` TEXT NOT NULL,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (`alert_id`) REFERENCES `fraud_alerts`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`user_id`) REFERENCES `users`(`id`),
    INDEX `idx_notes_alert` (`alert_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. Risk Scores Table
CREATE TABLE `risk_scores` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `transaction_id` BIGINT NOT NULL,
    `score` INT NOT NULL,
    `level` ENUM('LOW', 'MEDIUM', 'HIGH', 'CRITICAL') NOT NULL,
    `triggered_rules_json` JSON NOT NULL,
    `evidence_json` JSON NOT NULL,
    `recommended_action` VARCHAR(100) NOT NULL,
    `evaluated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (`transaction_id`) REFERENCES `transactions`(`id`) ON DELETE CASCADE,
    INDEX `idx_risk_scores_txn` (`transaction_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 9. Notifications Table
CREATE TABLE `notifications` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NULL,
    `role` ENUM('ADMIN', 'FRAUD_ANALYST', 'VIEWER') NULL,
    `severity` ENUM('INFO', 'WARNING', 'HIGH', 'CRITICAL') NOT NULL,
    `title` VARCHAR(100) NOT NULL,
    `message` TEXT NOT NULL,
    `read_state` BOOLEAN NOT NULL DEFAULT FALSE,
    `entity_type` VARCHAR(50) NULL,
    `entity_id` BIGINT NULL,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (`user_id`) REFERENCES `users`(`id`),
    INDEX `idx_notif_user_read` (`user_id`, `read_state`),
    INDEX `idx_notif_role_read` (`role`, `read_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 10. Audit Log Table
CREATE TABLE `audit_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NULL,
    `username` VARCHAR(50) NOT NULL,
    `role` VARCHAR(50) NOT NULL,
    `action` VARCHAR(100) NOT NULL,
    `entity_type` VARCHAR(50) NOT NULL,
    `entity_id` BIGINT NULL,
    `details_json` JSON NULL,
    `ip_address` VARCHAR(45) NULL,
    `timestamp` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (`user_id`) REFERENCES `users`(`id`),
    INDEX `idx_audit_user` (`user_id`),
    INDEX `idx_audit_action` (`action`),
    INDEX `idx_audit_ts` (`timestamp`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
