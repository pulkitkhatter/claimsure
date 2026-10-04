package com.claimsure.policy.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

import com.claimsure.common.error.ErrorAutoConfiguration;
import com.claimsure.common.security.SecurityAutoConfiguration;
import com.claimsure.policy.domain.Policy;
import com.claimsure.policy.service.PolicyService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PolicyController.class)
@ImportAutoConfiguration({SecurityAutoConfiguration.class, ErrorAutoConfiguration.class})
@TestPropertySource(properties = "claimsure.security.secret=unit-test-secret-0123456789-abcdefghij")
class PolicyControllerTest {
    private static final String BODY = """
            {"quote":{"type":"AUTO","coverageAmount":50000,"customerAge":40,"claimsInLast5Years":0,
             "smoker":false,"assetAgeYears":5,"deductible":"STANDARD"},"startDate":"2026-12-01"}""";
    @Autowired
    MockMvc mvc;
    @MockBean
    PolicyService service;

    @Test
    void adjustersCannotBuyPolicies() throws Exception {
        mvc.perform(post("/api/v1/policies").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADJUSTER")))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void customersCanBuy() throws Exception {
        Policy p = new Policy();
        p.setId("p1");
        p.setPolicyNumber("POL-1");
        p.setType(com.claimsure.policy.pricing.PolicyType.AUTO);
        p.setStartDate(LocalDate.of(2026, 12, 1));
        when(service.purchase(any(), any(), any())).thenReturn(p);
        mvc.perform(post("/api/v1/policies").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.policyNumber").value("POL-1"));
    }
}
