package com.claimsure.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.claimsure.common.error.ConflictException;
import com.claimsure.common.security.Roles;
import com.claimsure.identity.domain.User;
import com.claimsure.identity.domain.UserRepository;
import com.claimsure.identity.web.Dtos.LoginRequest;
import com.claimsure.identity.web.Dtos.RegisterRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AuthServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final TokenService tokens = mock(TokenService.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);
    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, encoder, tokens, clock);
        when(users.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(tokens.issue(any())).thenReturn("jwt-token");
    }

    private User existing(String password) {
        return new User("ann@example.com", encoder.encode(password), "Ann", Set.of(Roles.CUSTOMER));
    }

    @Test
    void registerAlwaysCreatesCustomerAndNormalisesEmail() {
        var view = service.register(new RegisterRequest("  Ann@Example.COM ", "Str0ngPassword", " Ann "));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("ann@example.com");
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("Str0ngPassword");
        assertThat(view.roles()).containsExactly(Roles.CUSTOMER);
        assertThat(view.fullName()).isEqualTo("Ann");
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(users.existsByEmail("ann@example.com")).thenReturn(true);
        assertThatThrownBy(() -> service.register(new RegisterRequest("ann@example.com", "Str0ngPassword", "Ann")))
                .isInstanceOf(ConflictException.class);
        verify(users, never()).save(any());
    }

    @Test
    void loginReturnsTokenOnValidCredentials() {
        when(users.findByEmail("ann@example.com")).thenReturn(Optional.of(existing("Str0ngPassword")));
        var res = service.login(new LoginRequest("ann@example.com", "Str0ngPassword"));
        assertThat(res.accessToken()).isEqualTo("jwt-token");
        assertThat(res.tokenType()).isEqualTo("Bearer");
    }

    @Test
    void unknownEmailAndWrongPasswordGiveTheSameError() {
        when(users.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        when(users.findByEmail("ann@example.com")).thenReturn(Optional.of(existing("Str0ngPassword")));

        var unknown = org.junit.jupiter.api.Assertions.assertThrows(BadCredentialsException.class,
                () -> service.login(new LoginRequest("nobody@example.com", "x")));
        var wrong = org.junit.jupiter.api.Assertions.assertThrows(BadCredentialsException.class,
                () -> service.login(new LoginRequest("ann@example.com", "wrong")));
        assertThat(unknown.getMessage()).isEqualTo(wrong.getMessage());
    }

    @Test
    void accountLocksAfterFiveFailures() {
        User user = existing("Str0ngPassword");
        user.setFailedAttempts(AuthService.MAX_FAILED_ATTEMPTS - 1);
        when(users.findByEmail("ann@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login(new LoginRequest("ann@example.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(user.getLockedUntil()).isEqualTo(clock.instant().plus(AuthService.LOCK_DURATION));
        assertThatThrownBy(() -> service.login(new LoginRequest("ann@example.com", "Str0ngPassword")))
                .as("even the right password is refused while locked")
                .isInstanceOf(LockedException.class);
    }

    @Test
    void successfulLoginResetsFailureCounter() {
        User user = existing("Str0ngPassword");
        user.setFailedAttempts(3);
        when(users.findByEmail("ann@example.com")).thenReturn(Optional.of(user));

        service.login(new LoginRequest("ann@example.com", "Str0ngPassword"));

        assertThat(user.getFailedAttempts()).isZero();
    }
}
