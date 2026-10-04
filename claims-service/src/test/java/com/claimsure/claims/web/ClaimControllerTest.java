package com.claimsure.claims.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.claimsure.claims.domain.Claim;
import com.claimsure.claims.service.ClaimService;
import com.claimsure.common.error.BusinessRuleException;
import com.claimsure.common.error.ErrorAutoConfiguration;
import com.claimsure.common.security.SecurityAutoConfiguration;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ClaimController.class)
@ImportAutoConfiguration({SecurityAutoConfiguration.class, ErrorAutoConfiguration.class})
@TestPropertySource(properties = "claimsure.security.secret=unit-test-secret-0123456789-abcdefghij")
class ClaimControllerTest {
    @Autowired
    MockMvc mvc;
    @MockBean
    ClaimService service;

    private static org.springframework.test.web.servlet.request.RequestPostProcessor as(String role, String sub) {
        return jwt().jwt(j -> j.subject(sub).claim("email", sub + "@x.co").claim("roles", java.util.List.of(role)))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private static Claim claim() {
        Claim c = new Claim();
        c.setId("c1");
        c.setClaimNumber("CLM-1");
        c.setAmountClaimed(new BigDecimal("500"));
        c.setFraudScore(60);
        c.getFraudFlags().add("flag");
        return c;
    }

    @Test
    void customerSubmitsClaimAndDoesNotSeeFraudData() throws Exception {
        when(service.submit(any(), any(), any())).thenReturn(claim());
        mvc.perform(post("/api/v1/claims").with(as("CUSTOMER", "u1")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policyId\":\"p1\",\"incidentDate\":\"2026-05-01\",\"amountClaimed\":500,"
                                + "\"description\":\"Kitchen flooded by burst pipe\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.claimNumber").value("CLM-1"))
                .andExpect(jsonPath("$.fraudScore").value(0))
                .andExpect(jsonPath("$.fraudFlags").isEmpty());
    }

    @Test
    void staffSeeFraudScoreAndReasons() throws Exception {
        when(service.get(any(), any())).thenReturn(claim());
        mvc.perform(get("/api/v1/claims/c1").with(as("ADJUSTER", "a1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fraudScore").value(60))
                .andExpect(jsonPath("$.fraudLevel").value("HIGH"))
                .andExpect(jsonPath("$.fraudFlags[0]").value("flag"));
    }

    @Test
    void shortDescriptionIsRejected() throws Exception {
        mvc.perform(post("/api/v1/claims").with(as("CUSTOMER", "u1")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policyId\":\"p1\",\"incidentDate\":\"2026-05-01\",\"amountClaimed\":500,"
                                + "\"description\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.description").exists());
    }

    @Test
    void customersCannotApprove() throws Exception {
        mvc.perform(post("/api/v1/claims/c1/approve").with(as("CUSTOMER", "u1"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"approvedAmount\":100}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void onlyAdminsCanPay() throws Exception {
        mvc.perform(post("/api/v1/claims/c1/pay").with(as("ADJUSTER", "a1"))).andExpect(status().isForbidden());
    }

    @Test
    void illegalTransitionMapsTo422() throws Exception {
        when(service.startReview(any(), any())).thenThrow(new BusinessRuleException("nope"));
        mvc.perform(post("/api/v1/claims/c1/review").with(as("ADJUSTER", "a1")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("nope"));
    }

    @Test
    void anonymousIs401() throws Exception {
        mvc.perform(get("/api/v1/claims")).andExpect(status().isUnauthorized());
    }
}
