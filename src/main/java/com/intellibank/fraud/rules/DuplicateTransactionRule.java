package com.intellibank.fraud.rules;

import com.intellibank.fraud.FraudRule;
import com.intellibank.fraud.FraudRuleConfig;
import com.intellibank.fraud.RuleEvaluationResult;
import com.intellibank.model.Account;
import com.intellibank.model.Customer;
import com.intellibank.model.Transaction;
import com.intellibank.util.dsa.DuplicateHashSet;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

public class DuplicateTransactionRule implements FraudRule {

    private static final DuplicateHashSet duplicateCache = new DuplicateHashSet();

    @Override
    public String getRuleCode() { return "DUPLICATE_TXN"; }

    @Override
    public String getRuleName() { return "Duplicate Transaction Submission"; }

    @Override
    public String getDescription() { return "Flags identical transaction amounts and merchants submitted for the same account within a 2-minute window"; }

    @Override
    public RuleEvaluationResult evaluate(Transaction transaction, Customer customer, Account sourceAccount, List<Transaction> recentCustomerHistory, FraudRuleConfig config) {
        if (config != null && !config.isEnabled()) {
            return new RuleEvaluationResult(getRuleCode(), getRuleName(), false, 0, "Rule disabled");
        }

        int windowMinutes = 2;
        if (config != null && config.getThresholdParams().has("windowMinutes")) {
            windowMinutes = config.getThresholdParams().get("windowMinutes").getAsInt();
        }
        int points = (config != null) ? config.getPoints() : 20;

        LocalDateTime currentTs = transaction.getTransactionTimestamp() != null ? transaction.getTransactionTimestamp() : LocalDateTime.now();
        long epochSeconds = currentTs.toEpochSecond(ZoneOffset.UTC);

        String hashKey = DuplicateHashSet.buildHashKey(
                transaction.getSourceAccountId(),
                transaction.getAmount() != null ? transaction.getAmount() : java.math.BigDecimal.ZERO,
                transaction.getMerchantName(),
                epochSeconds,
                windowMinutes
        );

        boolean triggered = false;
        if (duplicateCache.isDuplicate(hashKey)) {
            triggered = true;
        } else {
            duplicateCache.addIfAbsent(hashKey);
        }

        // Also check against recent customer history
        if (!triggered && recentCustomerHistory != null) {
            LocalDateTime cutoff = currentTs.minusMinutes(windowMinutes);
            for (Transaction t : recentCustomerHistory) {
                if (t.getTransactionTimestamp() != null && t.getTransactionTimestamp().isAfter(cutoff)) {
                    if (t.getAmount() != null && transaction.getAmount() != null && t.getAmount().compareTo(transaction.getAmount()) == 0) {
                        if (t.getMerchantName() != null && t.getMerchantName().equalsIgnoreCase(transaction.getMerchantName())) {
                            triggered = true;
                            break;
                        }
                    }
                }
            }
        }

        String evidence = triggered ?
                String.format("Duplicate transaction submission detected: identical amount $%s and merchant '%s' within %d minutes", transaction.getAmount() != null ? transaction.getAmount().toPlainString() : "0", transaction.getMerchantName(), windowMinutes) :
                "No duplicate submission detected";

        return new RuleEvaluationResult(getRuleCode(), getRuleName(), triggered, points, evidence);
    }
}
