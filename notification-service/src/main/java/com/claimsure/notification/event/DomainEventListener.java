package com.claimsure.notification.event;

import com.claimsure.common.event.DomainEvent;
import com.claimsure.notification.domain.NotificationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DomainEventListener {
    private static final Logger log = LoggerFactory.getLogger(DomainEventListener.class);

    private final ObjectMapper mapper;
    private final NotificationFactory factory;
    private final NotificationRepository repo;

    public DomainEventListener(ObjectMapper mapper, NotificationFactory factory, NotificationRepository repo) {
        this.mapper = mapper.copy().configure(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.factory = factory;
        this.repo = repo;
    }

    @KafkaListener(topics = {"${claimsure.topics.policy-events:policy-events}",
            "${claimsure.topics.claim-events:claim-events}"}, groupId = "notification-service")
    public void onEvent(String payload) {
        DomainEvent event;
        try {
            event = mapper.readValue(payload, DomainEvent.class);
        } catch (JsonProcessingException e) {
            log.warn("Skipping malformed event: {}", e.getOriginalMessage());
            return;
        }
        factory.create(event).ifPresent(n -> {
            try {
                repo.save(n);
                log.info("Notification stored for {} ({})", n.getUserId(), n.getType());
            } catch (DuplicateKeyException dup) {
                log.debug("Duplicate delivery ignored for {}", n.getEventKey());
            }
        });
    }
}
