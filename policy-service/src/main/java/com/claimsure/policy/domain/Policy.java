package com.claimsure.policy.domain;

import com.claimsure.policy.pricing.Deductible;
import com.claimsure.policy.pricing.PolicyType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("policies")
public class Policy {
    @Id
    private String id;
    @Indexed(unique = true)
    private String policyNumber;
    @Indexed
    private String customerId;
    private String customerEmail;
    private PolicyType type;
    private BigDecimal coverageAmount;
    private BigDecimal annualPremium;
    private Deductible deductible;
    private PolicyStatus status = PolicyStatus.ACTIVE;
    private LocalDate startDate;
    private LocalDate endDate;
    private Instant createdAt = Instant.now();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPolicyNumber() { return policyNumber; }
    public void setPolicyNumber(String v) { this.policyNumber = v; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String v) { this.customerId = v; }
    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String v) { this.customerEmail = v; }
    public PolicyType getType() { return type; }
    public void setType(PolicyType v) { this.type = v; }
    public BigDecimal getCoverageAmount() { return coverageAmount; }
    public void setCoverageAmount(BigDecimal v) { this.coverageAmount = v; }
    public BigDecimal getAnnualPremium() { return annualPremium; }
    public void setAnnualPremium(BigDecimal v) { this.annualPremium = v; }
    public Deductible getDeductible() { return deductible; }
    public void setDeductible(Deductible v) { this.deductible = v; }
    public PolicyStatus getStatus() { return status; }
    public void setStatus(PolicyStatus v) { this.status = v; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate v) { this.startDate = v; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate v) { this.endDate = v; }
    public Instant getCreatedAt() { return createdAt; }
}
