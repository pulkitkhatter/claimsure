package com.claimsure.identity.service;

import com.claimsure.common.error.ConflictException;
import com.claimsure.common.error.ResourceNotFoundException;
import com.claimsure.common.security.Roles;
import com.claimsure.identity.domain.User;
import com.claimsure.identity.domain.UserRepository;
import com.claimsure.identity.web.Dtos.AuthResponse;
import com.claimsure.identity.web.Dtos.LoginRequest;
import com.claimsure.identity.web.Dtos.RegisterRequest;
import com.claimsure.identity.web.Dtos.UserView;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    static final int MAX_FAILED_ATTEMPTS = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder encoder, TokenService tokens, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.tokens = tokens;
        this.clock = clock;
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    /** Public sign-up can only ever create CUSTOMER accounts - staff accounts are provisioned by admins. */
    public UserView register(RegisterRequest req) {
        String email = normalise(req.email());
        if (users.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        User saved = users.save(new User(email, encoder.encode(req.password()), req.fullName().trim(),
                Set.of(Roles.CUSTOMER)));
        return UserView.of(saved);
    }

    public AuthResponse login(LoginRequest req) {
        var found = users.findByEmail(normalise(req.email()));
        if (found.isEmpty()) {
            encoder.matches(req.password(), dummyHash); // equalise timing so unknown emails are not detectable
            throw new BadCredentialsException("Invalid email or password");
        }
        User user = found.get();
        if (user.isLocked(clock.instant())) {
            throw new LockedException("Account temporarily locked after repeated failures");
        }
        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            registerFailure(user);
            throw new BadCredentialsException("Invalid email or password");
        }
        if (user.getFailedAttempts() > 0 || user.getLockedUntil() != null) {
            user.setFailedAttempts(0);
            user.setLockedUntil(null);
            users.save(user);
        }
        return new AuthResponse(tokens.issue(user), "Bearer", tokens.ttlSeconds(), UserView.of(user));
    }

    public UserView me(String userId) {
        return users.findById(userId).map(UserView::of)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private void registerFailure(User user) {
        int attempts = user.getFailedAttempts() + 1;
        user.setFailedAttempts(attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(clock.instant().plus(LOCK_DURATION));
            user.setFailedAttempts(0);
        }
        users.save(user);
    }

    private static String normalise(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
