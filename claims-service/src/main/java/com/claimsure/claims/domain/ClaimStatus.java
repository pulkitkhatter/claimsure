package com.claimsure.claims.domain;

import java.util.EnumSet;
import java.util.Set;

/** SUBMITTED -> UNDER_REVIEW -> APPROVED -> PAID, with REJECTED / WITHDRAWN as the other exits. */
public enum ClaimStatus {
    SUBMITTED, UNDER_REVIEW, APPROVED, REJECTED, PAID, WITHDRAWN;

    private Set<ClaimStatus> next() {
        return switch (this) {
            case SUBMITTED -> EnumSet.of(UNDER_REVIEW, WITHDRAWN);
            case UNDER_REVIEW -> EnumSet.of(APPROVED, REJECTED, WITHDRAWN);
            case APPROVED -> EnumSet.of(PAID);
            case REJECTED, PAID, WITHDRAWN -> EnumSet.noneOf(ClaimStatus.class);
        };
    }

    public boolean canMoveTo(ClaimStatus target) {
        return next().contains(target);
    }

    public boolean isTerminal() {
        return next().isEmpty();
    }
}
