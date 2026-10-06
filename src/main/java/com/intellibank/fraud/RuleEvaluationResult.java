package com.intellibank.fraud;

public class RuleEvaluationResult {
    private String ruleCode;
    private String ruleName;
    private boolean triggered;
    private int points;
    private String evidence;

    public RuleEvaluationResult() {}

    public RuleEvaluationResult(String ruleCode, String ruleName, boolean triggered, int points, String evidence) {
        this.ruleCode = ruleCode;
        this.ruleName = ruleName;
        this.triggered = triggered;
        this.points = triggered ? points : 0;
        this.evidence = evidence;
    }

    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String ruleCode) { this.ruleCode = ruleCode; }

    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }

    public boolean isTriggered() { return triggered; }
    public void setTriggered(boolean triggered) { this.triggered = triggered; }

    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }

    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }
}
