package com.claimsure.common.security;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Shared by every service: the HMAC secret must be identical everywhere (identity-service signs, the rest verify).
 * Production deployments should switch to an asymmetric key pair / JWKS endpoint of a real IdP.
 */
@ConfigurationProperties(prefix = "claimsure.security")
public record JwtProperties(String secret, String issuer, Duration tokenTtl, List<String> publicPaths) {

    public JwtProperties {
        issuer = issuer == null ? "claimsure-identity" : issuer;
        tokenTtl = tokenTtl == null ? Duration.ofHours(1) : tokenTtl;
        publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
        if (secret == null || secret.getBytes().length < 32) {
            throw new IllegalStateException("claimsure.security.secret must be set and at least 32 bytes long");
        }
    }
}
