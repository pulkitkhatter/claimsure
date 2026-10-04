package com.claimsure.policy.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import com.claimsure.policy.pricing.PremiumQuote.Factor;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PremiumCalculatorTest {
    private final PremiumCalculator calc = new PremiumCalculator();

    private static QuoteRequest req(PolicyType t, String coverage, int age, int claims, boolean smoker,
                                    Integer assetAge, Deductible d) {
        return new QuoteRequest(t, new BigDecimal(coverage), age, claims, smoker, assetAge, d);
    }

    private static BigDecimal money(String v) {
        return new BigDecimal(v);
    }

    @Test
    void autoBaselineIsRatePerThousandOfCoverage() {
        PremiumQuote q = calc.calculate(req(PolicyType.AUTO, "50000", 40, 0, false, 5, Deductible.STANDARD));
        assertThat(q.basePremium()).isEqualByComparingTo(money("900.00"));
        assertThat(q.annualPremium()).isEqualByComparingTo(money("900.00"));
        assertThat(q.monthlyPremium()).isEqualByComparingTo(money("75.00"));
    }

    @Test
    void youngAutoDriversPayMore() {
        assertThat(calc.calculate(req(PolicyType.AUTO, "50000", 22, 0, false, 5, Deductible.STANDARD)).annualPremium())
                .isEqualByComparingTo(money("1440.00"));
    }

    @Test
    void eachPriorClaimAddsTwelvePercent() {
        assertThat(calc.calculate(req(PolicyType.AUTO, "50000", 40, 1, false, 5, Deductible.STANDARD)).annualPremium())
                .isEqualByComparingTo(money("1008.00"));
        assertThat(calc.calculate(req(PolicyType.AUTO, "50000", 40, 2, false, 5, Deductible.STANDARD)).annualPremium())
                .isEqualByComparingTo(money("1116.00"));
    }

    @Test
    void claimsLoadingIsCappedAtFiveClaims() {
        var five = calc.calculate(req(PolicyType.AUTO, "50000", 40, 5, false, 5, Deductible.STANDARD));
        var twenty = calc.calculate(req(PolicyType.AUTO, "50000", 40, 20, false, 5, Deductible.STANDARD));
        assertThat(twenty.annualPremium()).isEqualByComparingTo(five.annualPremium())
                .isEqualByComparingTo(money("1440.00"));
    }

    @Test
    void higherDeductibleLowersPremium() {
        assertThat(calc.calculate(req(PolicyType.AUTO, "50000", 40, 0, false, 5, Deductible.HIGH)).annualPremium())
                .isEqualByComparingTo(money("765.00"));
    }

    @Test
    void smokerLoadingOnlyAppliesToHealthAndLife() {
        assertThat(calc.calculate(req(PolicyType.HEALTH, "100000", 50, 0, true, null, Deductible.STANDARD)).annualPremium())
                .isEqualByComparingTo(money("2610.00"));   // 1200 * 1.45 * 1.5
        assertThat(calc.calculate(req(PolicyType.AUTO, "50000", 40, 0, true, 5, Deductible.STANDARD)).annualPremium())
                .isEqualByComparingTo(money("900.00"));
    }

    @Test
    void lifePremiumGrowsSteeplyWithAge() {
        assertThat(calc.calculate(req(PolicyType.LIFE, "200000", 65, 0, false, null, Deductible.STANDARD)).annualPremium())
                .isEqualByComparingTo(money("1496.00"));
    }

    @Test
    void oldBuildingsCostMoreToInsure() {
        var newer = calc.calculate(req(PolicyType.HOME, "400000", 40, 0, false, 5, Deductible.STANDARD));
        var older = calc.calculate(req(PolicyType.HOME, "400000", 40, 0, false, 50, Deductible.STANDARD));
        assertThat(newer.annualPremium()).isEqualByComparingTo(money("1400.00"));
        assertThat(older.annualPremium()).isEqualByComparingTo(money("1750.00"));
    }

    @Test
    void minimumPremiumFloorApplies() {
        assertThat(calc.calculate(req(PolicyType.HOME, "1000", 40, 0, false, 5, Deductible.STANDARD)).annualPremium())
                .isEqualByComparingTo(money("120.00"));
    }

    @Test
    void breakdownExposesEveryNonNeutralFactor() {
        PremiumQuote q = calc.calculate(req(PolicyType.AUTO, "50000", 22, 1, false, 2, Deductible.HIGH));
        assertThat(q.factors()).extracting(Factor::name)
                .contains("Driver age", "Claims history", "Vehicle age", "Deductible");
    }
}
