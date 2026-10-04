package com.claimsure.claims.web;

import com.claimsure.claims.domain.Claim;
import com.claimsure.claims.domain.ClaimStatus;
import com.claimsure.claims.service.ClaimService;
import com.claimsure.claims.service.ClaimService.SubmitCommand;
import com.claimsure.claims.web.ClaimDtos.ApproveRequest;
import com.claimsure.claims.web.ClaimDtos.ClaimView;
import com.claimsure.claims.web.ClaimDtos.RejectRequest;
import com.claimsure.claims.web.ClaimDtos.SubmitRequest;
import com.claimsure.common.security.CurrentUser;
import com.claimsure.common.web.PageResponse;
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
@RequestMapping("/api/v1/claims")
@Tag(name = "Claims")
@SecurityRequirement(name = "bearer")
public class ClaimController {
    private final ClaimService claims;

    public ClaimController(ClaimService claims) {
        this.claims = claims;
    }

    private static ClaimView view(Claim c, CurrentUser u) {
        return ClaimView.of(c, u.isStaff());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "File a claim against one of my active policies")
    public ClaimView submit(@Valid @RequestBody SubmitRequest r, @AuthenticationPrincipal Jwt jwt) {
        CurrentUser u = CurrentUser.from(jwt);
        return view(claims.submit(new SubmitCommand(r.policyId(), r.incidentDate(), r.amountClaimed(),
                r.description()), u, jwt.getTokenValue()), u);
    }

    @GetMapping
    @Operation(summary = "List my claims; staff may pass all=true and a status filter")
    public PageResponse<ClaimView> list(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size,
                                        @RequestParam(defaultValue = "false") boolean all,
                                        @RequestParam(required = false) ClaimStatus status,
                                        @AuthenticationPrincipal Jwt jwt) {
        CurrentUser u = CurrentUser.from(jwt);
        Page<Claim> p = claims.list(u, all, status, PageRequest.of(Math.max(page, 0),
                Math.max(1, Math.min(size, 100)), Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.of(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), c -> view(c, u));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Claim details with status history")
    public ClaimView get(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        CurrentUser u = CurrentUser.from(jwt);
        return view(claims.get(id, u), u);
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasRole('ADJUSTER')")
    @Operation(summary = "Adjuster picks up a submitted claim")
    public ClaimView review(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        CurrentUser u = CurrentUser.from(jwt);
        return view(claims.startReview(id, u), u);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADJUSTER')")
    @Operation(summary = "Approve a claim for an amount up to the claimed value")
    public ClaimView approve(@PathVariable String id, @Valid @RequestBody ApproveRequest r,
                             @AuthenticationPrincipal Jwt jwt) {
        CurrentUser u = CurrentUser.from(jwt);
        return view(claims.approve(id, r.approvedAmount(), r.note(), u), u);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADJUSTER')")
    @Operation(summary = "Reject a claim with a reason")
    public ClaimView reject(@PathVariable String id, @Valid @RequestBody RejectRequest r,
                            @AuthenticationPrincipal Jwt jwt) {
        CurrentUser u = CurrentUser.from(jwt);
        return view(claims.reject(id, r.reason(), u), u);
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Issue payment for an approved claim (admin only)")
    public ClaimView pay(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        CurrentUser u = CurrentUser.from(jwt);
        return view(claims.pay(id, u), u);
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Customer withdraws an open claim")
    public ClaimView withdraw(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        CurrentUser u = CurrentUser.from(jwt);
        return view(claims.withdraw(id, u), u);
    }
}
