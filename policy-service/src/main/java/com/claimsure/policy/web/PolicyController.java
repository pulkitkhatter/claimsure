package com.claimsure.policy.web;

import com.claimsure.common.security.CurrentUser;
import com.claimsure.common.web.PageResponse;
import com.claimsure.policy.domain.Policy;
import com.claimsure.policy.service.PolicyService;
import com.claimsure.policy.web.PolicyDtos.PolicyView;
import com.claimsure.policy.web.PolicyDtos.PurchaseRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/policies")
@Tag(name = "Policies")
@SecurityRequirement(name = "bearer")
public class PolicyController {
    private final PolicyService policies;

    public PolicyController(PolicyService policies) {
        this.policies = policies;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Buy a policy from a quote")
    public PolicyView purchase(@Valid @RequestBody PurchaseRequest req, @AuthenticationPrincipal Jwt jwt) {
        return PolicyView.of(policies.purchase(req.quote(), req.startDate(), CurrentUser.from(jwt)));
    }

    @GetMapping
    @Operation(summary = "List my policies (staff may pass all=true)")
    public PageResponse<PolicyView> list(@RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size,
                                         @RequestParam(defaultValue = "false") boolean all,
                                         @AuthenticationPrincipal Jwt jwt) {
        int s = Math.max(1, Math.min(size, 100));
        Page<Policy> p = policies.list(CurrentUser.from(jwt), all,
                PageRequest.of(Math.max(page, 0), s, Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.of(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), PolicyView::of);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one policy (owner or staff)")
    public PolicyView get(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return PolicyView.of(policies.get(id, CurrentUser.from(jwt)));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel an active policy")
    public PolicyView cancel(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return PolicyView.of(policies.cancel(id, CurrentUser.from(jwt)));
    }
}
