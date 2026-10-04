package com.claimsure.claims.domain;

import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ClaimRepository extends MongoRepository<Claim, String> {
    Page<Claim> findByCustomerId(String customerId, Pageable pageable);

    Page<Claim> findByStatus(ClaimStatus status, Pageable pageable);

    long countByPolicyIdAndCreatedAtAfter(String policyId, Instant after);
}
