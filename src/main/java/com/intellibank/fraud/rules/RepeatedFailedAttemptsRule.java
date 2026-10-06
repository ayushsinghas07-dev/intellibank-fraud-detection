package com.intellibank.fraud.rules;

import com.intellibank.fraud.FraudRule;
import com.intellibank.fraud.FraudRuleConfig;
import com.intellibank.fraud.RuleEvaluationResult;
import com.intellibank.model.Account;
import com.intellibank.model.Customer;
import com.intellibank.model.Transaction;

import java.time.LocalDateTime;
import java.util.List;

public class RepeatedFailedAttemptsRule implements FraudRule {

    @Override
    public String getRuleCode() { return "REPEATED_FAILED"; }

    @Override
    public String getRuleName() { return "Repeated Failed Transactions"; }

    @Override
    public String getDescription() { return "Flags multiple failed or declined transaction attempts within a short time window"; }

    @Override
    public RuleEvaluationResult evaluate(Transaction transaction, Customer customer, Account sourceAccount, List<Transaction> recentCustomerHistory, FraudRuleConfig config) {
        if (config != null && !config.isEnabled()) {
            return new RuleEvaluationResult(getRuleCode(), getRuleName(), false, 0, "Rule disabled");
        }

        int maxFailed = 3;
        int windowMinutes = 10;
        if (config != null && config.getThresholdParams().has("maxFailed")) {
            maxFailed = config.getThresholdParams().get("maxFailed").getAsInt();
        }
        if (config != null && config.getThresholdParams().has("windowMinutes")) {
            windowMinutes = config.getThresholdParams().get("windowMinutes").getAsInt();
        }
        int points = (config != null) ? config.getPoints() : 10;

        LocalDateTime currentTs = transaction.getTransactionTimestamp() != null ? transaction.getTransactionTimestamp() : LocalDateTime.now();
        LocalDateTime cutoff = currentTs.minusMinutes(windowMinutes);

        int failedCount = 0;
        if (recentCustomerHistory != null) {
            for (Transaction t : recentCustomerHistory) {
                if (t.getTransactionTimestamp() != null && t.getTransactionTimestamp().isAfter(cutoff)) {
                    if ("FAILED".equals(t.getStatus()) || "BLOCKED".equals(t.getStatus())) {
                        failedCount++;
                    }
                }
            }
        }

        boolean triggered = failedCount >= maxFailed;
        String evidence = triggered ?
                String.format("Detected %d failed/declined attempts in %d-minute window (threshold %d)", failedCount, windowMinutes, maxFailed) :
                String.format("%d failed attempts in %d-minute window (below threshold %d)", failedCount, windowMinutes, maxFailed);

        return new RuleEvaluationResult(getRuleCode(), getRuleName(), triggered, points, evidence);
    }
}
