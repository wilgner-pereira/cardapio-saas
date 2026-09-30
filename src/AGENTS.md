# Backend AGENTS.md

This file extends the repository root `AGENTS.md`.

All root rules remain mandatory.

It governs backend source, resources, migrations and tests under `src`.

---

## Stack

Backend stack

- Java 21
- Spring Boot 3.3
- Spring MVC
- Spring Security
- Spring Data JPA
- Bean Validation
- Flyway
- PostgreSQL
- H2 PostgreSQL compatibility mode for testsdev
- WebClient for Supabase Storage

Do not replace these technologies without explicit architectural justification.

Do not add Lombok or MapStruct merely to reduce boilerplate.

---

## Existing package responsibilities

Follow the existing package structure

```text
config
controller
exception
modeldto
modelentity
repository
security
service
util
```

Controllers

- parse HTTP input;
- validate DTOs;
- delegate business behavior;
- return HTTP responses.

Services

- own business rules;
- enforce domaintenant behavior;
- coordinate transactions.

Repositories

- perform persistence queries;
- encode tenant-scoped database lookup where appropriate.

DTOs

- define API contracts;
- validate external input.

Entities

- model persistence;
- do not become API response models.

Security package

- owns authenticationsecurity infrastructure.

Do not move business rules into controllers.

---

## Tenant isolation

The authenticated user's `Estabelecimento` is the tenant boundary.

Never trust a tenantestablishment ID supplied by the frontend as authorization.

For tenant resources, preserve patterns equivalent to

```java
findByEstabelecimentoAndId(estabelecimento, id)
```

instead of generic

```java
findById(id)
```

when ownership matters.

Cross-tenant resources should not reveal their existence.

Preserve the existing not-found behavior for unauthorized cross-tenant product lookup.

---

## Roles

Current roles are

```text
ROLE_PLATFORM_ADMIN
ROLE_TENANT_ADMIN
```

Do not reintroduce historical

```text
ROLE_ADMIN
ROLE_USER
```

A platform administrator does not automatically have tenant permissions.

A user must explicitly hold the appropriate tenant role to access `painel`.

Preserve method-level authorization already used on tenantplatform controllers.

---

## Authentication

Authentication currently uses

- short-lived JWT access token;
- JWT refresh token;
- HttpOnly cookies;
- database-backed refresh sessions;
- refresh session ID (`sid`);
- rotating `jti`;
- SHA-256 hash of current refresh identifier in DB;
- absolute refresh-session expiration;
- replay rejection.

Do not replace this with stateless refresh tokens.

Do not put refresh JWTs into the database.

Do not return access or refresh JWTs in JSON responses.

Do not make logout purely client-side.

---

## Refresh sessions

Preserve these invariants

- multiple independent user sessions are allowed;
- refresh rotates the identifier;
- a consumed refresh token cannot be replayed;
- only one concurrent refresh of the same token succeeds;
- logout revokes the relevant server-side session;
- revocation survives application restarts;
- an inactive tenant cannot refresh;
- accessrefresh token types cannot be interchanged.

Read `RefreshSessionIntegrationTest` before modifying this subsystem.

---

## Cookies

Authentication cookies must remain

- HttpOnly;
- Secure in production;
- configured with appropriate SameSite;
- bounded by tokensession lifetime.

Do not expose authentication cookies to JavaScript.

Use `TokenCookieService` instead of duplicating cookie construction.

---

## CSRF

CSRF protection is intentional for cookie-authenticated mutations.

Preserve

- `CookieCsrfTokenRepository`;
- SPA request handler;
- raw cookieheader support;
- XOR token returned by `authcsrf`;
- CSRF requirement on logout;
- CSRF requirement on setup;
- CSRF requirement on tenant mutations.

Do not disable CSRF globally.

A Bearer header does not justify silently removing existing CSRF protections from cookie-oriented flows.

Read `SpaCsrfIntegrationTests` before changing this behavior.

---

## Security setup

`setupadmin` is exceptional bootstrap infrastructure.

It must remain

- disabled by default;
- protected by strong setup token when enabled;
- protected by CSRF;
- concurrency safe;
- unable to create multiple first administrators.

Never expose setup simply because development is easier with it enabled.

---

## Tenant activation

Tenant activeinactive state is authorization-sensitive.

An inactive tenant must be blocked from tenant use even when an access token was previously issued.

Authorization state is intentionally reloaded from the database.

Do not move roles or tenant-active state into JWT claims as the sole authority.

---

## Rate limiting

Rate limiting currently protects important publicauthentication scopes.

Do not bypass `RequestRateLimitFilter` from new equivalent routes.

Existing scopes include

- login;
- refresh;
- setup;
- public traffic.

Do not trust forwarded headers as client identity unless a trusted proxy architecture is explicitly implemented.

Do not remove limits just to make tests or manual development easier.

---

## Validation

Use Jakarta Bean Validation in request DTOs.

Do not rely only on frontend validation.

Preserve length and format bounds for

- username;
- passwords;
- emails;
- names;
- categories;
- image URLs;
- establishment metadata.

Avoid accepting unbounded strings.

---

## Monetary values

Product prices use `BigDecimal`.

Never replace monetary values with

```text
float
double
```

Preserve database precisionscale.

---

## Transactions and locks

Existing locks are intentional.

Important flows serialize concurrent mutations, including

- category order;
- product order;
- storage quota;
- image lifecycle;
- setup;
- refresh rotation.

Do not remove

- transaction boundaries;
- pessimistic locks;
- `FOR UPDATE`;
- tenant row locking;

without understanding and updating the concurrency tests.

A simpler-looking implementation is not necessarily equivalent.

---

## JPA

The project intentionally uses

```text
spring.jpa.open-in-view=false
spring.jpa.hibernate.ddl-auto=validate
```

Do not change these to hide lazy-loading or schema problems.

Fetch required data explicitly.

Be alert to N+1 queries.

Do not solve lazy-loading problems by globally enabling Open Session in View.

---

## Flyway

All database schema evolution goes through Flyway.

Existing migrations are immutable history.

NEVER edit

```text
V1
V2
V3
...
V16
```

after they have entered the repository.

Add a new migration with the next appropriate version.

Migration changes must preserve production data.

Do not drop columnstables or rewrite data destructively unless explicitly required and carefully planned.

Add migration tests for non-trivial data transformations.

---

## Historical seed data

V4 contains historical demonstration data.

V12 intentionally disables insecure historical seed credentials without modifying V4.

Do not clean up V4 by changing its historical content.

Doing so would break Flyway checksum compatibility for existing databases.

Do not restore a shared demo password.

---

## Product ordering

Product order and category order are separate concepts.

Do not collapse them.

Preserve

- internal product order;
- category order;
- deterministic ordering;
- tenant isolation;
- concurrent update safety.

Read

```text
CategoryOrderIntegrationTest
CategoryOrderMigrationTest
```

before modifying ordering behavior.

---

## Storage architecture

Storage changes require extra care.

Current flow includes

```text
SupabaseStorageService
TenantStorageService
StorageQuotaService
StorageImageLifecycle
```

Do not bypass these layers by uploading directly from a controller or unrelated service.

The tenant comes from authenticated server state.

---

## Storage quota

Quota accounting is persistent.

Existing states include

```text
RESERVED
STORED
UNCERTAIN
DELETED
LEGACY
```

These states encode real failure scenarios.

Do not simplify them into a boolean.

Do not release space until remote deletionfailure semantics justify it.

Existing tenants requiring inventory reconciliation must not silently bypass `storage_inventory_required`.

---

## Remote upload failure

There is no distributed transaction between PostgreSQL and Supabase.

Preserve conservative accounting.

A timeout or ambiguous remote failure may represent a completed remote write.

Do not mark such storage as free merely because the client saw an error.

Read `StorageQuotaIntegrationTest` and `TenantStorageFailureTest` before modifying upload failure behavior.

---

## Image replacement

Image replacementremoval is transactional with persistent cleanup semantics.

Preserve

- ownership checks;
- replacement tracking;
- post-commit deletion;
- durable deletion queue;
- retry after restartfailure;
- shared-reference protection;
- external-image protection.

Do not delete the old object before the new database reference successfully commits.

Do not delete an object still referenced anywhere.

Do not delete external image URLs from the configured storage flow.

---

## Supabase WebClient

Supabase communication must remain constrained.

Preserve

- redirect rejection;
- endpoint validation;
- bucket validation;
- pathkey validation;
- operation timeouts;
- fixedsanitized error messages.

Do not enable automatic redirects.

Do not forward arbitrary user-provided URLs through WebClient.

Do not include raw provider response bodies or credentials in exceptionslogs.

---

## Upload validation

Backend upload validation is authoritative.

Preserve validation of

- maximum size;
- MIME;
- magic bytes;
- allowed image formats.

Do not trust filename extensions alone.

Generated storage object names should remain controlled by the application, not raw user filenames.

---

## Error handling

Use the existing centralized exception handler.

Do not expose

- stack traces;
- SQL;
- remote provider bodies;
- credentials;
- internal exception details.

Add a domain exception only when an existing exception does not represent the condition cleanly.

Do not create exceptions merely to rename normal validation behavior.

---

## Logging

Securityprivacy logging has intentional safeguards.

Reuse `LogSanitizer`.

Do not log

- username when an internal ID suffices;
- email;
- passwords;
- tokens;
- cookies;
- Authorization;
- raw request URLs with secretsquery parameters;
- raw SQL values;
- full unexpected exception chains containing sensitive information.

`SECURITY_AUDIT` is the existing security audit logger.

Use it for security-sensitive events where appropriate instead of inventing another audit mechanism.

---

## Health endpoints

Public health endpoints must remain minimal.

Administrative health information is restricted to platform administrators.

Do not expose full actuator details publicly.

Do not expose environmentconfiguration endpoints.

---

## Production profiles

Productionsupabase profiles deliberately suppress sensitivenoisy logging.

Do not enable

- SQL logging;
- Hibernate bind values;
- WebClient wire logs;
- detailed request logging;

in production by default.

Do not introduce fallback production credentials.

Required production configuration should fail closed when absent.

---

## Controllers and responses

Use appropriate HTTP status semantics.

Do not return `200 OK` for application errors merely to simplify frontend code.

Preserve centralized `ApiErrorDTO` response behavior.

Do not expose JPA entities directly from controllers.

---

## Tests

Security tests are part of the security model.

Relevant suites include

```text
SecurityIntegrationTests
SpaCsrfIntegrationTests
RefreshSessionIntegrationTest
InactiveEstablishmentSecurityIntegrationTest
SetupSecurityIntegrationTest
RequestRateLimitIntegrationTest
LogPrivacyTest
StorageQuotaIntegrationTest
ImageReplacementIntegrationTest
StorageRedirectTest
CategoryOrderIntegrationTest
```

When modifying one of those domains, run its focused tests plus the full backend suite.

Full suite

```bash
.mvnw test
```

Do not weaken an assertion merely to accommodate a new implementation.

---

## Final backend review

Before finishing verify

- Tenant identity comes from authenticated context.
- Cross-tenant access is impossible.
- Role separation is intact.
- JWTs are not exposed.
- CSRF remains enforced.
- Refresh replay protection remains intact.
- No migration history was modified.
- Concurrency guarantees remain intact.
- Storage quota cannot be bypassed.
- Image lifecycle cannot delete the wrong tenant's object.
- Sensitive data is absent from logserrors.
- PostgreSQL compatibility is preserved.
- Relevant integration tests pass.

---

## Backend principle

Security mechanisms in this repository are not boilerplate.

Do not remove code merely because it appears more complex than the business feature.

Understand the invariant first.

Then make the smallest safe change.