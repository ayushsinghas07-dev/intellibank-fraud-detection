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

public class UnusualAmountRule implements FraudRule {

    @Override
    public String getRuleCode() { return "UNUSUAL_AMOUNT"; }

    @Override
    public String getRuleName() { return "Unusual Amount vs Customer Average"; }

    @Override
    public String getDescription() { return "Flags transactions that significantly exceed customer's historical average amount"; }

    @Override
    public RuleEvaluationResult evaluate(Transaction transaction, Customer customer, Account sourceAccount, List<Transaction> recentCustomerHistory, FraudRuleConfig config) {
        if (config != null && !config.isEnabled()) {
            return new RuleEvaluationResult(getRuleCode(), getRuleName(), false, 0, "Rule disabled");
        }

        double multiplier = 3.0;
        if (config != null && config.getThresholdParams().has("multiplier")) {
            multiplier = config.getThresholdParams().get("multiplier").getAsDouble();
        }
        int points = (config != null) ? config.getPoints() : 20;

        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        if (recentCustomerHistory != null) {
            for (Transaction t : recentCustomerHistory) {
                if (t.getAmount() != null && "COMPLETED".equals(t.getStatus())) {
                    sum = sum.add(t.getAmount());
                    count++;
                }
            }
        }

        BigDecimal avg = count > 0 ? sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP) : BigDecimal.valueOf(500.00);
        BigDecimal maxAllowed = avg.multiply(BigDecimal.valueOf(multiplier)).setScale(2, RoundingMode.HALF_UP);

        BigDecimal currentAmount = transaction.getAmount() != null ? transaction.getAmount() : BigDecimal.ZERO;
        boolean triggered = currentAmount.compareTo(maxAllowed) > 0 && currentAmount.compareTo(BigDecimal.valueOf(1000)) > 0;

        String evidence = triggered ?
                String.format("Amount $%s is %.1fx customer 30-day average ($%s, threshold $%s)", currentAmount.toPlainString(), currentAmount.divide(avg, 2, RoundingMode.HALF_UP).doubleValue(), avg.toPlainString(), maxAllowed.toPlainString()) :
                String.format("Amount $%s within normal range of customer average ($%s)", currentAmount.toPlainString(), avg.toPlainString());

        return new RuleEvaluationResult(getRuleCode(), getRuleName(), triggered, points, evidence);
    }
}
