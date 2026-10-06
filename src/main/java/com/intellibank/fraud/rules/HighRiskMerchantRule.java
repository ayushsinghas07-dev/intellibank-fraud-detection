package com.intellibank.fraud.rules;

import com.intellibank.fraud.FraudRule;
import com.intellibank.fraud.FraudRuleConfig;
import com.intellibank.fraud.RuleEvaluationResult;
import com.intellibank.model.Account;
import com.intellibank.model.Customer;
import com.intellibank.model.Transaction;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HighRiskMerchantRule implements FraudRule {

    private static final Set<String> DEFAULT_HIGH_RISK_CATEGORIES = new HashSet<>(
            Arrays.asList("crypto", "gambling", "offshore", "wire transfer", "cash advance", "pawn shop")
    );

    @Override
    public String getRuleCode() { return "HIGH_RISK_MERCHANT"; }

    @Override
    public String getRuleName() { return "High Risk Merchant Category"; }

    @Override
    public String getDescription() { return "Flags transactions associated with high-risk merchant categories (Crypto, Gambling, Offshore, Wire)"; }

    @Override
    public RuleEvaluationResult evaluate(Transaction transaction, Customer customer, Account sourceAccount, List<Transaction> recentCustomerHistory, FraudRuleConfig config) {
        if (config != null && !config.isEnabled()) {
            return new RuleEvaluationResult(getRuleCode(), getRuleName(), false, 0, "Rule disabled");
        }

        int points = (config != null) ? config.getPoints() : 10;
        String merchantName = transaction.getMerchantName() != null ? transaction.getMerchantName().toLowerCase() : "";
        String merchantCategory = transaction.getMerchantCategory() != null ? transaction.getMerchantCategory().toLowerCase() : "";

        boolean triggered = false;
        String matchedCategory = "";

        for (String cat : DEFAULT_HIGH_RISK_CATEGORIES) {
            if (merchantCategory.contains(cat) || merchantName.contains(cat)) {
                triggered = true;
                matchedCategory = cat;
                break;
            }
        }

        String evidence = triggered ?
                String.format("Transaction with merchant '%s' matched high-risk category '%s'", transaction.getMerchantName(), matchedCategory) :
                String.format("Merchant '%s' category normal", transaction.getMerchantName() != null ? transaction.getMerchantName() : "N/A");

        return new RuleEvaluationResult(getRuleCode(), getRuleName(), triggered, points, evidence);
    }
}
