package com.intellibank.model;

import java.time.LocalDateTime;

public class FraudAlert {
    private Long id;
    private String alertNumber;
    private Long transactionId;
    private Long customerId;
    private int riskScore;
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
    private String status; // OPEN, UNDER_REVIEW, CONFIRMED_FRAUD, FALSE_POSITIVE, RESOLVED
    private Long assignedToUserId;
    private String triggeredRulesJson;
    private String evidenceJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime resolvedAt;

    // Joined fields for UI
    private String customerName;
    private String transactionReference;
    private String assignedUsername;

    public FraudAlert() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAlertNumber() { return alertNumber; }
    public void setAlertNumber(String alertNumber) { this.alertNumber = alertNumber; }

    public Long getTransactionId() { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public int getRiskScore() { return riskScore; }
    public void setRiskScore(int riskScore) { this.riskScore = riskScore; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getAssignedToUserId() { return assignedToUserId; }
    public void setAssignedToUserId(Long assignedToUserId) { this.assignedToUserId = assignedToUserId; }

    public String getTriggeredRulesJson() { return triggeredRulesJson; }
    public void setTriggeredRulesJson(String triggeredRulesJson) { this.triggeredRulesJson = triggeredRulesJson; }

    public String getEvidenceJson() { return evidenceJson; }
    public void setEvidenceJson(String evidenceJson) { this.evidenceJson = evidenceJson; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getTransactionReference() { return transactionReference; }
    public void setTransactionReference(String transactionReference) { this.transactionReference = transactionReference; }

    public String getAssignedUsername() { return assignedUsername; }
    public void setAssignedUsername(String assignedUsername) { this.assignedUsername = assignedUsername; }
}
