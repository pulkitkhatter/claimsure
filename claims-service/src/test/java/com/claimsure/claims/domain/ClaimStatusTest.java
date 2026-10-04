package com.claimsure.claims.domain;

import static com.claimsure.claims.domain.ClaimStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClaimStatusTest {
    @Test
    void happyPathIsAllowed() {
        assertThat(SUBMITTED.canMoveTo(UNDER_REVIEW)).isTrue();
        assertThat(UNDER_REVIEW.canMoveTo(APPROVED)).isTrue();
        assertThat(APPROVED.canMoveTo(PAID)).isTrue();
    }

    @Test
    void cannotSkipReviewOrPayUnapproved() {
        assertThat(SUBMITTED.canMoveTo(APPROVED)).isFalse();
        assertThat(UNDER_REVIEW.canMoveTo(PAID)).isFalse();
    }

    @Test
    void terminalStatesAreFinal() {
        for (ClaimStatus s : new ClaimStatus[] {REJECTED, PAID, WITHDRAWN}) {
            assertThat(s.isTerminal()).isTrue();
            for (ClaimStatus t : ClaimStatus.values()) {
                assertThat(s.canMoveTo(t)).as(s + " -> " + t).isFalse();
            }
        }
    }

    @Test
    void openClaimsCanBeWithdrawnButApprovedOnesCannot() {
        assertThat(SUBMITTED.canMoveTo(WITHDRAWN)).isTrue();
        assertThat(UNDER_REVIEW.canMoveTo(WITHDRAWN)).isTrue();
        assertThat(APPROVED.canMoveTo(WITHDRAWN)).isFalse();
    }
}
