package com.intellibank.fraud.rules;

import com.intellibank.fraud.FraudRule;
import com.intellibank.fraud.FraudRuleConfig;
import com.intellibank.fraud.RuleEvaluationResult;
import com.intellibank.model.Account;
import com.intellibank.model.Customer;
import com.intellibank.model.Transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class RapidLargeTransactionsRule implements FraudRule {

    @Override
    public String getRuleCode() { return "RAPID_LARGE_TXN"; }

    @Override
    public String getRuleName() { return "Rapid Consecutive Large Transactions"; }

    @Override
    public String getDescription() { return "Flags multiple large transactions executed in rapid succession"; }

    @Override
    public RuleEvaluationResult evaluate(Transaction transaction, Customer customer, Account sourceAccount, List<Transaction> recentCustomerHistory, FraudRuleConfig config) {
        if (config != null && !config.isEnabled()) {
            return new RuleEvaluationResult(getRuleCode(), getRuleName(), false, 0, "Rule disabled");
        }

        double minAmount = 5000.00;
        int maxCount = 2;
        int windowMinutes = 10;
        if (config != null && config.getThresholdParams().has("minAmount")) {
            minAmount = config.getThresholdParams().get("minAmount").getAsDouble();
        }
        if (config != null && config.getThresholdParams().has("maxCount")) {
            maxCount = config.getThresholdParams().get("maxCount").getAsInt();
        }
        if (config != null && config.getThresholdParams().has("windowMinutes")) {
            windowMinutes = config.getThresholdParams().get("windowMinutes").getAsInt();
        }
        int points = (config != null) ? config.getPoints() : 15;

        BigDecimal minAmountBd = BigDecimal.valueOf(minAmount);
        BigDecimal currentAmount = transaction.getAmount() != null ? transaction.getAmount() : BigDecimal.ZERO;

        int largeTxnCount = 0;
        if (currentAmount.compareTo(minAmountBd) >= 0) largeTxnCount++;

        LocalDateTime currentTs = transaction.getTransactionTimestamp() != null ? transaction.getTransactionTimestamp() : LocalDateTime.now();
        LocalDateTime cutoff = currentTs.minusMinutes(windowMinutes);

        if (recentCustomerHistory != null) {
            for (Transaction t : recentCustomerHistory) {
                if (t.getTransactionTimestamp() != null && t.getTransactionTimestamp().isAfter(cutoff)) {
                    if (t.getAmount() != null && t.getAmount().compareTo(minAmountBd) >= 0) {
                        largeTxnCount++;
                    }
                }
            }
        }

        boolean triggered = largeTxnCount >= maxCount;
        String evidence = triggered ?
                String.format("Detected %d large transactions (>= $%s) in %d-minute window", largeTxnCount, minAmountBd.toPlainString(), windowMinutes) :
                String.format("%d large transactions in %d-minute window (below threshold %d)", largeTxnCount, windowMinutes, maxCount);

        return new RuleEvaluationResult(getRuleCode(), getRuleName(), triggered, points, evidence);
    }
}
