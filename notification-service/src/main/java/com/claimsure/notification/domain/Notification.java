package com.claimsure.notification.domain;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("notifications")
@CompoundIndex(def = "{'userId': 1, 'createdAt': -1}")
public class Notification {
    @Id
    private String id;
    /** type + entity + timestamp: makes Kafka redelivery idempotent via a unique index. */
    @Indexed(unique = true)
    private String eventKey;
    private String userId;
    private String email;
    private String type;
    private String title;
    private String message;
    private String reference;
    private boolean read;
    private Instant createdAt = Instant.now();

    public String getId() { return id; }
    public String getEventKey() { return eventKey; }
    public void setEventKey(String v) { this.eventKey = v; }
    public String getUserId() { return userId; }
    public void setUserId(String v) { this.userId = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }
    public String getType() { return type; }
    public void setType(String v) { this.type = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public String getMessage() { return message; }
    public void setMessage(String v) { this.message = v; }
    public String getReference() { return reference; }
    public void setReference(String v) { this.reference = v; }
    public boolean isRead() { return read; }
    public void setRead(boolean v) { this.read = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
}
