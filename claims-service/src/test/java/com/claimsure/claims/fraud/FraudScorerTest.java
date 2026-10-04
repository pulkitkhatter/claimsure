package com.claimsure.claims.fraud;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FraudScorerTest {
    private final FraudScorer scorer = new FraudScorer();
    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final String LONG_TEXT = "Rear-ended at traffic lights on Main Street, police report filed.";

    private FraudAssessment score(String amount, String coverage, LocalDate incident, String text, long recent) {
        return scorer.assess(new BigDecimal(amount), new BigDecimal(coverage), incident, START, text, recent);
    }

    @Test
    void ordinaryClaimIsLowRisk() {
        FraudAssessment a = score("2000", "50000", LocalDate.of(2026, 5, 1), LONG_TEXT, 0);
        assertThat(a.score()).isZero();
        assertThat(a.level()).isEqualTo(FraudAssessment.Level.LOW);
        assertThat(a.flags()).isEmpty();
    }

    @Test
    void claimNearFullCoverageIsFlagged() {
        FraudAssessment a = score("45000", "50000", LocalDate.of(2026, 5, 1), LONG_TEXT, 0);
        assertThat(a.score()).isEqualTo(30);
        assertThat(a.flags()).hasSize(1);
    }

    @Test
    void incidentSoonAfterPolicyStartIsFlagged() {
        assertThat(score("2000", "50000", LocalDate.of(2026, 1, 15), LONG_TEXT, 0).score()).isEqualTo(25);
    }

    @Test
    void sparseDescriptionIsFlagged() {
        assertThat(score("2000", "50000", LocalDate.of(2026, 5, 1), "car broke", 0).score()).isEqualTo(10);
    }

    @Test
    void repeatClaimsEscalate() {
        assertThat(score("2000", "50000", LocalDate.of(2026, 5, 1), LONG_TEXT, 1).score()).isEqualTo(20);
        assertThat(score("2000", "50000", LocalDate.of(2026, 5, 1), LONG_TEXT, 3).score()).isEqualTo(35);
    }

    @Test
    void combinedSignalsReachHighAndAreCapped() {
        FraudAssessment a = score("90000", "100000", LocalDate.of(2026, 1, 10), "stolen", 4);
        assertThat(a.level()).isEqualTo(FraudAssessment.Level.HIGH);
        assertThat(a.score()).isLessThanOrEqualTo(100).isGreaterThanOrEqualTo(50);
        assertThat(a.flags().size()).isGreaterThanOrEqualTo(4);
    }
}
