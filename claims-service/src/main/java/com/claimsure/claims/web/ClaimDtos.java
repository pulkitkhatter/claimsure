package com.claimsure.claims.web;

import com.claimsure.claims.domain.Claim;
import com.claimsure.claims.domain.Claim.StatusChange;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ClaimDtos {
    private ClaimDtos() {
    }

    public record SubmitRequest(
            @NotBlank String policyId,
            @NotNull LocalDate incidentDate,
            @NotNull @DecimalMin("1") @DecimalMax("5000000") BigDecimal amountClaimed,
            @NotBlank @Size(min = 10, max = 2000) String description) {
    }

    public record ApproveRequest(@NotNull @DecimalMin("0.01") BigDecimal approvedAmount,
                                 @Size(max = 500) String note) {
    }

    public record RejectRequest(@NotBlank @Size(max = 500) String reason) {
    }

    public record ClaimView(String id, String claimNumber, String policyId, String policyNumber, String status,
                            LocalDate incidentDate, String description, BigDecimal amountClaimed,
                            BigDecimal approvedAmount, int fraudScore, String fraudLevel, List<String> fraudFlags,
                            List<StatusChange> history, Instant createdAt, String customerEmail) {
        /** Fraud signals are internal: customers never see the score or its reasons. */
        public static ClaimView of(Claim c, boolean staff) {
            String level = c.getFraudScore() >= 50 ? "HIGH" : c.getFraudScore() >= 25 ? "MEDIUM" : "LOW";
            return new ClaimView(c.getId(), c.getClaimNumber(), c.getPolicyId(), c.getPolicyNumber(),
                    c.getStatus().name(), c.getIncidentDate(), c.getDescription(), c.getAmountClaimed(),
                    c.getApprovedAmount(), staff ? c.getFraudScore() : 0, staff ? level : null,
                    staff ? c.getFraudFlags() : List.of(), c.getHistory(), c.getCreatedAt(),
                    staff ? c.getCustomerEmail() : null);
        }
    }
}
