package com.claimsure.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.claimsure.common.security.CurrentUser;
import com.claimsure.common.security.Roles;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class CurrentUserTest {

    private static Jwt jwt(List<String> roles) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "HS256").subject("u1")
                .claim("email", "a@b.c").claim("name", "Ann").issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        if (roles != null) {
            b.claim("roles", roles);
        }
        return b.build();
    }

    @Test
    void extractsIdentityAndRoles() {
        CurrentUser u = CurrentUser.from(jwt(List.of(Roles.ADJUSTER)));
        assertThat(u.id()).isEqualTo("u1");
        assertThat(u.email()).isEqualTo("a@b.c");
        assertThat(u.isStaff()).isTrue();
        assertThat(u.hasRole(Roles.ADMIN)).isFalse();
    }

    @Test
    void missingRolesClaimMeansNoRoles() {
        CurrentUser u = CurrentUser.from(jwt(null));
        assertThat(u.roles()).isEmpty();
        assertThat(u.isStaff()).isFalse();
    }
}
