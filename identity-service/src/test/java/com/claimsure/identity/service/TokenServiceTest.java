package com.claimsure.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.claimsure.common.security.JwtProperties;
import com.claimsure.common.security.Roles;
import com.claimsure.identity.domain.User;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class TokenServiceTest {
    private static final String SECRET = "unit-test-secret-0123456789-abcdefghij";

    @Test
    void issuedTokenCanBeVerifiedAndCarriesRolesAndIdentity() {
        var key = new SecretKeySpec(SECRET.getBytes(), "HmacSHA256");
        var props = new JwtProperties(SECRET, "claimsure-identity", Duration.ofMinutes(30), List.of());
        var service = new TokenService(new NimbusJwtEncoder(new ImmutableSecret<>(key)), props);
        User user = new User("ann@example.com", "hash", "Ann", Set.of(Roles.ADJUSTER));
        user.setId("u-1");

        var jwt = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build()
                .decode(service.issue(user));

        assertThat(jwt.getSubject()).isEqualTo("u-1");
        assertThat(jwt.getClaimAsString("email")).isEqualTo("ann@example.com");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly(Roles.ADJUSTER);
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("claimsure-identity");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(30));
        assertThat(service.ttlSeconds()).isEqualTo(1800);
    }

    @Test
    void shortSecretsAreRejectedAtStartup() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> new JwtProperties("too-short", null, null, null));
    }
}
