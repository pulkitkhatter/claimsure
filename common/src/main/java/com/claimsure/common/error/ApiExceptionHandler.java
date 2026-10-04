package com.claimsure.common.error;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** RFC 7807 problem+json for every failure; never leaks stack traces or internals (OWASP A05/A09). */
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(ResourceNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Not found", e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ProblemDetail> conflict(ConflictException e) {
        return problem(HttpStatus.CONFLICT, "Conflict", e.getMessage());
    }

    @ExceptionHandler(BusinessRuleException.class)
    ResponseEntity<ProblemDetail> rule(BusinessRuleException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Business rule violated", e.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> denied(AccessDeniedException e) {
        return problem(HttpStatus.FORBIDDEN, "Forbidden", "You are not allowed to perform this action");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> invalid(MethodArgumentNotValidException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> errors.putIfAbsent(f.getField(), f.getDefaultMessage()));
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        pd.setTitle("Invalid request");
        pd.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(pd);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    ResponseEntity<ProblemDetail> badArg(Exception e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", "A request parameter has an invalid value");
    }

    /** A downstream service answered with an error: pass 403/404 through, hide the rest behind 502. */
    @ExceptionHandler(HttpClientErrorException.class)
    ResponseEntity<ProblemDetail> downstream(HttpClientErrorException e) {
        HttpStatus s = HttpStatus.resolve(e.getStatusCode().value());
        if (s == HttpStatus.NOT_FOUND || s == HttpStatus.FORBIDDEN) {
            return problem(s, s.getReasonPhrase(), "Referenced resource is not available to you");
        }
        log.warn("Downstream call failed: {}", e.getMessage());
        return problem(HttpStatus.BAD_GATEWAY, "Bad gateway", "A dependent service failed");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception e) {
        log.error("Unhandled error", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Server error", "Something went wrong");
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setType(URI.create("about:blank"));
        return ResponseEntity.status(status).body(pd);
    }
}
