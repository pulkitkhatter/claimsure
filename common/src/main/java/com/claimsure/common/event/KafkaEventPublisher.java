package com.claimsure.common.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

public class KafkaEventPublisher implements EventPublisher {
    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final String topic;

    public KafkaEventPublisher(KafkaTemplate<String, String> kafka, ObjectMapper mapper, String topic) {
        this.kafka = kafka;
        this.mapper = mapper;
        this.topic = topic;
    }

    @Override
    public void publish(DomainEvent event) {
        try {
            // key = entity id -> all events of one policy/claim land on one partition, preserving their order
            kafka.send(topic, event.entityId(), mapper.writeValueAsString(event))
                    .whenComplete((r, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish {} to {}", event.type(), topic, ex);
                        } else {
                            log.debug("Published {} to {} offset {}", event.type(), topic, r.getRecordMetadata().offset());
                        }
                    });
        } catch (JsonProcessingException e) {
            log.error("Could not serialise event {}", event.type(), e);
        }
    }
}
