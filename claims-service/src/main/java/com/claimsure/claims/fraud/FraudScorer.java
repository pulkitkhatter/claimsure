package com.claimsure.claims.fraud;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Transparent rule-based screening. It never rejects a claim - it only gives the adjuster a score and the reasons,
 * so a human always makes the decision.
 */
@Component
public class FraudScorer {
    private static final BigDecimal NEAR_LIMIT = new BigDecimal("0.80");
    private static final int EARLY_CLAIM_DAYS = 30;
    private static final int MIN_DESCRIPTION = 30;

    public FraudAssessment assess(BigDecimal amount, BigDecimal coverage, LocalDate incidentDate,
                                  LocalDate policyStart, String description, long claimsOnPolicyLast90Days) {
        int score = 0;
        List<String> flags = new ArrayList<>();

        if (amount.compareTo(coverage.multiply(NEAR_LIMIT)) >= 0) {
            score += 30;
            flags.add("Claim amount is 80% or more of the policy coverage");
        }
        if (ChronoUnit.DAYS.between(policyStart, incidentDate) <= EARLY_CLAIM_DAYS) {
            score += 25;
            flags.add("Incident occurred within " + EARLY_CLAIM_DAYS + " days of policy start");
        }
        if (description == null || description.trim().length() < MIN_DESCRIPTION) {
            score += 10;
            flags.add("Incident description is very short");
        }
        if (claimsOnPolicyLast90Days >= 2) {
            score += 35;
            flags.add(claimsOnPolicyLast90Days + " other claims on this policy in the last 90 days");
        } else if (claimsOnPolicyLast90Days == 1) {
            score += 20;
            flags.add("Another claim on this policy in the last 90 days");
        }
        return new FraudAssessment(Math.min(score, 100), List.copyOf(flags));
    }
}
