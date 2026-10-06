package com.intellibank.fraud.rules;

import com.intellibank.fraud.FraudRule;
import com.intellibank.fraud.FraudRuleConfig;
import com.intellibank.fraud.RuleEvaluationResult;
import com.intellibank.model.Account;
import com.intellibank.model.Customer;
import com.intellibank.model.Transaction;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

public class LocationAnomalyRule implements FraudRule {

    @Override
    public String getRuleCode() { return "LOCATION_ANOMALY"; }

    @Override
    public String getRuleName() { return "Geographic Location Anomaly"; }

    @Override
    public String getDescription() { return "Flags impossible travel velocity or geographic location shifts between consecutive transactions"; }

    @Override
    public RuleEvaluationResult evaluate(Transaction transaction, Customer customer, Account sourceAccount, List<Transaction> recentCustomerHistory, FraudRuleConfig config) {
        if (config != null && !config.isEnabled()) {
            return new RuleEvaluationResult(getRuleCode(), getRuleName(), false, 0, "Rule disabled");
        }

        int points = (config != null) ? config.getPoints() : 15;
        String currentLoc = transaction.getLocation() != null ? transaction.getLocation().trim() : "";
        String homeLoc = customer != null && customer.getHomeLocation() != null ? customer.getHomeLocation().trim() : "";

        boolean triggered = false;
        String evidence = "Location normal: " + (currentLoc.isEmpty() ? "Local" : currentLoc);

        if (recentCustomerHistory != null && !recentCustomerHistory.isEmpty()) {
            Transaction lastTxn = recentCustomerHistory.get(0); // Most recent transaction
            String lastLoc = lastTxn.getLocation() != null ? lastTxn.getLocation().trim() : "";

            if (!currentLoc.isEmpty() && !lastLoc.isEmpty() && !currentLoc.equalsIgnoreCase(lastLoc)) {
                LocalDateTime currentTs = transaction.getTransactionTimestamp() != null ? transaction.getTransactionTimestamp() : LocalDateTime.now();
                LocalDateTime lastTs = lastTxn.getTransactionTimestamp() != null ? lastTxn.getTransactionTimestamp() : currentTs.minusHours(1);

                long minutesBetween = Math.abs(Duration.between(lastTs, currentTs).toMinutes());
                if (minutesBetween < 180 && isDifferentCountryOrContinent(currentLoc, lastLoc)) {
                    triggered = true;
                    evidence = String.format("Impossible travel velocity: Location shifted from %s to %s within %d minutes", lastLoc, currentLoc, minutesBetween);
                }
            }
        }

        if (!triggered && !currentLoc.isEmpty() && !homeLoc.isEmpty() && !currentLoc.equalsIgnoreCase(homeLoc) && isDifferentCountryOrContinent(currentLoc, homeLoc)) {
            triggered = true;
            evidence = String.format("Foreign location anomaly: Transaction in %s differs from customer home location %s", currentLoc, homeLoc);
        }

        return new RuleEvaluationResult(getRuleCode(), getRuleName(), triggered, points, evidence);
    }

    private boolean isDifferentCountryOrContinent(String loc1, String loc2) {
        String country1 = extractCountry(loc1);
        String country2 = extractCountry(loc2);
        return !country1.equalsIgnoreCase(country2);
    }

    private String extractCountry(String loc) {
        if (loc.contains(",")) {
            String[] parts = loc.split(",");
            return parts[parts.length - 1].trim();
        }
        return loc;
    }
}
