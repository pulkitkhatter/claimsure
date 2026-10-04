package com.claimsure.policy.pricing;

import java.math.BigDecimal;

/** Higher deductible = customer carries more risk = cheaper premium. */
public enum Deductible {
    LOW("1.15"), STANDARD("1.00"), HIGH("0.85");

    private final BigDecimal multiplier;

    Deductible(String multiplier) {
        this.multiplier = new BigDecimal(multiplier);
    }

    public BigDecimal multiplier() {
        return multiplier;
    }
}
