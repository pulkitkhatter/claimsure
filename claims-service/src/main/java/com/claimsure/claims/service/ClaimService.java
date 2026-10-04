package com.claimsure.claims.service;

import com.claimsure.claims.client.PolicyClient;
import com.claimsure.claims.client.PolicyInfo;
import com.claimsure.claims.domain.Claim;
import com.claimsure.claims.domain.Claim.StatusChange;
import com.claimsure.claims.domain.ClaimRepository;
import com.claimsure.claims.domain.ClaimStatus;
import com.claimsure.claims.fraud.FraudAssessment;
import com.claimsure.claims.fraud.FraudScorer;
import com.claimsure.common.error.BusinessRuleException;
import com.claimsure.common.error.ResourceNotFoundException;
import com.claimsure.common.event.DomainEvent;
import com.claimsure.common.event.EventPublisher;
import com.claimsure.common.security.CurrentUser;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class ClaimService {
    public record SubmitCommand(String policyId, LocalDate incidentDate, BigDecimal amountClaimed, String description) {
    }

    private final ClaimRepository repo;
    private final PolicyClient policies;
    private final FraudScorer fraud;
    private final EventPublisher events;
    private final Clock clock;

    public ClaimService(ClaimRepository repo, PolicyClient policies, FraudScorer fraud, EventPublisher events,
                        Clock clock) {
        this.repo = repo;
        this.policies = policies;
        this.fraud = fraud;
        this.events = events;
        this.clock = clock;
    }

    public Claim submit(SubmitCommand cmd, CurrentUser user, String bearerToken) {
        PolicyInfo policy = policies.fetch(cmd.policyId(), bearerToken);
        if (!user.id().equals(policy.customerId())) {
            throw new ResourceNotFoundException("Policy", cmd.policyId());
        }
        if (!"ACTIVE".equals(policy.status())) {
            throw new BusinessRuleException("Claims can only be filed against an active policy");
        }
        LocalDate today = LocalDate.now(clock);
        if (cmd.incidentDate().isAfter(today)) {
            throw new BusinessRuleException("Incident date cannot be in the future");
        }
        if (cmd.incidentDate().isBefore(policy.startDate()) || cmd.incidentDate().isAfter(policy.endDate())) {
            throw new BusinessRuleException("Incident date is outside the policy period");
        }
        if (cmd.amountClaimed().compareTo(policy.coverageAmount()) > 0) {
            throw new BusinessRuleException("Claimed amount exceeds the policy coverage");
        }

        Instant ninetyDaysAgo = today.minusDays(90).atStartOfDay().toInstant(ZoneOffset.UTC);
        FraudAssessment fa = fraud.assess(cmd.amountClaimed(), policy.coverageAmount(), cmd.incidentDate(),
                policy.startDate(), cmd.description(), repo.countByPolicyIdAndCreatedAtAfter(policy.id(), ninetyDaysAgo));

        Claim c = new Claim();
        c.setClaimNumber("CLM-" + today.getYear() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        c.setPolicyId(policy.id());
        c.setPolicyNumber(policy.policyNumber());
        c.setCustomerId(user.id());
        c.setCustomerEmail(user.email());
        c.setIncidentDate(cmd.incidentDate());
        c.setDescription(cmd.description().trim());
        c.setAmountClaimed(cmd.amountClaimed());
        c.setFraudScore(fa.score());
        c.setFraudFlags(fa.flags());
        Claim saved = repo.save(c);
        publish("CLAIM_SUBMITTED", saved, "We received your claim " + saved.getClaimNumber());
        return saved;
    }

    public Page<Claim> list(CurrentUser user, boolean all, ClaimStatus status, Pageable pageable) {
        if (all && user.isStaff()) {
            return status == null ? repo.findAll(pageable) : repo.findByStatus(status, pageable);
        }
        return repo.findByCustomerId(user.id(), pageable);
    }

    public Claim get(String id, CurrentUser user) {
        Claim c = repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Claim", id));
        if (!user.isStaff() && !c.getCustomerId().equals(user.id())) {
            throw new ResourceNotFoundException("Claim", id);
        }
        return c;
    }

    public Claim startReview(String id, CurrentUser adjuster) {
        Claim c = get(id, adjuster);
        move(c, ClaimStatus.UNDER_REVIEW, adjuster, "Review started");
        return finish(c, "CLAIM_UNDER_REVIEW", "An adjuster has started reviewing your claim");
    }

    public Claim approve(String id, BigDecimal approvedAmount, String note, CurrentUser adjuster) {
        Claim c = get(id, adjuster);
        if (approvedAmount.compareTo(c.getAmountClaimed()) > 0) {
            throw new BusinessRuleException("Approved amount cannot exceed the claimed amount");
        }
        move(c, ClaimStatus.APPROVED, adjuster, note);
        c.setApprovedAmount(approvedAmount);
        return finish(c, "CLAIM_APPROVED", "Your claim was approved for " + approvedAmount);
    }

    public Claim reject(String id, String reason, CurrentUser adjuster) {
        Claim c = get(id, adjuster);
        move(c, ClaimStatus.REJECTED, adjuster, reason);
        return finish(c, "CLAIM_REJECTED", "Your claim was declined: " + reason);
    }

    public Claim pay(String id, CurrentUser finance) {
        Claim c = get(id, finance);
        move(c, ClaimStatus.PAID, finance, "Payment issued");
        return finish(c, "CLAIM_PAID", "Payment of " + c.getApprovedAmount() + " has been issued");
    }

    public Claim withdraw(String id, CurrentUser user) {
        Claim c = get(id, user);
        if (!c.getCustomerId().equals(user.id())) {
            throw new ResourceNotFoundException("Claim", id);
        }
        move(c, ClaimStatus.WITHDRAWN, user, "Withdrawn by customer");
        return finish(c, "CLAIM_WITHDRAWN", "You withdrew claim " + c.getClaimNumber());
    }

    private void move(Claim c, ClaimStatus target, CurrentUser by, String note) {
        if (!c.getStatus().canMoveTo(target)) {
            throw new BusinessRuleException("A claim that is " + c.getStatus() + " cannot move to " + target);
        }
        c.getHistory().add(new StatusChange(c.getStatus(), target, by.email(), note, clock.instant()));
        c.setStatus(target);
    }

    private Claim finish(Claim c, String eventType, String detail) {
        Claim saved = repo.save(c);
        publish(eventType, saved, detail);
        return saved;
    }

    private void publish(String type, Claim c, String detail) {
        events.publish(DomainEvent.of(type, c.getId(), c.getClaimNumber(), c.getCustomerId(), c.getCustomerEmail(),
                c.getStatus().name(), c.getApprovedAmount() != null ? c.getApprovedAmount() : c.getAmountClaimed(),
                detail));
    }
}
