package com.claimsure.policy.web;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.claimsure.common.error.ErrorAutoConfiguration;
import com.claimsure.common.security.SecurityAutoConfiguration;
import com.claimsure.policy.pricing.PremiumCalculator;
import com.claimsure.policy.service.QuoteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(QuoteController.class)
@AutoConfigureRestDocs(outputDir = "target/generated-snippets")
@ImportAutoConfiguration({SecurityAutoConfiguration.class, ErrorAutoConfiguration.class})
@Import({QuoteService.class, PremiumCalculator.class})
@TestPropertySource(properties = {
        "claimsure.security.secret=unit-test-secret-0123456789-abcdefghij",
        "claimsure.security.public-paths=/api/v1/products"})
class QuoteControllerTest {
    private static final String BODY = """
            {"type":"AUTO","coverageAmount":50000,"customerAge":40,"claimsInLast5Years":0,
             "smoker":false,"assetAgeYears":5,"deductible":"STANDARD"}""";
    @Autowired
    MockMvc mvc;

    @Test
    void v2ReturnsBreakdownAndIsDocumented() throws Exception {
        mvc.perform(post("/api/v2/quotes").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.annualPremium").value(900.00))
                .andExpect(jsonPath("$.factors").isArray())
                .andDo(document("quote-v2",
                        requestFields(
                                fieldWithPath("type").description("AUTO, HOME, HEALTH or LIFE"),
                                fieldWithPath("coverageAmount").description("Sum insured, 1,000 - 5,000,000"),
                                fieldWithPath("customerAge").description("18-99"),
                                fieldWithPath("claimsInLast5Years").description("0-20"),
                                fieldWithPath("smoker").description("Applies to HEALTH and LIFE"),
                                fieldWithPath("assetAgeYears").description("Vehicle or building age"),
                                fieldWithPath("deductible").description("LOW, STANDARD or HIGH")),
                        responseFields(
                                fieldWithPath("type").description("Echoed policy type"),
                                fieldWithPath("coverageAmount").description("Echoed coverage"),
                                fieldWithPath("basePremium").description("Premium before risk factors"),
                                fieldWithPath("annualPremium").description("Final yearly premium"),
                                fieldWithPath("monthlyPremium").description("Yearly premium / 12"),
                                fieldWithPath("factors").description("Applied risk multipliers"))
                                .andWithPrefix("factors[].",
                                        fieldWithPath("name").type(org.springframework.restdocs.payload.JsonFieldType.STRING).optional().description("Factor name"),
                                        fieldWithPath("multiplier").type(org.springframework.restdocs.payload.JsonFieldType.NUMBER).optional().description("Multiplier applied"))));
    }

    @Test
    void v1IsMarkedDeprecated() throws Exception {
        mvc.perform(post("/api/v1/quotes").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(header().string("Deprecation", "true"))
                .andExpect(jsonPath("$.monthlyPremium").value(75.00));
    }

    @Test
    void invalidInputIs400() throws Exception {
        mvc.perform(post("/api/v2/quotes").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("\"customerAge\":40", "\"customerAge\":5")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.customerAge").exists());
    }

    @Test
    void quotesRequireAuthButCatalogIsPublic() throws Exception {
        mvc.perform(post("/api/v2/quotes").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/products")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4));
    }
}
