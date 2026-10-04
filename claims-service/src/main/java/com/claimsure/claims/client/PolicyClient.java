package com.claimsure.claims.client;

public interface PolicyClient {
    /** Fetches the policy using the caller's own token, so policy-service enforces ownership for us. */
    PolicyInfo fetch(String policyId, String bearerToken);
}
