package com.intellibank.model;

import java.time.LocalDateTime;

public class RiskScore {
    private Long id;
    private Long transactionId;
    private int score;
    private String level; // LOW, MEDIUM, HIGH, CRITICAL
    private String triggeredRulesJson;
    private String evidenceJson;
    private String recommendedAction;
    private LocalDateTime evaluatedAt;

    public RiskScore() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTransactionId() { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public String getTriggeredRulesJson() { return triggeredRulesJson; }
    public void setTriggeredRulesJson(String triggeredRulesJson) { this.triggeredRulesJson = triggeredRulesJson; }

    public String getEvidenceJson() { return evidenceJson; }
    public void setEvidenceJson(String evidenceJson) { this.evidenceJson = evidenceJson; }

    public String getRecommendedAction() { return recommendedAction; }
    public void setRecommendedAction(String recommendedAction) { this.recommendedAction = recommendedAction; }

    public LocalDateTime getEvaluatedAt() { return evaluatedAt; }
    public void setEvaluatedAt(LocalDateTime evaluatedAt) { this.evaluatedAt = evaluatedAt; }
}
