package com.intellibank.fraud.rules;

import com.intellibank.fraud.FraudRule;
import com.intellibank.fraud.FraudRuleConfig;
import com.intellibank.fraud.RuleEvaluationResult;
import com.intellibank.model.Account;
import com.intellibank.model.Customer;
import com.intellibank.model.Transaction;
import com.intellibank.util.dsa.SlidingWindowTimestampDeque;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

public class VelocityRule implements FraudRule {

    @Override
    public String getRuleCode() { return "VELOCITY"; }

    @Override
    public String getRuleName() { return "Transaction Velocity Spike"; }

    @Override
    public String getDescription() { return "Flags accounts with unusually high transaction counts within a short sliding window"; }

    @Override
    public RuleEvaluationResult evaluate(Transaction transaction, Customer customer, Account sourceAccount, List<Transaction> recentCustomerHistory, FraudRuleConfig config) {
        if (config != null && !config.isEnabled()) {
            return new RuleEvaluationResult(getRuleCode(), getRuleName(), false, 0, "Rule disabled");
        }

        int maxCount = 5;
        int windowMinutes = 5;
        if (config != null && config.getThresholdParams().has("maxCount")) {
            maxCount = config.getThresholdParams().get("maxCount").getAsInt();
        }
        if (config != null && config.getThresholdParams().has("windowMinutes")) {
            windowMinutes = config.getThresholdParams().get("windowMinutes").getAsInt();
        }
        int points = (config != null) ? config.getPoints() : 15;

        LocalDateTime currentTs = transaction.getTransactionTimestamp() != null ? transaction.getTransactionTimestamp() : LocalDateTime.now();
        LocalDateTime cutoff = currentTs.minusMinutes(windowMinutes);

        SlidingWindowTimestampDeque deque = new SlidingWindowTimestampDeque(windowMinutes);
        long currentEpochMillis = currentTs.toInstant(ZoneOffset.UTC).toEpochMilli();

        if (recentCustomerHistory != null) {
            for (Transaction t : recentCustomerHistory) {
                if (t.getTransactionTimestamp() != null && t.getTransactionTimestamp().isAfter(cutoff) && !t.getTransactionTimestamp().isAfter(currentTs)) {
                    deque.addTimestamp(t.getTransactionTimestamp().toInstant(ZoneOffset.UTC).toEpochMilli());
                }
            }
        }
        deque.addTimestamp(currentEpochMillis);

        int countInWindow = deque.getCountInWindow(currentEpochMillis);
        boolean triggered = countInWindow > maxCount;

        String evidence = triggered ?
                String.format("Detected %d transactions in %d-minute sliding window (exceeds max %d)", countInWindow, windowMinutes, maxCount) :
                String.format("%d transactions in %d-minute sliding window (below max %d)", countInWindow, windowMinutes, maxCount);

        return new RuleEvaluationResult(getRuleCode(), getRuleName(), triggered, points, evidence);
    }
}
