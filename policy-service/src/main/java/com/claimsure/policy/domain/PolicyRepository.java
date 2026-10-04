package com.claimsure.policy.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PolicyRepository extends MongoRepository<Policy, String> {
    Page<Policy> findByCustomerId(String customerId, Pageable pageable);
}
