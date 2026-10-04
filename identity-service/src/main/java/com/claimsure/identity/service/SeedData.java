package com.claimsure.identity.service;

import com.claimsure.common.security.Roles;
import com.claimsure.identity.domain.User;
import com.claimsure.identity.domain.UserRepository;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Dev/demo accounts (one per role). Disable with claimsure.seed.enabled=false in real environments. */
@Component
public class SeedData implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(SeedData.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final boolean enabled;
    private final String password;

    public SeedData(UserRepository users, PasswordEncoder encoder,
                    @Value("${claimsure.seed.enabled:true}") boolean enabled,
                    @Value("${claimsure.seed.password:}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.enabled = enabled;
        this.password = password;
    }

    @Override
    public void run(String... args) {
        if (!enabled || password.isBlank()) {
            return;
        }
        seed("customer@claimsure.test", "Casey Customer", Set.of(Roles.CUSTOMER));
        seed("adjuster@claimsure.test", "Alex Adjuster", Set.of(Roles.ADJUSTER));
        seed("admin@claimsure.test", "Avery Admin", Set.of(Roles.ADMIN, Roles.ADJUSTER));
    }

    private void seed(String email, String name, Set<String> roles) {
        if (!users.existsByEmail(email)) {
            users.save(new User(email, encoder.encode(password), name, roles));
            log.info("Seeded demo user {}", email);
        }
    }
}
