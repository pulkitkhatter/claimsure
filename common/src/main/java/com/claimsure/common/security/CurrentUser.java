package com.claimsure.common.security;

import java.util.Set;
import org.springframework.security.oauth2.jwt.Jwt;

/** Immutable view of the caller, extracted from the verified JWT. */
public record CurrentUser(String id, String email, String name, Set<String> roles) {

    public static CurrentUser from(Jwt jwt) {
        var roles = jwt.getClaimAsStringList("roles");
        return new CurrentUser(jwt.getSubject(), jwt.getClaimAsString("email"), jwt.getClaimAsString("name"),
                roles == null ? Set.of() : Set.copyOf(roles));
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean isStaff() {
        return hasRole(Roles.ADJUSTER) || hasRole(Roles.ADMIN);
    }
}
