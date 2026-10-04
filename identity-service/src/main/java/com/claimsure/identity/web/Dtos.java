package com.claimsure.identity.web;

import com.claimsure.identity.domain.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

public final class Dtos {
    private Dtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 120) String email,
            @NotBlank @Size(min = 10, max = 72, message = "must be 10-72 characters")
            @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$", message = "needs upper, lower case letters and a digit")
            String password,
            @NotBlank @Size(max = 80) String fullName) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record UserView(String id, String email, String fullName, Set<String> roles) {
        public static UserView of(User u) {
            return new UserView(u.getId(), u.getEmail(), u.getFullName(), u.getRoles());
        }
    }

    public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserView user) {
    }
}
