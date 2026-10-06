package com.intellibank.fraud.rules;

import com.intellibank.fraud.FraudRule;
import com.intellibank.fraud.FraudRuleConfig;
import com.intellibank.fraud.RuleEvaluationResult;
import com.intellibank.model.Account;
import com.intellibank.model.Customer;
import com.intellibank.model.Transaction;

import java.math.BigDecimal;
import java.util.List;

public class HighValueRule implements FraudRule {

    @Override
    public String getRuleCode() { return "HIGH_VALUE"; }

    @Override
    public String getRuleName() { return "High Value Transaction"; }

    @Override
    public String getDescription() { return "Flags transactions that exceed the configured high-value threshold"; }

    @Override
    public RuleEvaluationResult evaluate(Transaction transaction, Customer customer, Account sourceAccount, List<Transaction> recentCustomerHistory, FraudRuleConfig config) {
        if (config != null && !config.isEnabled()) {
            return new RuleEvaluationResult(getRuleCode(), getRuleName(), false, 0, "Rule disabled");
        }

        double threshold = 10000.00;
        if (config != null && config.getThresholdParams().has("threshold")) {
            threshold = config.getThresholdParams().get("threshold").getAsDouble();
        }
        int points = (config != null) ? config.getPoints() : 20;

        BigDecimal thresholdBd = BigDecimal.valueOf(threshold);
        boolean triggered = transaction.getAmount() != null && transaction.getAmount().compareTo(thresholdBd) >= 0;

        String evidence = triggered ?
                String.format("Transaction amount $%s exceeds high-value threshold of $%s", transaction.getAmount().toPlainString(), thresholdBd.toPlainString()) :
                String.format("Transaction amount $%s below threshold of $%s", transaction.getAmount() != null ? transaction.getAmount().toPlainString() : "0", thresholdBd.toPlainString());

        return new RuleEvaluationResult(getRuleCode(), getRuleName(), triggered, points, evidence);
    }
}
