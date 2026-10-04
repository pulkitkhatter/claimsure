package com.claimsure.identity.domain;

import java.time.Instant;
import java.util.Set;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("users")
public class User {
    @Id
    private String id;
    @Indexed(unique = true)
    private String email;
    private String passwordHash;
    private String fullName;
    private Set<String> roles;
    private int failedAttempts;
    private Instant lockedUntil;
    private Instant createdAt = Instant.now();

    public User() {
    }

    public User(String email, String passwordHash, String fullName, Set<String> roles) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.roles = roles;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public Set<String> getRoles() { return roles; }
    public int getFailedAttempts() { return failedAttempts; }
    public void setFailedAttempts(int failedAttempts) { this.failedAttempts = failedAttempts; }
    public Instant getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(Instant lockedUntil) { this.lockedUntil = lockedUntil; }
    public Instant getCreatedAt() { return createdAt; }
}
