package com.claimsure.notification.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.claimsure.notification.domain.Notification;
import com.claimsure.notification.domain.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class DomainEventListenerTest {
    private final NotificationRepository repo = mock(NotificationRepository.class);
    private final DomainEventListener listener = new DomainEventListener(
            new ObjectMapper().registerModule(new JavaTimeModule()), new NotificationFactory(), repo);

    private static final String JSON = """
            {"type":"CLAIM_PAID","entityId":"c1","entityNumber":"CLM-1","customerId":"u1","customerEmail":"a@b.co",
             "status":"PAID","amount":10,"detail":"Paid","occurredAt":"2026-01-01T00:00:00Z","futureField":1}""";

    @Test
    void storesNotificationForValidEventAndToleratesUnknownFields() {
        listener.onEvent(JSON);
        verify(repo).save(any(Notification.class));
    }

    @Test
    void malformedPayloadIsSkippedNotRetriedForever() {
        listener.onEvent("{not json");
        verify(repo, never()).save(any());
    }

    @Test
    void redeliveredEventIsSwallowed() {
        when(repo.save(any())).thenThrow(new DuplicateKeyException("dup"));
        listener.onEvent(JSON); // must not throw
    }
}
