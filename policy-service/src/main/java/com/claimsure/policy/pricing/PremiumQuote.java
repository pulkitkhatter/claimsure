package com.claimsure.policy.pricing;

import java.math.BigDecimal;
import java.util.List;

public record PremiumQuote(PolicyType type, BigDecimal coverageAmount, BigDecimal basePremium,
                           BigDecimal annualPremium, BigDecimal monthlyPremium, List<Factor> factors) {

    public record Factor(String name, BigDecimal multiplier) {
    }
}
