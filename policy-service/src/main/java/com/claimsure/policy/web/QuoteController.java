package com.claimsure.policy.web;

import com.claimsure.policy.pricing.PremiumQuote;
import com.claimsure.policy.pricing.QuoteRequest;
import com.claimsure.policy.service.QuoteService;
import com.claimsure.policy.web.PolicyDtos.Product;
import com.claimsure.policy.pricing.PolicyType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Quotes")
public class QuoteController {
    private final QuoteService quotes;

    public QuoteController(QuoteService quotes) {
        this.quotes = quotes;
    }

    @PostMapping("/api/v1/quotes")
    @Operation(summary = "Premium only (deprecated, use v2)", deprecated = true)
    public PolicyDtos.QuoteV1 quoteV1(@Valid @RequestBody QuoteRequest req, HttpServletResponse res) {
        res.setHeader("Deprecation", "true");
        res.setHeader("Link", "</api/v2/quotes>; rel=\"successor-version\"");
        PremiumQuote q = quotes.quote(req);
        return new PolicyDtos.QuoteV1(q.annualPremium(), q.monthlyPremium());
    }

    @PostMapping("/api/v2/quotes")
    @Operation(summary = "Premium with full factor breakdown")
    public PremiumQuote quoteV2(@Valid @RequestBody QuoteRequest req) {
        return quotes.quote(req);
    }

    @GetMapping("/api/v1/products")
    @Cacheable("catalog")
    @Operation(summary = "Insurance product catalogue (cached)")
    public List<Product> products() {
        return List.of(
                new Product(PolicyType.AUTO, "Auto", "Collision, liability and theft cover for your vehicle", 5000, 250000),
                new Product(PolicyType.HOME, "Home", "Building and contents protection", 50000, 2000000),
                new Product(PolicyType.HEALTH, "Health", "Hospitalisation and treatment cover", 10000, 1000000),
                new Product(PolicyType.LIFE, "Life", "Financial security for your family", 25000, 5000000));
    }
}
