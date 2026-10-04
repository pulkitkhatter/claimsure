package com.claimsure.policy.service;

import com.claimsure.common.error.BusinessRuleException;
import com.claimsure.common.error.ResourceNotFoundException;
import com.claimsure.common.event.DomainEvent;
import com.claimsure.common.event.EventPublisher;
import com.claimsure.common.security.CurrentUser;
import com.claimsure.policy.domain.Policy;
import com.claimsure.policy.domain.PolicyRepository;
import com.claimsure.policy.domain.PolicyStatus;
import com.claimsure.policy.pricing.PremiumQuote;
import com.claimsure.policy.pricing.QuoteRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class PolicyService {
    static final int MAX_DAYS_AHEAD = 60;

    private final PolicyRepository repo;
    private final QuoteService quotes;
    private final EventPublisher events;
    private final Clock clock;

    public PolicyService(PolicyRepository repo, QuoteService quotes, EventPublisher events, Clock clock) {
        this.repo = repo;
        this.quotes = quotes;
        this.events = events;
        this.clock = clock;
    }

    /** The premium is always recomputed server-side; the client never supplies a price. */
    public Policy purchase(QuoteRequest req, LocalDate startDate, CurrentUser user) {
        LocalDate today = LocalDate.now(clock);
        if (startDate.isBefore(today)) {
            throw new BusinessRuleException("Start date cannot be in the past");
        }
        if (startDate.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            throw new BusinessRuleException("Start date cannot be more than " + MAX_DAYS_AHEAD + " days ahead");
        }
        PremiumQuote quote = quotes.quote(req);
        Policy p = new Policy();
        p.setPolicyNumber("POL-" + today.getYear() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        p.setCustomerId(user.id());
        p.setCustomerEmail(user.email());
        p.setType(req.type());
        p.setCoverageAmount(req.coverageAmount());
        p.setDeductible(req.deductible());
        p.setAnnualPremium(quote.annualPremium());
        p.setStartDate(startDate);
        p.setEndDate(startDate.plusYears(1));
        Policy saved = repo.save(p);
        publish("POLICY_ISSUED", saved, "Your " + saved.getType() + " policy starts on " + saved.getStartDate());
        return saved;
    }

    public Page<Policy> list(CurrentUser user, boolean all, Pageable pageable) {
        return all && user.isStaff() ? repo.findAll(pageable) : repo.findByCustomerId(user.id(), pageable);
    }

    /** Non-owners get 404 rather than 403 so policy ids cannot be probed. */
    public Policy get(String id, CurrentUser user) {
        Policy p = repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Policy", id));
        if (!user.isStaff() && !p.getCustomerId().equals(user.id())) {
            throw new ResourceNotFoundException("Policy", id);
        }
        return p;
    }

    public Policy cancel(String id, CurrentUser user) {
        Policy p = get(id, user);
        if (p.getStatus() != PolicyStatus.ACTIVE) {
            throw new BusinessRuleException("Only active policies can be cancelled");
        }
        p.setStatus(PolicyStatus.CANCELLED);
        LocalDate today = LocalDate.now(clock);
        p.setEndDate(today.isBefore(p.getStartDate()) ? p.getStartDate() : today);
        Policy saved = repo.save(p);
        publish("POLICY_CANCELLED", saved, "Your policy has been cancelled");
        return saved;
    }

    private void publish(String type, Policy p, String detail) {
        events.publish(DomainEvent.of(type, p.getId(), p.getPolicyNumber(), p.getCustomerId(), p.getCustomerEmail(),
                p.getStatus().name(), p.getAnnualPremium(), detail));
    }
}
