package com.claimsure.common.event;

public interface EventPublisher {
    /** Fire-and-forget: a broker outage must never roll back a committed business operation. */
    void publish(DomainEvent event);
}
