package com.claimsure.claims.fraud;

import java.util.List;

public record FraudAssessment(int score, List<String> flags) {
    public enum Level { LOW, MEDIUM, HIGH }

    public Level level() {
        return score >= 50 ? Level.HIGH : score >= 25 ? Level.MEDIUM : Level.LOW;
    }
}
