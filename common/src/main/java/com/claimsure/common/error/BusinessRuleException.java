package com.claimsure.common.error;

/** A request that is well-formed but violates a domain rule (e.g. illegal claim status transition). Maps to HTTP 422. */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
