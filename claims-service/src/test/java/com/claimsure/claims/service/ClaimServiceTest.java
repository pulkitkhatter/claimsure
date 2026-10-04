package com.claimsure.claims.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.claimsure.claims.client.PolicyClient;
import com.claimsure.claims.client.PolicyInfo;
import com.claimsure.claims.domain.Claim;
import com.claimsure.claims.domain.ClaimRepository;
import com.claimsure.claims.domain.ClaimStatus;
import com.claimsure.claims.fraud.FraudScorer;
import com.claimsure.common.error.BusinessRuleException;
import com.claimsure.common.error.ResourceNotFoundException;
import com.claimsure.common.event.DomainEvent;
import com.claimsure.common.event.EventPublisher;
import com.claimsure.common.security.CurrentUser;
import com.claimsure.common.security.Roles;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ClaimServiceTest {
    private final ClaimRepository repo = mock(ClaimRepository.class);
    private final PolicyClient policies = mock(PolicyClient.class);
    private final EventPublisher events = mock(EventPublisher.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC);
    private final CurrentUser customer = new CurrentUser("u1", "c@x.co", "Cat", Set.of(Roles.CUSTOMER));
    private final CurrentUser stranger = new CurrentUser("u2", "o@x.co", "Oth", Set.of(Roles.CUSTOMER));
    private final CurrentUser adjuster = new CurrentUser("a1", "a@x.co", "Adj", Set.of(Roles.ADJUSTER));
    private ClaimService service;

    private static final String LONG = "Water pipe burst in the kitchen and flooded the ground floor, plumber invoice attached";

    @BeforeEach
    void setUp() {
        service = new ClaimService(repo, policies, new FraudScorer(), events, clock);
        when(repo.save(any(Claim.class))).thenAnswer(i -> {
            Claim c = i.getArgument(0);
            if (c.getId() == null) {
                c.setId("c1");
            }
            return c;
        });
        when(repo.countByPolicyIdAndCreatedAtAfter(eq("p1"), any())).thenReturn(0L);
        when(policies.fetch("p1", "tok")).thenReturn(policy("ACTIVE"));
    }

    private PolicyInfo policy(String status) {
        return new PolicyInfo("p1", "POL-1", "u1", "HOME", new BigDecimal("100000"), status,
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1));
    }

    private ClaimService.SubmitCommand cmd(String amount, LocalDate incident) {
        return new ClaimService.SubmitCommand("p1", incident, new BigDecimal(amount), LONG);
    }

    private Claim stored(ClaimStatus status) {
        Claim c = new Claim();
        c.setId("c1");
        c.setCustomerId("u1");
        c.setCustomerEmail("c@x.co");
        c.setClaimNumber("CLM-1");
        c.setStatus(status);
        c.setAmountClaimed(new BigDecimal("5000"));
        when(repo.findById("c1")).thenReturn(Optional.of(c));
        return c;
    }

    @Test
    void submitCreatesClaimWithFraudScoreAndPublishes() {
        Claim c = service.submit(cmd("5000", LocalDate.of(2026, 5, 20)), customer, "tok");

        assertThat(c.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(c.getClaimNumber()).startsWith("CLM-2026-");
        assertThat(c.getPolicyNumber()).isEqualTo("POL-1");
        assertThat(c.getFraudScore()).isZero();
        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(events).publish(ev.capture());
        assertThat(ev.getValue().type()).isEqualTo("CLAIM_SUBMITTED");
    }

    @Test
    void submitRejectsInactivePolicy() {
        when(policies.fetch("p1", "tok")).thenReturn(policy("CANCELLED"));
        assertThatThrownBy(() -> service.submit(cmd("5000", LocalDate.of(2026, 5, 20)), customer, "tok"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("active");
        verifyNoInteractions(events);
    }

    @Test
    void submitRejectsIncidentOutsidePolicyPeriodOrInFuture() {
        assertThatThrownBy(() -> service.submit(cmd("5000", LocalDate.of(2025, 12, 31)), customer, "tok"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.submit(cmd("5000", LocalDate.of(2026, 6, 2)), customer, "tok"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void submitRejectsAmountAboveCoverage() {
        assertThatThrownBy(() -> service.submit(cmd("100001", LocalDate.of(2026, 5, 20)), customer, "tok"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("coverage");
    }

    @Test
    void submitRejectsClaimOnSomeoneElsesPolicy() {
        assertThatThrownBy(() -> service.submit(cmd("5000", LocalDate.of(2026, 5, 20)), stranger, "tok"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void repeatClaimsRaiseFraudScore() {
        when(repo.countByPolicyIdAndCreatedAtAfter(eq("p1"), any())).thenReturn(2L);
        Claim c = service.submit(cmd("5000", LocalDate.of(2026, 5, 20)), customer, "tok");
        assertThat(c.getFraudScore()).isEqualTo(35);
        assertThat(c.getFraudFlags()).hasSize(1);
    }

    @Test
    void adjusterWorkflowRecordsHistoryAndEvents() {
        Claim c = stored(ClaimStatus.SUBMITTED);
        service.startReview("c1", adjuster);
        assertThat(c.getStatus()).isEqualTo(ClaimStatus.UNDER_REVIEW);
        service.approve("c1", new BigDecimal("4500"), "Covered", adjuster);
        assertThat(c.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(c.getApprovedAmount()).isEqualByComparingTo("4500");
        assertThat(c.getHistory()).hasSize(2);
        assertThat(c.getHistory().get(1).by()).isEqualTo("a@x.co");
        verify(events, org.mockito.Mockito.times(2)).publish(any(DomainEvent.class));
    }

    @Test
    void approvedAmountCannotExceedClaimedAmount() {
        stored(ClaimStatus.UNDER_REVIEW);
        assertThatThrownBy(() -> service.approve("c1", new BigDecimal("5001"), "x", adjuster))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void illegalTransitionsAreRefused() {
        stored(ClaimStatus.SUBMITTED);
        assertThatThrownBy(() -> service.approve("c1", BigDecimal.ONE, "x", adjuster))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.pay("c1", adjuster)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void customerCanWithdrawOwnClaimOnly() {
        Claim c = stored(ClaimStatus.SUBMITTED);
        assertThatThrownBy(() -> service.withdraw("c1", stranger)).isInstanceOf(ResourceNotFoundException.class);
        service.withdraw("c1", customer);
        assertThat(c.getStatus()).isEqualTo(ClaimStatus.WITHDRAWN);
    }

    @Test
    void customersCannotReadOthersClaimsButStaffCan() {
        stored(ClaimStatus.SUBMITTED);
        assertThatThrownBy(() -> service.get("c1", stranger)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(service.get("c1", adjuster)).isNotNull();
        assertThat(service.get("c1", customer)).isNotNull();
    }
}
