package com.intellibank.fraud;
import com.intellibank.util.JsonUtil;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class FraudRuleConfig {
    private String ruleCode;
    private int points;
    private boolean enabled;
    private JsonObject thresholdParams;

    private static final Gson gson = JsonUtil.getGson();

    public FraudRuleConfig() {
        this.thresholdParams = new JsonObject();
    }

    public FraudRuleConfig(String ruleCode, int points, boolean enabled, String jsonParams) {
        this.ruleCode = ruleCode;
        this.points = points;
        this.enabled = enabled;
        if (jsonParams != null && !jsonParams.trim().isEmpty()) {
            try {
                this.thresholdParams = gson.fromJson(jsonParams, JsonObject.class);
            } catch (Exception e) {
                this.thresholdParams = new JsonObject();
            }
        } else {
            this.thresholdParams = new JsonObject();
        }
    }

    public String getRuleCode() { return ruleCode; }
    public int getPoints() { return points; }
    public boolean isEnabled() { return enabled; }
    public JsonObject getThresholdParams() { return thresholdParams; }
}
