package com.intellibank.service;
import com.intellibank.util.JsonUtil;

import com.google.gson.Gson;
import com.intellibank.config.DatabaseConfig;
import com.intellibank.dao.*;
import com.intellibank.exception.BusinessException;
import com.intellibank.fraud.FraudDetectionService;
import com.intellibank.fraud.FraudDetectionService.FraudResult;
import com.intellibank.fraud.RuleEvaluationResult;
import com.intellibank.model.*;
import com.intellibank.risk.RiskScoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class TransactionService {
    private static final Logger logger = LoggerFactory.getLogger(TransactionService.class);
    private static final Gson gson = JsonUtil.getGson();

    private final AccountDao accountDao;
    private final CustomerDao customerDao;
    private final TransactionDao transactionDao;
    private final FraudAlertDao alertDao;
    private final RiskScoreDao riskScoreDao;
    private final NotificationDao notificationDao;
    private final AuditLogDao auditLogDao;
    private final FraudDetectionService fraudDetectionService;

    public TransactionService() {
        this.accountDao = new AccountDao();
        this.customerDao = new CustomerDao();
        this.transactionDao = new TransactionDao();
        this.alertDao = new FraudAlertDao();
        this.riskScoreDao = new RiskScoreDao();
        this.notificationDao = new NotificationDao();
        this.auditLogDao = new AuditLogDao();
        this.fraudDetectionService = new FraudDetectionService();
    }

    public TransactionService(AccountDao accountDao, CustomerDao customerDao, TransactionDao transactionDao,
                              FraudAlertDao alertDao, RiskScoreDao riskScoreDao, NotificationDao notificationDao,
                              AuditLogDao auditLogDao, FraudDetectionService fraudDetectionService) {
        this.accountDao = accountDao;
        this.customerDao = customerDao;
        this.transactionDao = transactionDao;
        this.alertDao = alertDao;
        this.riskScoreDao = riskScoreDao;
        this.notificationDao = notificationDao;
        this.auditLogDao = auditLogDao;
        this.fraudDetectionService = fraudDetectionService;
    }

    /**
     * Executes the complete transaction pipeline.
     */
    public Transaction processTransaction(Transaction request, User currentUser) {
        // Step 0: Idempotency Check
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().trim().isEmpty()) {
            Optional<Transaction> existing = transactionDao.findByIdempotencyKey(request.getIdempotencyKey().trim());
            if (existing.isPresent()) {
                logger.info("Idempotent retry detected for key: {}. Returning original transaction {}", request.getIdempotencyKey(), existing.get().getReferenceNumber());
                return existing.get();
            }
        } else {
            request.setIdempotencyKey("IDEMP-" + UUID.randomUUID().toString().substring(0, 12));
        }

        // Step 1: Input Validation
        validateTransactionRequest(request);

        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();
            conn.setAutoCommit(false);

            // Lock accounts in ascending ID order to prevent deadlocks
            List<Account> lockedAccounts = accountDao.lockAccountsForUpdate(conn, request.getSourceAccountId(), request.getDestinationAccountId());
            Account srcAccount = lockedAccounts.stream().filter(a -> a.getId().equals(request.getSourceAccountId())).findFirst()
                    .orElseThrow(() -> new BusinessException("SOURCE_ACCOUNT_NOT_FOUND", "Source account not found"));

            Account dstAccount = null;
            if (request.getDestinationAccountId() != null) {
                dstAccount = lockedAccounts.stream().filter(a -> a.getId().equals(request.getDestinationAccountId())).findFirst()
                        .orElseThrow(() -> new BusinessException("DESTINATION_ACCOUNT_NOT_FOUND", "Destination account not found"));
            }

            // Check Account Statuses
            if (!"ACTIVE".equalsIgnoreCase(srcAccount.getStatus())) {
                throw new BusinessException("ACCOUNT_FROZEN", "Source account is " + srcAccount.getStatus() + ". Transactions cannot be processed.");
            }
            if (dstAccount != null && !"ACTIVE".equalsIgnoreCase(dstAccount.getStatus())) {
                throw new BusinessException("DESTINATION_ACCOUNT_FROZEN", "Destination account is " + dstAccount.getStatus());
            }

            // Check Balance for Debit Operations
            if ("WITHDRAWAL".equalsIgnoreCase(request.getTransactionType()) || "TRANSFER".equalsIgnoreCase(request.getTransactionType()) ||
                "CARD_PAYMENT".equalsIgnoreCase(request.getTransactionType()) || "ONLINE_PAYMENT".equalsIgnoreCase(request.getTransactionType())) {
                
                if (srcAccount.getBalance().compareTo(request.getAmount()) < 0) {
                    throw new BusinessException("INSUFFICIENT_FUNDS", String.format("Insufficient balance. Available: $%s, Required: $%s", srcAccount.getBalance().toPlainString(), request.getAmount().toPlainString()));
                }

                // Check Daily Limit
                BigDecimal todaySpent = transactionDao.getDailySpendingTotal(conn, srcAccount.getId(), LocalDate.now());
                BigDecimal newDailyTotal = todaySpent.add(request.getAmount());
                if (newDailyTotal.compareTo(srcAccount.getDailyLimit()) > 0) {
                    throw new BusinessException("DAILY_LIMIT_EXCEEDED", String.format("Transaction exceeds daily spending limit of $%s. Current daily total: $%s", srcAccount.getDailyLimit().toPlainString(), todaySpent.toPlainString()));
                }
            }

            Customer customer = customerDao.findById(srcAccount.getCustomerId())
                    .orElseThrow(() -> new BusinessException("CUSTOMER_NOT_FOUND", "Customer profile not found"));

            List<Transaction> recentHistory = transactionDao.findByAccountId(srcAccount.getId(), 50);

            // Step 2: Fraud Engine & Risk Scoring BEFORE moving funds
            FraudResult fraudResult = fraudDetectionService.evaluateTransaction(request, customer, srcAccount, recentHistory);
            int riskScore = fraudResult.getTotalScore();
            String riskLevel = RiskScoringService.calculateRiskLevel(riskScore);
            String action = RiskScoringService.getRecommendedAction(riskLevel);

            request.setRiskScore(riskScore);
            request.setRiskLevel(riskLevel);
            if (request.getReferenceNumber() == null) {
                request.setReferenceNumber("TXN-" + System.currentTimeMillis() + "-" + (100 + (int)(Math.random() * 900)));
            }

            List<String> triggeredRuleNames = new ArrayList<>();
            List<String> evidenceList = new ArrayList<>();
            for (RuleEvaluationResult r : fraudResult.getTriggeredRules()) {
                triggeredRuleNames.add(r.getRuleName() + " (+" + r.getPoints() + " pts)");
                evidenceList.add(r.getEvidence());
            }
            String triggeredRulesJson = gson.toJson(triggeredRuleNames);
            String evidenceJson = gson.toJson(evidenceList);

            // Step 3: Decision by Risk Level
            if ("LOW".equals(riskLevel) || "MEDIUM".equals(riskLevel)) {
                // Complete funds transfer
                request.setStatus("COMPLETED");
                request.setFraudStatus("MEDIUM".equals(riskLevel) ? "SUSPICIOUS" : "NOT_SUSPICIOUS");

                if ("DEPOSIT".equalsIgnoreCase(request.getTransactionType())) {
                    accountDao.updateBalance(conn, srcAccount.getId(), srcAccount.getBalance().add(request.getAmount()));
                } else if ("WITHDRAWAL".equalsIgnoreCase(request.getTransactionType()) || "CARD_PAYMENT".equalsIgnoreCase(request.getTransactionType()) || "ONLINE_PAYMENT".equalsIgnoreCase(request.getTransactionType())) {
                    accountDao.updateBalance(conn, srcAccount.getId(), srcAccount.getBalance().subtract(request.getAmount()));
                } else if ("TRANSFER".equalsIgnoreCase(request.getTransactionType()) && dstAccount != null) {
                    accountDao.updateBalance(conn, srcAccount.getId(), srcAccount.getBalance().subtract(request.getAmount()));
                    accountDao.updateBalance(conn, dstAccount.getId(), dstAccount.getBalance().add(request.getAmount()));
                }
            } else if ("HIGH".equals(riskLevel)) {
                // Hold funds as UNDER_REVIEW (Do NOT move money)
                request.setStatus("UNDER_REVIEW");
                request.setFraudStatus("SUSPICIOUS");
            } else if ("CRITICAL".equals(riskLevel)) {
                // Block transaction (Do NOT move money)
                request.setStatus("BLOCKED");
                request.setFraudStatus("SUSPICIOUS");
            }

            // Step 4: Persist transaction & risk score atomically
            Transaction persistedTxn = transactionDao.create(conn, request);

            RiskScore rs = new RiskScore();
            rs.setTransactionId(persistedTxn.getId());
            rs.setScore(riskScore);
            rs.setLevel(riskLevel);
            rs.setTriggeredRulesJson(triggeredRulesJson);
            rs.setEvidenceJson(evidenceJson);
            rs.setRecommendedAction(action);
            riskScoreDao.create(conn, rs);

            // Step 5: Create Fraud Alerts & Notifications if MEDIUM/HIGH/CRITICAL
            if (riskScore >= 30) {
                FraudAlert alert = new FraudAlert();
                alert.setAlertNumber("ALT-" + System.currentTimeMillis());
                alert.setTransactionId(persistedTxn.getId());
                alert.setCustomerId(customer.getId());
                alert.setRiskScore(riskScore);
                alert.setRiskLevel(riskLevel);
                alert.setStatus("HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel) ? "OPEN" : "RESOLVED");
                alert.setTriggeredRulesJson(triggeredRulesJson);
                alert.setEvidenceJson(evidenceJson);
                alertDao.create(conn, alert);

                Notification notif = new Notification();
                notif.setRole("FRAUD_ANALYST");
                notif.setSeverity("CRITICAL".equals(riskLevel) ? "CRITICAL" : ("HIGH".equals(riskLevel) ? "HIGH" : "WARNING"));
                notif.setTitle("Fraud Alert: " + alert.getAlertNumber());
                notif.setMessage(String.format("Transaction %s ($%s) flagged with risk score %d/100 (%s)", persistedTxn.getReferenceNumber(), persistedTxn.getAmount().toPlainString(), riskScore, riskLevel));
                notif.setEntityType("FRAUD_ALERT");
                notif.setEntityId(alert.getId());
                notificationDao.create(conn, notif);
            }

            // Step 6: Create Audit Entry
            AuditLog audit = new AuditLog();
            audit.setUserId(currentUser != null ? currentUser.getId() : null);
            audit.setUsername(currentUser != null ? currentUser.getUsername() : "system");
            audit.setRole(currentUser != null ? currentUser.getRole() : "SYSTEM");
            audit.setAction("EXECUTE_TRANSACTION");
            audit.setEntityType("TRANSACTION");
            audit.setEntityId(persistedTxn.getId());
            audit.setDetailsJson(gson.toJson(persistedTxn));
            auditLogDao.create(conn, audit);

            conn.commit();
            return persistedTxn;

        } catch (Exception e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ignored) {}
            }
            logger.error("Transaction failed: {}", e.getMessage());
            if (e instanceof BusinessException) throw (BusinessException) e;
            throw new BusinessException("TRANSACTION_FAILED", "Failed to process transaction: " + e.getMessage());
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
            }
        }
    }

    /**
     * Alert resolution flow by Fraud Analyst.
     */
    public boolean resolveFraudAlert(Long alertId, String newStatus, String noteText, boolean freezeAccount, User analyst) {
        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();
            conn.setAutoCommit(false);

            FraudAlert alert = alertDao.findById(alertId)
                    .orElseThrow(() -> new BusinessException("ALERT_NOT_FOUND", "Fraud alert not found"));

            Transaction txn = transactionDao.findById(alert.getTransactionId())
                    .orElseThrow(() -> new BusinessException("TRANSACTION_NOT_FOUND", "Transaction not found"));

            if ("FALSE_POSITIVE".equalsIgnoreCase(newStatus)) {
                // False Positive -> Release funds if transaction was UNDER_REVIEW
                if ("UNDER_REVIEW".equalsIgnoreCase(txn.getStatus())) {
                    List<Account> lockedAccounts = accountDao.lockAccountsForUpdate(conn, txn.getSourceAccountId(), txn.getDestinationAccountId());
                    Account srcAcc = lockedAccounts.stream().filter(a -> a.getId().equals(txn.getSourceAccountId())).findFirst().get();

                    if (srcAcc.getBalance().compareTo(txn.getAmount()) < 0) {
                        throw new BusinessException("INSUFFICIENT_FUNDS", "Cannot release funds: source account balance is insufficient");
                    }

                    if ("DEPOSIT".equalsIgnoreCase(txn.getTransactionType())) {
                        accountDao.updateBalance(conn, srcAcc.getId(), srcAcc.getBalance().add(txn.getAmount()));
                    } else if ("WITHDRAWAL".equalsIgnoreCase(txn.getTransactionType()) || "CARD_PAYMENT".equalsIgnoreCase(txn.getTransactionType()) || "ONLINE_PAYMENT".equalsIgnoreCase(txn.getTransactionType())) {
                        accountDao.updateBalance(conn, srcAcc.getId(), srcAcc.getBalance().subtract(txn.getAmount()));
                    } else if ("TRANSFER".equalsIgnoreCase(txn.getTransactionType()) && txn.getDestinationAccountId() != null) {
                        Account dstAcc = lockedAccounts.stream().filter(a -> a.getId().equals(txn.getDestinationAccountId())).findFirst().get();
                        accountDao.updateBalance(conn, srcAcc.getId(), srcAcc.getBalance().subtract(txn.getAmount()));
                        accountDao.updateBalance(conn, dstAcc.getId(), dstAcc.getBalance().add(txn.getAmount()));
                    }

                    transactionDao.updateStatus(conn, txn.getId(), "COMPLETED", "FALSE_POSITIVE");
                }
            } else if ("CONFIRMED_FRAUD".equalsIgnoreCase(newStatus)) {
                // Confirm Fraud -> Keep BLOCKED
                transactionDao.updateStatus(conn, txn.getId(), "BLOCKED", "CONFIRMED_FRAUD");
                if (freezeAccount) {
                    accountDao.updateStatus(txn.getSourceAccountId(), "FROZEN");
                }
            }

            alertDao.updateStatus(conn, alert.getId(), newStatus, analyst.getId());

            if (noteText != null && !noteText.trim().isEmpty()) {
                AlertNote note = new AlertNote();
                note.setAlertId(alert.getId());
                note.setUserId(analyst.getId());
                note.setNoteText(noteText.trim());
                new AlertNoteDao().create(conn, note);
            }

            AuditLog audit = new AuditLog();
            audit.setUserId(analyst.getId());
            audit.setUsername(analyst.getUsername());
            audit.setRole(analyst.getRole());
            audit.setAction("RESOLVE_FRAUD_ALERT");
            audit.setEntityType("FRAUD_ALERT");
            audit.setEntityId(alert.getId());
            audit.setDetailsJson(String.format("{\"newStatus\":\"%s\", \"freezeAccount\":%b}", newStatus, freezeAccount));
            auditLogDao.create(conn, audit);

            conn.commit();
            return true;

        } catch (Exception e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ignored) {}
            }
            if (e instanceof BusinessException) throw (BusinessException) e;
            throw new BusinessException("ALERT_RESOLUTION_FAILED", "Failed to resolve alert: " + e.getMessage());
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
            }
        }
    }

    private void validateTransactionRequest(Transaction t) {
        if (t.getSourceAccountId() == null) {
            throw new BusinessException("INVALID_SOURCE_ACCOUNT", "Source account ID is required");
        }
        if (t.getAmount() == null || t.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVALID_AMOUNT", "Amount must be greater than zero");
        }
        if (t.getAmount().scale() > 2) {
            throw new BusinessException("INVALID_AMOUNT_PRECISION", "Amount cannot have more than 2 decimal places");
        }
        if ("TRANSFER".equalsIgnoreCase(t.getTransactionType()) && t.getDestinationAccountId() == null) {
            throw new BusinessException("INVALID_DESTINATION_ACCOUNT", "Destination account ID is required for transfers");
        }
        if ("TRANSFER".equalsIgnoreCase(t.getTransactionType()) && t.getSourceAccountId().equals(t.getDestinationAccountId())) {
            throw new BusinessException("SAME_ACCOUNT_TRANSFER", "Source and destination account cannot be the same");
        }
    }
}
