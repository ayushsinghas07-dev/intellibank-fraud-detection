package com.intellibank.risk;

import com.intellibank.fraud.FraudDetectionService.FraudResult;
import com.intellibank.fraud.RuleEvaluationResult;

import java.util.List;

public class RiskScoringService {

    public static String calculateRiskLevel(int score) {
        if (score >= 80) return "CRITICAL";
        if (score >= 60) return "HIGH";
        if (score >= 30) return "MEDIUM";
        return "LOW";
    }

    public static String getRecommendedAction(String riskLevel) {
        switch (riskLevel) {
            case "CRITICAL":
                return "BLOCK_TRANSACTION_AND_FLAG_FOR_URGENT_REVIEW";
            case "HIGH":
                return "HOLD_FUNDS_AND_FLAG_FOR_MANUAL_REVIEW";
            case "MEDIUM":
                return "COMPLETE_TRANSACTION_AND_MONITOR_ACTIVITY";
            case "LOW":
            default:
                return "ALLOW_TRANSACTION";
        }
    }

    public static String buildPlainLanguageExplanation(int score, String riskLevel, List<RuleEvaluationResult> triggeredRules) {
        if (triggeredRules == null || triggeredRules.isEmpty()) {
            return String.format("Transaction evaluated with score %d/100 (%s). No suspicious risk patterns detected.", score, riskLevel);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Risk Score %d/100 (%s) - Action: %s. Triggered rules: ", score, riskLevel, getRecommendedAction(riskLevel)));
        for (int i = 0; i < triggeredRules.size(); i++) {
            RuleEvaluationResult r = triggeredRules.get(i);
            sb.append(r.getRuleName()).append(" (+").append(r.getPoints()).append(" pts)");
            if (i < triggeredRules.size() - 1) sb.append(", ");
        }
        return sb.toString();
    }
}
