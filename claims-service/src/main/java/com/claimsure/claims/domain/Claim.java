package com.claimsure.claims.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("claims")
public class Claim {
    public record StatusChange(ClaimStatus from, ClaimStatus to, String by, String note, Instant at) {
    }

    @Id
    private String id;
    @Version
    private Long version;   // optimistic locking: two adjusters cannot both decide the same claim
    @Indexed(unique = true)
    private String claimNumber;
    @Indexed
    private String policyId;
    private String policyNumber;
    @Indexed
    private String customerId;
    private String customerEmail;
    private LocalDate incidentDate;
    private String description;
    private BigDecimal amountClaimed;
    private BigDecimal approvedAmount;
    private ClaimStatus status = ClaimStatus.SUBMITTED;
    private int fraudScore;
    private List<String> fraudFlags = new ArrayList<>();
    private List<StatusChange> history = new ArrayList<>();
    private Instant createdAt = Instant.now();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Long getVersion() { return version; }
    public String getClaimNumber() { return claimNumber; }
    public void setClaimNumber(String v) { this.claimNumber = v; }
    public String getPolicyId() { return policyId; }
    public void setPolicyId(String v) { this.policyId = v; }
    public String getPolicyNumber() { return policyNumber; }
    public void setPolicyNumber(String v) { this.policyNumber = v; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String v) { this.customerId = v; }
    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String v) { this.customerEmail = v; }
    public LocalDate getIncidentDate() { return incidentDate; }
    public void setIncidentDate(LocalDate v) { this.incidentDate = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public BigDecimal getAmountClaimed() { return amountClaimed; }
    public void setAmountClaimed(BigDecimal v) { this.amountClaimed = v; }
    public BigDecimal getApprovedAmount() { return approvedAmount; }
    public void setApprovedAmount(BigDecimal v) { this.approvedAmount = v; }
    public ClaimStatus getStatus() { return status; }
    public void setStatus(ClaimStatus v) { this.status = v; }
    public int getFraudScore() { return fraudScore; }
    public void setFraudScore(int v) { this.fraudScore = v; }
    public List<String> getFraudFlags() { return fraudFlags; }
    public void setFraudFlags(List<String> v) { this.fraudFlags = v; }
    public List<StatusChange> getHistory() { return history; }
    public Instant getCreatedAt() { return createdAt; }
}
