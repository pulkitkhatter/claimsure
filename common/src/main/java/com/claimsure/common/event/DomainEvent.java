package com.claimsure.common.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One envelope for every business event on Kafka (topics: policy-events, claim-events).
 * Consumers must tolerate unknown {@code type} values (forward compatibility).
 */
public record DomainEvent(
        String type,            // e.g. POLICY_ISSUED, CLAIM_SUBMITTED, CLAIM_APPROVED
        String entityId,
        String entityNumber,    // human friendly: POL-..., CLM-...
        String customerId,
        String customerEmail,
        String status,
        BigDecimal amount,
        String detail,
        Instant occurredAt) {

    public static DomainEvent of(String type, String entityId, String entityNumber, String customerId,
                                 String customerEmail, String status, BigDecimal amount, String detail) {
        return new DomainEvent(type, entityId, entityNumber, customerId, customerEmail, status, amount, detail,
                Instant.now());
    }
}
