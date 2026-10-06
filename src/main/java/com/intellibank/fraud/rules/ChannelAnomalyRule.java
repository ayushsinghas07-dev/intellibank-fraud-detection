package com.intellibank.fraud.rules;

import com.intellibank.fraud.FraudRule;
import com.intellibank.fraud.FraudRuleConfig;
import com.intellibank.fraud.RuleEvaluationResult;
import com.intellibank.model.Account;
import com.intellibank.model.Customer;
import com.intellibank.model.Transaction;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChannelAnomalyRule implements FraudRule {

    @Override
    public String getRuleCode() { return "CHANNEL_ANOMALY"; }

    @Override
    public String getRuleName() { return "Channel Switching Anomaly"; }

    @Override
    public String getDescription() { return "Flags rapid channel switching across 3 or more distinct channels in a short time window"; }

    @Override
    public RuleEvaluationResult evaluate(Transaction transaction, Customer customer, Account sourceAccount, List<Transaction> recentCustomerHistory, FraudRuleConfig config) {
        if (config != null && !config.isEnabled()) {
            return new RuleEvaluationResult(getRuleCode(), getRuleName(), false, 0, "Rule disabled");
        }

        int maxChannels = 3;
        if (config != null && config.getThresholdParams().has("maxChannelsInWindow")) {
            maxChannels = config.getThresholdParams().get("maxChannelsInWindow").getAsInt();
        }
        int points = (config != null) ? config.getPoints() : 10;

        Set<String> channelsUsed = new HashSet<>();
        if (transaction.getChannel() != null) channelsUsed.add(transaction.getChannel());

        LocalDateTime currentTs = transaction.getTransactionTimestamp() != null ? transaction.getTransactionTimestamp() : LocalDateTime.now();
        LocalDateTime cutoff = currentTs.minusMinutes(30);

        if (recentCustomerHistory != null) {
            for (Transaction t : recentCustomerHistory) {
                if (t.getTransactionTimestamp() != null && t.getTransactionTimestamp().isAfter(cutoff) && t.getChannel() != null) {
                    channelsUsed.add(t.getChannel());
                }
            }
        }

        boolean triggered = channelsUsed.size() >= maxChannels;
        String evidence = triggered ?
                String.format("Rapid channel switching detected: %d distinct channels used in 30 minutes %s", channelsUsed.size(), channelsUsed.toString()) :
                String.format("Channel usage normal: %d channels used (%s)", channelsUsed.size(), channelsUsed.toString());

        return new RuleEvaluationResult(getRuleCode(), getRuleName(), triggered, points, evidence);
    }
}
