package com.intellibank.fraud;

import com.intellibank.model.Account;
import com.intellibank.model.Customer;
import com.intellibank.model.Transaction;

import java.util.List;

public interface FraudRule {
    String getRuleCode();
    String getRuleName();
    String getDescription();
    RuleEvaluationResult evaluate(Transaction transaction, Customer customer, Account sourceAccount, List<Transaction> recentCustomerHistory, FraudRuleConfig config);
}
