package com.claimsure.policy.service;

import com.claimsure.policy.pricing.PremiumCalculator;
import com.claimsure.policy.pricing.PremiumQuote;
import com.claimsure.policy.pricing.QuoteRequest;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/** Separate bean so the Spring cache proxy applies (no self-invocation). Rating is pure, hence safely cacheable. */
@Service
public class QuoteService {
    private final PremiumCalculator calculator;

    public QuoteService(PremiumCalculator calculator) {
        this.calculator = calculator;
    }

    @Cacheable(cacheNames = "quotes", key = "#req.cacheKey()")
    public PremiumQuote quote(QuoteRequest req) {
        return calculator.calculate(req);
    }
}
