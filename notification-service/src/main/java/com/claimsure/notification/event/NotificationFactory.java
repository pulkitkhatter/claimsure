package com.claimsure.notification.event;

import com.claimsure.common.event.DomainEvent;
import com.claimsure.notification.domain.Notification;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class NotificationFactory {
    private static final Map<String, String> TITLES = Map.ofEntries(
            Map.entry("POLICY_ISSUED", "Policy issued"),
            Map.entry("POLICY_CANCELLED", "Policy cancelled"),
            Map.entry("CLAIM_SUBMITTED", "Claim received"),
            Map.entry("CLAIM_UNDER_REVIEW", "Claim under review"),
            Map.entry("CLAIM_APPROVED", "Claim approved"),
            Map.entry("CLAIM_REJECTED", "Claim declined"),
            Map.entry("CLAIM_PAID", "Claim paid"),
            Map.entry("CLAIM_WITHDRAWN", "Claim withdrawn"));

    public Optional<Notification> create(DomainEvent e) {
        String title = TITLES.get(e.type());
        if (title == null || e.customerId() == null) {
            return Optional.empty();
        }
        Notification n = new Notification();
        n.setEventKey(e.type() + ":" + e.entityId() + ":" + e.occurredAt());
        n.setUserId(e.customerId());
        n.setEmail(e.customerEmail());
        n.setType(e.type());
        n.setTitle(title);
        n.setMessage(e.detail());
        n.setReference(e.entityNumber());
        if (e.occurredAt() != null) {
            n.setCreatedAt(e.occurredAt());
        }
        return Optional.of(n);
    }
}
