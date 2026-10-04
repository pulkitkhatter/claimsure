package com.claimsure.claims.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PolicyInfo(String id, String policyNumber, String customerId, String type, BigDecimal coverageAmount,
                         String status, LocalDate startDate, LocalDate endDate) {
}
