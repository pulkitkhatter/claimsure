package com.claimsure.policy.pricing;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record QuoteRequest(
        @NotNull PolicyType type,
        @NotNull @DecimalMin("1000") @DecimalMax("5000000") BigDecimal coverageAmount,
        @Min(18) @Max(99) int customerAge,
        @Min(0) @Max(20) int claimsInLast5Years,
        boolean smoker,
        @Min(0) @Max(100) Integer assetAgeYears,   // vehicle age (AUTO) or building age (HOME); ignored otherwise
        @NotNull Deductible deductible) {

    /** Scale-insensitive key so 50000 and 50000.00 share one cache entry. */
    public String cacheKey() {
        return type + "|" + coverageAmount.stripTrailingZeros().toPlainString() + "|" + customerAge + "|"
                + claimsInLast5Years + "|" + smoker + "|" + assetAgeYears + "|" + deductible;
    }
}
