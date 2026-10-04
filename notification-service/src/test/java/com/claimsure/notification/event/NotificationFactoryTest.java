package com.claimsure.notification.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.claimsure.common.event.DomainEvent;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class NotificationFactoryTest {
    private final NotificationFactory factory = new NotificationFactory();

    private static DomainEvent event(String type) {
        return new DomainEvent(type, "e1", "CLM-9", "u1", "c@x.co", "APPROVED", new BigDecimal("120"),
                "Your claim was approved", Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void knownEventsBecomeTitledNotifications() {
        var n = factory.create(event("CLAIM_APPROVED")).orElseThrow();
        assertThat(n.getTitle()).isEqualTo("Claim approved");
        assertThat(n.getMessage()).isEqualTo("Your claim was approved");
        assertThat(n.getUserId()).isEqualTo("u1");
        assertThat(n.getReference()).isEqualTo("CLM-9");
        assertThat(n.isRead()).isFalse();
    }

    @Test
    void unknownEventTypesAreIgnoredForForwardCompatibility() {
        assertThat(factory.create(event("SOMETHING_NEW"))).isEmpty();
    }

    @Test
    void eventsWithoutRecipientAreIgnored() {
        var e = new DomainEvent("CLAIM_PAID", "e1", "CLM-9", null, null, "PAID", null, "x", Instant.now());
        assertThat(factory.create(e)).isEmpty();
    }

    @Test
    void keyIsStablePerEventSoRedeliveryIsIdempotent() {
        assertThat(factory.create(event("CLAIM_PAID")).orElseThrow().getEventKey())
                .isEqualTo(factory.create(event("CLAIM_PAID")).orElseThrow().getEventKey());
    }
}
