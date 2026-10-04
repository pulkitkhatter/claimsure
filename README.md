# ClaimSure — insurance policy & claims platform

Spring Boot 3 / Java 21 microservices + vanilla-JS web app. A domain-different sibling of ShopSphere (e-commerce).

| Service | Port | Responsibility |
|---|---|---|
| api-gateway | 9080 | Edge routing (Spring Cloud Gateway), CORS, security headers/CSP, serves the web UI |
| discovery-server | 9761 | Eureka registry |
| identity-service | 9081 | Register / login, BCrypt, lockout, issues JWT (roles CUSTOMER, ADJUSTER, ADMIN) |
| policy-service | 9082 | Rating engine, quotes (v1 deprecated, v2 breakdown), policy lifecycle, Caffeine cache |
| claims-service | 9083 | Claim intake, status workflow, fraud screening, calls policy-service via Eureka |
| notification-service | 9084 | Kafka consumer -> per-user notifications (idempotent) |

Ports are 9xxx so it can run beside ShopSphere. Shared code lives in `common` (JWT resource-server config, RFC 7807 errors, event envelope).

## Run
```bash
./scripts/start-all.sh   # Docker (MongoDB + Kafka), build, start all services
./scripts/stop-all.sh
```
Open http://localhost:9080 — demo logins `customer@` / `adjuster@` / `admin@claimsure.test`, password `Passw0rd!demo`.
Swagger UI per service: http://localhost:9081..9084/swagger-ui.html

## Test
```bash
./mvnw test                 # backend: unit + MVC slice tests (no Docker needed)
node --test frontend-tests/ # front-end pure-logic tests
```
Spring REST Docs snippets are written to `policy-service/target/generated-snippets` by `QuoteControllerTest`.

## Tech coverage
REST + OpenAPI/Swagger (springdoc) + Spring REST Docs · URL versioning (`/api/v1`, `/api/v2` + `Deprecation` header) · Eureka discovery + gateway ·
JWT auth, role-based `@PreAuthorize`, BCrypt, lockout, IDOR-safe 404s, validation, CSP/security headers (OWASP) · MongoDB (Decimal128, unique indexes, optimistic locking) ·
Caffeine caching · Kafka (keyed events, idempotent consumer) · Maven multi-module · TDD with JUnit 5/Mockito/MockMvc/JaCoCo · JS front-end.

## Claim workflow
SUBMITTED → UNDER_REVIEW → APPROVED → PAID, with REJECTED / WITHDRAWN exits. Fraud score is visible to staff only and never auto-rejects.

## Production notes
Replace the shared HMAC secret (`JWT_SECRET`) with an asymmetric IdP/JWKS, disable `claimsure.seed`, and add TLS at the gateway.
