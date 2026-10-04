package com.claimsure.policy.pricing;

import com.claimsure.policy.pricing.PremiumQuote.Factor;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Pure, deterministic rating engine: annual = (coverage / 1000 x base rate) x product(risk factors), floored at a minimum.
 * No I/O, so it is trivially unit-testable and safe to cache.
 */
@Component
public class PremiumCalculator {
    static final BigDecimal MINIMUM_PREMIUM = new BigDecimal("120.00");
    private static final BigDecimal THOUSAND = new BigDecimal("1000");
    private static final BigDecimal CLAIM_STEP = new BigDecimal("0.12");
    private static final int MAX_COUNTED_CLAIMS = 5;

    /** Annual premium per $1,000 of coverage. */
    private static final Map<PolicyType, BigDecimal> BASE_RATE = Map.of(
            PolicyType.AUTO, new BigDecimal("18.0"),
            PolicyType.HOME, new BigDecimal("3.5"),
            PolicyType.HEALTH, new BigDecimal("12.0"),
            PolicyType.LIFE, new BigDecimal("2.2"));

    public PremiumQuote calculate(QuoteRequest r) {
        BigDecimal base = r.coverageAmount().divide(THOUSAND).multiply(BASE_RATE.get(r.type()));
        List<Factor> factors = new ArrayList<>();
        BigDecimal premium = base;

        premium = apply(premium, factors, ageFactorName(r.type()), ageFactor(r.type(), r.customerAge()));

        BigDecimal claims = BigDecimal.ONE.add(CLAIM_STEP.multiply(
                BigDecimal.valueOf(Math.min(r.claimsInLast5Years(), MAX_COUNTED_CLAIMS))));
        premium = apply(premium, factors, "Claims history", claims);

        if (r.smoker() && (r.type() == PolicyType.HEALTH || r.type() == PolicyType.LIFE)) {
            premium = apply(premium, factors, "Smoker", new BigDecimal("1.50"));
        }
        if (r.assetAgeYears() != null) {
            if (r.type() == PolicyType.AUTO) {
                premium = apply(premium, factors, "Vehicle age", vehicleFactor(r.assetAgeYears()));
            } else if (r.type() == PolicyType.HOME) {
                premium = apply(premium, factors, "Building age", buildingFactor(r.assetAgeYears()));
            }
        }
        premium = apply(premium, factors, "Deductible", r.deductible().multiplier());

        BigDecimal annual = premium.max(MINIMUM_PREMIUM).setScale(2, RoundingMode.HALF_UP);
        if (premium.compareTo(MINIMUM_PREMIUM) < 0) {
            factors.add(new Factor("Minimum premium", MINIMUM_PREMIUM.divide(premium, 4, RoundingMode.HALF_UP)));
        }
        return new PremiumQuote(r.type(), r.coverageAmount(), base.setScale(2, RoundingMode.HALF_UP), annual,
                annual.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP), List.copyOf(factors));
    }

    private static BigDecimal apply(BigDecimal premium, List<Factor> factors, String name, BigDecimal multiplier) {
        if (multiplier.compareTo(BigDecimal.ONE) != 0) {
            factors.add(new Factor(name, multiplier));
        }
        return premium.multiply(multiplier);
    }

    private static String ageFactorName(PolicyType t) {
        return t == PolicyType.AUTO ? "Driver age" : "Customer age";
    }

    private static BigDecimal ageFactor(PolicyType type, int age) {
        return new BigDecimal(switch (type) {
            case AUTO -> age < 25 ? "1.60" : age > 65 ? "1.25" : "1.00";
            case HEALTH -> age < 30 ? "0.85" : age < 45 ? "1.00" : age < 60 ? "1.45" : "2.10";
            case LIFE -> age < 30 ? "0.80" : age < 45 ? "1.00" : age < 60 ? "1.90" : "3.40";
            case HOME -> "1.00";
        });
    }

    private static BigDecimal vehicleFactor(int years) {
        return new BigDecimal(years <= 3 ? "1.15" : years <= 10 ? "1.00" : "0.90");
    }

    private static BigDecimal buildingFactor(int years) {
        return new BigDecimal(years > 40 ? "1.25" : years >= 20 ? "1.10" : "1.00");
    }
}
