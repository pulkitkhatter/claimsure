package com.claimsure.policy.web;

import com.claimsure.policy.domain.Policy;
import com.claimsure.policy.pricing.Deductible;
import com.claimsure.policy.pricing.PolicyType;
import com.claimsure.policy.pricing.QuoteRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class PolicyDtos {
    private PolicyDtos() {
    }

    public record PurchaseRequest(@NotNull @Valid QuoteRequest quote, @NotNull LocalDate startDate) {
    }

    public record QuoteV1(BigDecimal annualPremium, BigDecimal monthlyPremium) {
    }

    public record PolicyView(String id, String policyNumber, String customerId, PolicyType type,
                             BigDecimal coverageAmount, BigDecimal annualPremium, Deductible deductible,
                             String status, LocalDate startDate, LocalDate endDate) {
        public static PolicyView of(Policy p) {
            return new PolicyView(p.getId(), p.getPolicyNumber(), p.getCustomerId(), p.getType(),
                    p.getCoverageAmount(), p.getAnnualPremium(), p.getDeductible(), p.getStatus().name(),
                    p.getStartDate(), p.getEndDate());
        }
    }

    public record Product(PolicyType type, String name, String description, int minCoverage, int maxCoverage) {
    }
}
