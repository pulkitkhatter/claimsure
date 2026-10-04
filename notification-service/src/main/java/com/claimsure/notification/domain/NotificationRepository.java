package com.claimsure.notification.domain;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NotificationRepository extends MongoRepository<Notification, String> {
    Page<Notification> findByUserId(String userId, Pageable pageable);

    Optional<Notification> findByIdAndUserId(String id, String userId);

    long countByUserIdAndReadFalse(String userId);
}
