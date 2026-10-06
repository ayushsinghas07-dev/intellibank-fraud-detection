package com.intellibank.fraud;

import com.intellibank.dao.FraudRuleDao;
import com.intellibank.fraud.rules.*;
import com.intellibank.model.Account;
import com.intellibank.model.Customer;
import com.intellibank.model.FraudRule;
import com.intellibank.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class FraudDetectionService {
    private static final Logger logger = LoggerFactory.getLogger(FraudDetectionService.class);

    private final FraudRuleDao ruleDao;
    private final List<com.intellibank.fraud.FraudRule> rulesRegistry;

    public FraudDetectionService() {
        this(new FraudRuleDao());
    }

    public FraudDetectionService(FraudRuleDao ruleDao) {
        this.ruleDao = ruleDao;
        this.rulesRegistry = new ArrayList<>();
        registerRules();
    }

    private void registerRules() {
        rulesRegistry.add(new HighValueRule());
        rulesRegistry.add(new VelocityRule());
        rulesRegistry.add(new UnusualAmountRule());
        rulesRegistry.add(new LocationAnomalyRule());
        rulesRegistry.add(new ChannelAnomalyRule());
        rulesRegistry.add(new RepeatedFailedAttemptsRule());
        rulesRegistry.add(new RapidLargeTransactionsRule());
        rulesRegistry.add(new HighRiskMerchantRule());
        rulesRegistry.add(new DailyThresholdBreachRule());
        rulesRegistry.add(new DuplicateTransactionRule());
    }

    public FraudResult evaluateTransaction(Transaction transaction, Customer customer, Account sourceAccount, List<Transaction> recentCustomerHistory) {
        List<FraudRule> dbRules = ruleDao.findAll();
        Map<String, FraudRuleConfig> configMap = new HashMap<>();

        for (FraudRule r : dbRules) {
            configMap.put(r.getRuleCode(), new FraudRuleConfig(r.getRuleCode(), r.getPoints(), r.isEnabled(), r.getThresholdParams()));
        }

        int totalScore = 0;
        List<RuleEvaluationResult> triggeredRules = new ArrayList<>();
        List<RuleEvaluationResult> allResults = new ArrayList<>();

        for (com.intellibank.fraud.FraudRule rule : rulesRegistry) {
            FraudRuleConfig config = configMap.get(rule.getRuleCode());
            if (config != null && !config.isEnabled()) {
                continue;
            }

            RuleEvaluationResult result = rule.evaluate(transaction, customer, sourceAccount, recentCustomerHistory, config);
            allResults.add(result);

            if (result.isTriggered()) {
                triggeredRules.add(result);
                totalScore += result.getPoints();
            }
        }

        // Cap total score at 100 max
        if (totalScore > 100) {
            totalScore = 100;
        }

        return new FraudResult(totalScore, triggeredRules, allResults);
    }

    public static class FraudResult {
        private final int totalScore;
        private final List<RuleEvaluationResult> triggeredRules;
        private final List<RuleEvaluationResult> allResults;

        public FraudResult(int totalScore, List<RuleEvaluationResult> triggeredRules, List<RuleEvaluationResult> allResults) {
            this.totalScore = totalScore;
            this.triggeredRules = triggeredRules;
            this.allResults = allResults;
        }

        public int getTotalScore() { return totalScore; }
        public List<RuleEvaluationResult> getTriggeredRules() { return triggeredRules; }
        public List<RuleEvaluationResult> getAllResults() { return allResults; }
    }
}
