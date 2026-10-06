package com.intellibank.model;

import java.time.LocalDateTime;

public class FraudRule {
    private Long id;
    private String ruleCode;
    private String ruleName;
    private String description;
    private String thresholdParams; // JSON string
    private int points;
    private boolean enabled;
    private LocalDateTime updatedAt;

    public FraudRule() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String ruleCode) { this.ruleCode = ruleCode; }

    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getThresholdParams() { return thresholdParams; }
    public void setThresholdParams(String thresholdParams) { this.thresholdParams = thresholdParams; }

    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
