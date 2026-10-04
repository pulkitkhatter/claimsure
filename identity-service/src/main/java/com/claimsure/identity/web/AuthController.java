package com.claimsure.identity.web;

import com.claimsure.identity.service.AuthService;
import com.claimsure.identity.web.Dtos.AuthResponse;
import com.claimsure.identity.web.Dtos.LoginRequest;
import com.claimsure.identity.web.Dtos.RegisterRequest;
import com.claimsure.identity.web.Dtos.UserView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a customer account")
    public UserView register(@Valid @RequestBody RegisterRequest req) {
        return auth.register(req);
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange credentials for a JWT access token")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req);
    }

    @GetMapping("/me")
    @Operation(summary = "Profile of the authenticated user", security = @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearer"))
    public UserView me(@AuthenticationPrincipal Jwt jwt) {
        return auth.me(jwt.getSubject());
    }
}
