package com.intellibank.fraud.rules;

import com.intellibank.fraud.FraudRule;
import com.intellibank.fraud.FraudRuleConfig;
import com.intellibank.fraud.RuleEvaluationResult;
import com.intellibank.model.Account;
import com.intellibank.model.Customer;
import com.intellibank.model.Transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class DailyThresholdBreachRule implements FraudRule {

    @Override
    public String getRuleCode() { return "DAILY_LIMIT_BREACH"; }

    @Override
    public String getRuleName() { return "Daily Account Limit Breach"; }

    @Override
    public String getDescription() { return "Flags cumulative daily transaction volume that exceeds the account's configured daily spending limit"; }

    @Override
    public RuleEvaluationResult evaluate(Transaction transaction, Customer customer, Account sourceAccount, List<Transaction> recentCustomerHistory, FraudRuleConfig config) {
        if (config != null && !config.isEnabled()) {
            return new RuleEvaluationResult(getRuleCode(), getRuleName(), false, 0, "Rule disabled");
        }

        int points = (config != null) ? config.getPoints() : 15;
        BigDecimal dailyLimit = sourceAccount != null && sourceAccount.getDailyLimit() != null ? sourceAccount.getDailyLimit() : BigDecimal.valueOf(10000.00);
        BigDecimal currentAmount = transaction.getAmount() != null ? transaction.getAmount() : BigDecimal.ZERO;

        BigDecimal todaySum = currentAmount;
        if (recentCustomerHistory != null) {
            for (Transaction t : recentCustomerHistory) {
                if (t.getAmount() != null && ("COMPLETED".equals(t.getStatus()) || "PENDING".equals(t.getStatus()) || "UNDER_REVIEW".equals(t.getStatus()))) {
                    todaySum = todaySum.add(t.getAmount());
                }
            }
        }

        boolean triggered = todaySum.compareTo(dailyLimit) > 0;
        String evidence = triggered ?
                String.format("Cumulative daily spending $%s exceeds account daily limit of $%s (%.1f%% of limit)", todaySum.toPlainString(), dailyLimit.toPlainString(), todaySum.multiply(BigDecimal.valueOf(100)).divide(dailyLimit, 1, RoundingMode.HALF_UP).doubleValue()) :
                String.format("Daily spending $%s within account limit of $%s", todaySum.toPlainString(), dailyLimit.toPlainString());

        return new RuleEvaluationResult(getRuleCode(), getRuleName(), triggered, points, evidence);
    }
}
