package com.claimsure.claims.client;

import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

public class RestPolicyClient implements PolicyClient {
    private final RestClient client;

    public RestPolicyClient(RestClient.Builder loadBalancedBuilder, String baseUrl) {
        this.client = loadBalancedBuilder.baseUrl(baseUrl).build();
    }

    @Override
    public PolicyInfo fetch(String policyId, String bearerToken) {
        return client.get().uri("/api/v1/policies/{id}", policyId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .retrieve()
                .body(PolicyInfo.class);
    }
}
