package com.claimsure.policy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.claimsure.common.error.BusinessRuleException;
import com.claimsure.common.error.ResourceNotFoundException;
import com.claimsure.common.event.DomainEvent;
import com.claimsure.common.event.EventPublisher;
import com.claimsure.common.security.CurrentUser;
import com.claimsure.common.security.Roles;
import com.claimsure.policy.domain.Policy;
import com.claimsure.policy.domain.PolicyRepository;
import com.claimsure.policy.domain.PolicyStatus;
import com.claimsure.policy.pricing.Deductible;
import com.claimsure.policy.pricing.PremiumCalculator;
import com.claimsure.policy.pricing.PolicyType;
import com.claimsure.policy.pricing.QuoteRequest;
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

class PolicyServiceTest {
    private final PolicyRepository repo = mock(PolicyRepository.class);
    private final EventPublisher events = mock(EventPublisher.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC);
    private final CurrentUser owner = new CurrentUser("u1", "o@x.co", "Owner", Set.of(Roles.CUSTOMER));
    private final CurrentUser other = new CurrentUser("u2", "t@x.co", "Other", Set.of(Roles.CUSTOMER));
    private final CurrentUser staff = new CurrentUser("s1", "s@x.co", "Staff", Set.of(Roles.ADJUSTER));
    private final QuoteRequest quote = new QuoteRequest(PolicyType.AUTO, new BigDecimal("50000"), 40, 0, false, 5,
            Deductible.STANDARD);
    private PolicyService service;

    @BeforeEach
    void setUp() {
        service = new PolicyService(repo, new QuoteService(new PremiumCalculator()), events, clock);
        when(repo.save(any(Policy.class))).thenAnswer(i -> {
            Policy p = i.getArgument(0);
            if (p.getId() == null) {
                p.setId("p1");
            }
            return p;
        });
    }

    @Test
    void purchaseComputesPremiumServerSideAndPublishesEvent() {
        Policy p = service.purchase(quote, LocalDate.of(2026, 6, 10), owner);

        assertThat(p.getAnnualPremium()).isEqualByComparingTo("900.00");
        assertThat(p.getEndDate()).isEqualTo(LocalDate.of(2027, 6, 10));
        assertThat(p.getPolicyNumber()).startsWith("POL-2026-");
        assertThat(p.getCustomerId()).isEqualTo("u1");
        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(events).publish(ev.capture());
        assertThat(ev.getValue().type()).isEqualTo("POLICY_ISSUED");
        assertThat(ev.getValue().customerEmail()).isEqualTo("o@x.co");
    }

    @Test
    void startDateMustBeWithinWindow() {
        assertThatThrownBy(() -> service.purchase(quote, LocalDate.of(2026, 5, 31), owner))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.purchase(quote, LocalDate.of(2026, 9, 1), owner))
                .isInstanceOf(BusinessRuleException.class);
    }

    private Policy stored(PolicyStatus status) {
        Policy p = new Policy();
        p.setId("p1");
        p.setCustomerId("u1");
        p.setStatus(status);
        p.setStartDate(LocalDate.of(2026, 1, 1));
        p.setEndDate(LocalDate.of(2027, 1, 1));
        p.setAnnualPremium(BigDecimal.TEN);
        return p;
    }

    @Test
    void nonOwnerSeesNotFoundButStaffCanRead() {
        when(repo.findById("p1")).thenReturn(Optional.of(stored(PolicyStatus.ACTIVE)));
        assertThatThrownBy(() -> service.get("p1", other)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(service.get("p1", staff)).isNotNull();
        assertThat(service.get("p1", owner)).isNotNull();
    }

    @Test
    void cancelEndsPolicyTodayAndPublishes() {
        when(repo.findById("p1")).thenReturn(Optional.of(stored(PolicyStatus.ACTIVE)));
        Policy p = service.cancel("p1", owner);
        assertThat(p.getStatus()).isEqualTo(PolicyStatus.CANCELLED);
        assertThat(p.getEndDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        verify(events).publish(any(DomainEvent.class));
    }

    @Test
    void cannotCancelTwice() {
        when(repo.findById("p1")).thenReturn(Optional.of(stored(PolicyStatus.CANCELLED)));
        assertThatThrownBy(() -> service.cancel("p1", owner)).isInstanceOf(BusinessRuleException.class);
    }
}
