package com.claimsure.identity.web;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ProblemDetail> badCredentials(BadCredentialsException e) {
        return build(HttpStatus.UNAUTHORIZED, "Authentication failed", e.getMessage());
    }

    @ExceptionHandler(LockedException.class)
    ResponseEntity<ProblemDetail> locked(LockedException e) {
        return build(HttpStatus.LOCKED, "Account locked", e.getMessage());
    }

    private static ResponseEntity<ProblemDetail> build(HttpStatus s, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(s, detail);
        pd.setTitle(title);
        return ResponseEntity.status(s).body(pd);
    }
}
