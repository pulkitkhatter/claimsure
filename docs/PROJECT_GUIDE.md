# ClaimSure — Project Guide (simple explanation)

## 1. What is this project?

ClaimSure is a website for an **insurance company**. It does three jobs:

1. **Customers** get a price (quote) for insurance (Auto, Home, Health, Life) and buy a policy.
2. **Customers** file a **claim** when something goes wrong (e.g. a fire, a car accident).
3. **Staff** (adjusters and admins) review claims, approve or reject them, and pay them.

Every step sends the customer a notification ("Your claim was approved").

Think of a real insurance company: a customer asks "how much for home insurance?", buys it, later has a pipe burst, files a claim, an employee checks it, money is paid. ClaimSure does exactly this flow.

## 2. The big idea: microservices

Instead of one giant application, the project is split into **small applications, each with one job**. They talk to each other over the network.

```
 Browser (HTML/CSS/JavaScript)
        |
        v
 API Gateway (9080)  -- the single front door
   |        |         |          |
   v        v         v          v
Identity  Policy    Claims    Notification
(9081)    (9082)    (9083)    (9084)
   \        |         |          /
    \       |         |         /
     +---- MongoDB (data) ------+
              Kafka (events)  Eureka (phone book, 9761)
```

Why? Each part can be changed, tested, scaled and deployed on its own. If notifications break, buying a policy still works.

## 3. Each service — what it does

| Service | Port | Job in simple words |
|---|---|---|
| **api-gateway** | 9080 | The front door. The browser only talks to this. It forwards each request to the right service, and also serves the website pages. |
| **discovery-server** (Eureka) | 9761 | A phone book. Services register their address here, so they can find each other by name (`policy-service`) instead of fixed addresses. |
| **identity-service** | 9081 | Sign up and log in. Checks the password and hands out a **JWT token** (a digital ID card). |
| **policy-service** | 9082 | Calculates prices (quotes), sells policies, cancels policies. |
| **claims-service** | 9083 | Takes claims, moves them through the workflow, checks for fraud signs. |
| **notification-service** | 9084 | Listens for events and saves a notification for the right customer. |
| **common** (library) | – | Shared code used by all services: security setup, error format, event format. Not a running service. |

## 4. What happens when a user does things (story)

**Login**
1. Browser sends email + password to the gateway → identity-service.
2. identity-service checks the password (stored as a BCrypt hash, never plain text). After 5 wrong tries the account locks for 15 minutes.
3. It returns a **JWT**: a signed token that says who you are and your role (CUSTOMER / ADJUSTER / ADMIN).
4. The browser sends this token with every later request. Every service verifies the signature itself.

**Get a quote and buy**
1. Customer enters cover type, amount, age, past claims, etc.
2. policy-service's **premium calculator** computes the price: `(coverage ÷ 1000 × base rate) × age factor × claims factor × deductible factor ...`, with a minimum price of 120.
3. The same inputs always give the same price, so the result is **cached** (Caffeine) for 10 minutes.
4. When buying, the server **recalculates the price itself** — it never trusts a price sent by the browser.
5. Policy saved in MongoDB. An event `POLICY_ISSUED` is sent to Kafka.

**File a claim**
1. claims-service asks policy-service (found through Eureka) "is this policy real, active and owned by this user?" using the user's own token.
2. Rules checked: policy active, incident date inside policy period and not in future, amount not above coverage.
3. **Fraud scoring** adds points for suspicious signs (amount ≥ 80% of coverage, incident within 30 days of policy start, very short description, other recent claims). Only staff can see the score. It never rejects automatically — a human decides.
4. Claim saved, event `CLAIM_SUBMITTED` sent to Kafka.

**Claim workflow (state machine)**
`SUBMITTED → UNDER_REVIEW → APPROVED → PAID`, with `REJECTED` and `WITHDRAWN` as other endings. Illegal jumps (e.g. SUBMITTED → PAID) are refused. Who can do what:
- Customer: file, withdraw own claim.
- Adjuster: start review, approve, reject.
- Admin: pay.

**Notification**
Each change publishes an event to **Kafka**. notification-service reads it and stores a notification for that customer. The bell icon in the website shows the unread count.

## 5. Technologies — what is used for which part

### Backend
| Technology | Used for |
|---|---|
| **Java 21** | The programming language of all services. |
| **Spring Boot 3.3** | The framework that makes each service run (web server, configuration, wiring). |
| **Spring Web (REST)** | Creating the API endpoints (`/api/v1/policies`, etc.). |
| **Spring Validation** | Rejecting bad input (age 5, short description, weak password). |
| **Spring Security + OAuth2 Resource Server** | Protecting endpoints; verifying JWT tokens; role checks with `@PreAuthorize`. |
| **JWT (HS256) + BCrypt** | JWT = proof of login. BCrypt = safe password storage. |
| **Spring Data MongoDB** | Saving/reading data from MongoDB. |
| **MongoDB** | The database (users, policies, claims, notifications). Each service has its own database. |
| **Apache Kafka + Spring Kafka** | Sending/receiving events between services without them calling each other directly. |
| **Spring Cache + Caffeine** | In-memory cache for quotes and the product list (faster, less repeated work). |
| **Spring Cloud Gateway** | The API gateway: routing, CORS, security headers. |
| **Eureka (Spring Cloud Netflix)** | Service discovery / registration. |
| **Spring Cloud LoadBalancer + RestClient** | claims-service calling `http://policy-service` and Eureka choosing the instance. |
| **springdoc-openapi (Swagger UI)** | Auto-generated API documentation page for each service. |
| **Spring REST Docs** | API documentation snippets generated from tests (so docs are always correct). |
| **Spring Actuator** | Health check endpoints (`/actuator/health`). |
| **RFC 7807 Problem Details** | A standard JSON format for error messages. |
| **Maven (multi-module)** | Building the project; one parent pom, 7 modules. |

### API design
- **Versioning:** `/api/v1/quotes` (old, marked deprecated with a `Deprecation` header) and `/api/v2/quotes` (new, with price breakdown).
- **Pagination:** lists return pages (`page`, `size`, `totalElements`).
- **Proper status codes:** 201 created, 400 invalid, 401 not logged in, 403 not allowed, 404 not found, 409 duplicate, 422 business-rule broken.

### Security (OWASP) — what we protect against
- Passwords hashed with BCrypt; lockout after 5 failures (brute force).
- Same error for "wrong email" and "wrong password" (user enumeration).
- Public sign-up can only create CUSTOMER accounts (privilege escalation).
- Users can only see their own policies/claims; others get 404 (IDOR).
- Price calculated on the server (tampering).
- Input validation everywhere; front-end escapes all text (XSS).
- Security headers + Content-Security-Policy at the gateway; no stack traces in errors.

### Testing (TDD)
| Tool | Used for |
|---|---|
| **JUnit 5 + AssertJ** | Writing unit tests. |
| **Mockito** | Faking the database/Kafka in unit tests. |
| **MockMvc / @WebMvcTest** | Testing REST controllers and security rules without starting the whole app. |
| **Spring Security Test** | Pretending to be a logged-in user with a role. |
| **JaCoCo** | Test coverage report. |
| **node:test** | Tests for the front-end JavaScript logic. |

Test-first examples: premium calculator, claim status rules, fraud scorer, lockout logic, notification factory.

### Frontend
| Technology | Used for |
|---|---|
| **HTML5 / CSS3** | Page structure and design (works on phone, supports dark mode). |
| **JavaScript (ES modules, no framework)** | The whole web app: routing between pages, calling the API, showing data. |
| **fetch API** | Calling the backend. |
| **sessionStorage** | Keeping the login token (removed when the tab closes). |
| `lib.js` | Pure helper functions (formatting, validation, safe HTML) so they can be tested. |

### Infrastructure
| Tool | Used for |
|---|---|
| **Docker + docker-compose** | Running MongoDB and Kafka easily. |
| **Shell scripts** | `start-all.sh` / `stop-all.sh` start and stop everything. |
| **Git + GitHub** | Source code storage (private repo). |

## 6. Project folder map

```
claimsure/
├── pom.xml                  parent Maven file (versions, modules)
├── docker-compose.yml       MongoDB + Kafka
├── scripts/                 start-all.sh, stop-all.sh
├── common/                  shared security, errors, events
├── discovery-server/        Eureka
├── api-gateway/             routing + the website (static/ folder)
│   └── src/main/resources/static/   index.html, css/, js/
├── identity-service/        login/register/JWT
├── policy-service/          pricing, policies, caching
│   └── pricing/PremiumCalculator.java   the price formula
├── claims-service/          claims, workflow, fraud
│   ├── domain/ClaimStatus.java          allowed status moves
│   └── fraud/FraudScorer.java           fraud rules
├── notification-service/    Kafka listener + notifications
└── frontend-tests/          JavaScript tests
```

Inside each service the layers are the same:
`web/` (controllers = the API) → `service/` (business rules) → `domain/` (database objects + repositories).

## 7. How to run and demo

```bash
./scripts/start-all.sh
```
Open http://localhost:9080

Demo order (5 minutes):
1. Log in as `customer@claimsure.test` / `Passw0rd!demo`.
2. **Get a quote** → see the price breakdown → **Buy**.
3. **Policies** → **File a claim**.
4. Sign out, log in as `adjuster@claimsure.test` → **Claims queue** → open claim → see **fraud score** → **Start review** → **Approve**.
5. Log in as `admin@claimsure.test` → open the claim → **Issue payment**.
6. Log back in as the customer → bell icon shows every update.
7. Show Swagger at http://localhost:9082/swagger-ui.html and Eureka at http://localhost:9761.

## 8. Likely interview questions and short answers

- **Why microservices?** Independent deploy/scale/failure; each team owns one service.
- **Why a gateway?** One entry point: routing, CORS, security headers; hides internal services.
- **Why Eureka?** Services find each other by name, so addresses can change.
- **Why Kafka and not a direct call for notifications?** Loose coupling: claims-service doesn't wait for or depend on notification-service; messages are kept if a consumer is down.
- **How is Kafka ordering kept?** The event key is the policy/claim id, so all events of one claim go to the same partition in order.
- **What if Kafka sends the same event twice?** The notification has a unique key (type + entity + time), duplicates are ignored (idempotent consumer).
- **How does auth work across services?** identity-service signs a JWT; every service verifies it with the same secret and reads the role from it (stateless, no sessions).
- **What would you change for production?** Use an asymmetric key / real identity provider instead of one shared secret, TLS, disable seed users, Redis instead of in-memory cache if several instances, add rate limiting and circuit breakers.
- **Why cache quotes?** Pricing is pure maths — same input gives same output — so caching is safe and fast.
- **Why optimistic locking on claims?** Two adjusters cannot decide the same claim at once; the second save fails.
- **How do you stop price tampering?** The server recalculates the premium when buying.
- **Difference between 403 and 404 here?** 403 = your role cannot do this; 404 = the item isn't yours, so we hide that it exists.
