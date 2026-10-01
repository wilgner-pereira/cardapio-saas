# Backend AGENTS.md

This file extends the repository root `AGENTS.md`.

All root rules remain mandatory.

It governs:

- backend source code;
- Spring Security;
- persistence;
- database migrations;
- storage integration;
- backend configuration;
- backend tests;
- resources under `src`.

Do not duplicate general repository or frontend rules here.

---

# Backend Stack

Current backend stack:

- Java 21
- Spring Boot 3.3
- Spring MVC
- Spring Security
- Spring Data JPA
- Jakarta Bean Validation
- Flyway
- PostgreSQL
- H2 in PostgreSQL compatibility mode for development/tests
- Spring WebClient
- Maven

Do not replace these technologies without explicit architectural justification.

Do not add Lombok or MapStruct merely to reduce boilerplate.

Prefer the existing Spring/JPA patterns.

---

# Backend Structure

Backend source root:

```text
src/main/java/com/wilgner/cardapio/
```

Application entry point:

```text
CardapioApplication.java
```

Main packages:

```text
config/
controller/
exception/
model/
repository/
security/
service/
util/
```

Resources:

```text
src/main/resources/
```

Tests:

```text
src/test/java/
```

---

# Package Responsibilities

## config

Framework/application configuration.

Important current classes include:

```text
BootstrapAdminRunner
MultipartConfig
OpenApiConfig
SecurityConfig
WebClientConfig
```

Do not place normal domain/business behavior in configuration classes.

---

## controller

HTTP boundary.

Controllers should:

- receive HTTP input;
- validate DTO input;
- enforce route-level authorization where appropriate;
- delegate business behavior;
- return appropriate HTTP responses.

Controllers should not:

- own business rules;
- perform persistence logic directly;
- implement independent tenant authorization;
- call Supabase directly;
- expose JPA entities as API contracts.

---

## service

Business/domain behavior.

Services should:

- enforce business rules;
- enforce tenant rules;
- coordinate persistence;
- define transaction boundaries;
- preserve concurrency guarantees;
- coordinate storage behavior where required.

Business logic belongs in services rather than controllers.

---

## repository

Persistence queries.

Repositories should:

- use existing Spring Data patterns;
- preserve tenant-scoped queries;
- support locks where required;
- preserve deterministic ordering where required.

Do not use generic lookups when tenant ownership is part of authorization.

---

## model/dto

External API contracts.

DTOs should:

- define request/response shapes;
- validate external input;
- avoid exposing persistence internals.

Use Jakarta Bean Validation for user-controlled input.

---

## model/entity

Persistence entities.

Entities represent database state.

Do not expose them directly through controllers.

Use DTOs and existing mappers.

---

## security

Authentication and authorization infrastructure.

This package owns behavior such as:

- JWT validation;
- authentication filters;
- refresh sessions;
- cookies;
- rate limiting;
- audit logging;
- authentication failures;
- authorization failures;
- SPA CSRF support.

Security code is intentional application behavior.

Do not treat it as disposable framework boilerplate.

---

# Controller Map

Controllers live in:

```text
src/main/java/com/wilgner/cardapio/controller/
```

Current controllers:

```text
AdminCategoriaController
AdminEstabelecimentoController
AdminProdutoController
AuthenticationController
EstabelecimentoController
PlatformAdminController
PublicProdutoController
PublicStorageController
SetupController
SupabaseStorageController
```

Search these before creating a new controller.

---

# AuthenticationController

File:

```text
controller/AuthenticationController.java
```

Base route:

```text
/auth
```

Current operations include:

```text
POST /auth/login
POST /auth/refresh
POST /auth/logout

GET /auth/validate
GET /auth/session
GET /auth/csrf
```

Authentication uses HttpOnly cookies.

Do not return access or refresh JWTs in JSON responses.

---

# PublicProdutoController

File:

```text
controller/PublicProdutoController.java
```

Base route:

```text
/public/{slug}/cardapio
```

Current endpoints:

```text
GET /public/{slug}/cardapio
GET /public/{slug}/cardapio/info
```

Business implementation:

```text
service/PublicProdutoServiceImpl.java
```

These routes power anonymous public menus.

Public behavior must still preserve:

- establishment activation;
- safe field exposure;
- rate limiting;
- product/category ordering.

---

# AdminProdutoController

File:

```text
controller/AdminProdutoController.java
```

Base route:

```text
/painel/produtos
```

Required role:

```text
ROLE_TENANT_ADMIN
```

Current operations include:

```text
POST   /painel/produtos
PUT    /painel/produtos/{id}
PATCH  /painel/produtos/{id}/status
PATCH  /painel/produtos/{id}/ordem
DELETE /painel/produtos/{id}
GET    /painel/produtos/{id}
GET    /painel/produtos
```

Business implementation:

```text
service/AdminProdutoServiceImpl.java
```

Product access must remain tenant-scoped.

Never use a frontend-supplied tenant ID as authorization.

---

# AdminCategoriaController

File:

```text
controller/AdminCategoriaController.java
```

Base route:

```text
/painel/categorias
```

Required role:

```text
ROLE_TENANT_ADMIN
```

Current ordering route:

```text
PATCH /painel/categorias/ordem
```

Category ordering is persisted backend state.

Preserve concurrency safety.

---

# EstabelecimentoController

File:

```text
controller/EstabelecimentoController.java
```

Base route:

```text
/painel/estabelecimento
```

Required role:

```text
ROLE_TENANT_ADMIN
```

Current operations include:

```text
GET /painel/estabelecimento
PUT /painel/estabelecimento
PUT /painel/estabelecimento/logo
PUT /painel/estabelecimento/tema
```

Business implementation:

```text
service/EstabelecimentoServiceImpl.java
```

These operations apply to the authenticated tenant's establishment.

Do not add an arbitrary `estabelecimentoId` parameter to tenant routes as authorization.

---

# AdminEstabelecimentoController

File:

```text
controller/AdminEstabelecimentoController.java
```

Base route:

```text
/plataforma/estabelecimentos
```

Required role:

```text
ROLE_PLATFORM_ADMIN
```

Current operations:

```text
POST   /plataforma/estabelecimentos
GET    /plataforma/estabelecimentos
GET    /plataforma/estabelecimentos/{id}
PATCH  /plataforma/estabelecimentos/{id}/ativar
PATCH  /plataforma/estabelecimentos/{id}/desativar
DELETE /plataforma/estabelecimentos/{id}
```

Business implementation:

```text
service/AdminEstabelecimentoServiceImpl.java
```

These operations are platform-level.

Do not expose them to tenant administrators.

---

# PlatformAdminController

File:

```text
controller/PlatformAdminController.java
```

Base route:

```text
/plataforma/admins
```

Required role:

```text
ROLE_PLATFORM_ADMIN
```

Current operation:

```text
POST /plataforma/admins
```

Do not allow tenant routes to grant `ROLE_PLATFORM_ADMIN`.

---

# SupabaseStorageController

File:

```text
controller/SupabaseStorageController.java
```

Base route:

```text
/painel/storage
```

Required role:

```text
ROLE_TENANT_ADMIN
```

Responsibilities include:

- authenticated tenant image upload;
- unused uploaded-image cleanup.

Storage operations must use the existing storage service chain.

Do not call Supabase directly from this controller.

---

# PublicStorageController

File:

```text
controller/PublicStorageController.java
```

Base route:

```text
/public/storage
```

Current route:

```text
GET /public/storage/{fileName}
```

This provides controlled access to managed objects.

Do not turn this route into an arbitrary URL proxy.

---

# SetupController

File:

```text
controller/SetupController.java
```

Route:

```text
POST /setup/admin
```

This is exceptional initial bootstrap infrastructure.

It is not normal public registration.

It must remain:

- disabled by default;
- protected by a strong setup token when enabled;
- protected by CSRF;
- concurrency-safe;
- unable to create multiple first platform administrators.

Do not weaken setup security for convenience.

---

# Service Map

Services live in:

```text
src/main/java/com/wilgner/cardapio/service/
```

Current important services include:

```text
AdminEstabelecimentoServiceImpl
AdminProdutoServiceImpl
CustomUserDetailsService
EstabelecimentoServiceImpl
ImageUploadProfile
PublicProdutoServiceImpl
SecurityUtils
StorageImageLifecycle
StorageQuotaService
SupabaseStorageService
TenantStorageService
```

---

# AdminProdutoServiceImpl

File:

```text
service/AdminProdutoServiceImpl.java
```

Responsibilities include:

- product creation;
- product editing;
- product deletion;
- product status changes;
- tenant product listing;
- product ordering;
- category ordering.

Current business operations include concepts such as:

```text
criarProduto
atualizarProduto
deleteProduto
atualizarStatus
pesquisarProdutoPorId
listarProdutoDinamico
atualizarOrdem
trocarOrdemCategorias
```

Product order and category order are separate concepts.

Do not collapse them.

Preserve:

- deterministic ordering;
- tenant isolation;
- transactions;
- concurrency safety.

---

# EstabelecimentoServiceImpl

File:

```text
service/EstabelecimentoServiceImpl.java
```

Responsibilities include:

```text
obterMeuEstabelecimento
atualizarMeuEstabelecimento
atualizarLogo
atualizarTema
```

Tenant identity must come from authenticated server-side context.

---

# AdminEstabelecimentoServiceImpl

File:

```text
service/AdminEstabelecimentoServiceImpl.java
```

Responsibilities include:

- creating establishments;
- listing establishments;
- loading establishment details;
- activating establishments;
- deactivating establishments;
- deleting establishments;
- creating the first platform administrator;
- creating additional platform administrators.

Keep platform-level operations separate from tenant operations.

---

# PublicProdutoServiceImpl

File:

```text
service/PublicProdutoServiceImpl.java
```

Responsibilities include:

```text
listarProdutoDinamico
obterInfoEstabelecimento
```

Public establishment lookup uses the unique:

```text
slug
```

---

# CustomUserDetailsService

File:

```text
service/CustomUserDetailsService.java
```

Loads application users for Spring Security.

Before changing user loading or role resolution, inspect security and inactive-tenant tests.

---

# SecurityUtils

File:

```text
service/SecurityUtils.java
```

Provides existing authenticated security/domain context helpers.

Reuse existing helpers instead of reconstructing authentication and tenant lookup manually inside controllers.

---

# Tenant Isolation

The authenticated user's `Estabelecimento` is the tenant boundary.

Never trust a tenant/establishment ID supplied by the frontend as authorization.

For tenant-owned resources, preserve patterns equivalent to:

```java
findByEstabelecimentoAndId(estabelecimento, id)
```

instead of generic:

```java
findById(id)
```

when ownership matters.

Cross-tenant resources should not reveal their existence.

Tenant isolation applies to:

- products;
- ordering;
- establishment configuration;
- image ownership;
- storage quota;
- image deletion.

---

# Roles

Current roles:

```text
ROLE_PLATFORM_ADMIN
ROLE_TENANT_ADMIN
```

Do not reintroduce historical roles such as:

```text
ROLE_ADMIN
ROLE_USER
```

A platform administrator does not automatically have tenant permissions.

Tenant access:

```text
/painel/**
→ ROLE_TENANT_ADMIN
```

Platform access:

```text
/plataforma/**
→ ROLE_PLATFORM_ADMIN
```

Preserve backend authorization regardless of frontend behavior.

---

# Authentication Architecture

Authentication currently uses:

- short-lived JWT access tokens;
- JWT refresh tokens;
- HttpOnly cookies;
- database-backed refresh sessions;
- refresh session ID (`sid`);
- rotating `jti`;
- SHA-256 hash of the current refresh identifier in the database;
- absolute refresh-session expiration;
- replay rejection.

Do not replace this with stateless refresh tokens.

Do not store complete refresh JWTs in the database.

Do not return JWTs in JSON.

Do not make logout purely client-side.

---

# TokenService

File:

```text
security/TokenService.java
```

Responsibilities include:

```text
generateAccessToken
generateRefreshToken
validateAccessToken
validateRefreshToken
verifyRefreshToken
```

Access and refresh tokens are different token types.

Do not make them interchangeable.

Do not move all authorization state into JWT claims.

Persistent application state remains authoritative where required.

---

# TokenCookieService

File:

```text
security/TokenCookieService.java
```

Owns authentication cookie creation/deletion.

Reuse this service rather than constructing auth cookies independently.

Authentication cookies must remain:

- HttpOnly;
- Secure in production;
- configured with the intended SameSite behavior;
- bounded by token/session lifetime.

Do not expose authentication cookies to JavaScript.

---

# RefreshSessionService

File:

```text
security/RefreshSessionService.java
```

Preserve these invariants:

- multiple independent user sessions are allowed;
- refresh rotates the identifier;
- a consumed refresh token cannot be replayed;
- only one concurrent refresh of the same token succeeds;
- logout revokes the relevant server-side session;
- revocation survives application restarts;
- inactive tenants cannot refresh;
- access and refresh token types cannot be interchanged.

Read:

```text
RefreshSessionIntegrationTest
RefreshSessionRestartTest
```

before modifying this subsystem.

---

# SecurityFilter

File:

```text
security/SecurityFilter.java
```

Integrates access-token authentication with Spring Security.

Do not create a parallel authentication filter.

Do not weaken token validation to work around frontend issues.

---

# SecurityConfig

File:

```text
config/SecurityConfig.java
```

Controls behavior including:

- endpoint authorization;
- authentication integration;
- roles;
- CORS;
- CSRF;
- security filters;
- unauthorized responses;
- forbidden responses;
- health endpoint access.

Important boundaries:

```text
/plataforma/**
→ ROLE_PLATFORM_ADMIN

/painel/**
→ ROLE_TENANT_ADMIN
```

Do not make protected routes public to fix authentication problems.

---

# CSRF

CSRF protection is intentional because authentication uses cookies.

Current architecture includes:

```text
CookieCsrfTokenRepository
SpaCsrfTokenRequestHandler
```

Frontend CSRF bootstrap:

```text
GET /auth/csrf
```

Preserve CSRF protection for mutable cookie-authenticated operations.

Do not disable CSRF globally.

Do not create a second independent CSRF implementation.

Read:

```text
SpaCsrfIntegrationTests
```

before changing this behavior.

---

# Rate Limiting

Current implementation:

```text
security/RequestRateLimiter.java
security/RequestRateLimitFilter.java
```

Important scopes include:

- login;
- refresh;
- setup;
- public traffic.

Do not create equivalent routes that bypass existing rate limiting.

Do not remove rate limits merely to simplify tests or development.

Do not trust forwarded headers as client identity unless trusted-proxy behavior is explicitly configured.

---

# Tenant Activation

Tenant active/inactive state is authorization-sensitive.

An inactive tenant must be blocked from tenant use even when an access token was issued before deactivation.

Authorization state is intentionally loaded from persistent state.

Do not use JWT claims as the sole authority for tenant activation.

---

# Validation

Use Jakarta Bean Validation for external request input.

Do not rely only on frontend validation.

Preserve bounds and validation for:

- usernames;
- passwords;
- email addresses;
- establishment names;
- product names;
- descriptions;
- categories;
- image URLs;
- phone numbers;
- social URLs;
- establishment metadata.

Avoid unbounded strings.

Backend validation remains authoritative.

---

# Monetary Values

Product prices use:

```java
BigDecimal
```

Never replace monetary values with:

```text
float
double
```

Preserve database precision and scale.

---

# JPA

The project intentionally uses:

```text
spring.jpa.open-in-view=false
spring.jpa.hibernate.ddl-auto=validate
```

Do not change these settings to hide persistence problems.

Do not globally enable Open Session in View.

Fetch required information explicitly.

Watch for N+1 queries when modifying relationships and DTO mapping.

---

# Entities

Entities live in:

```text
src/main/java/com/wilgner/cardapio/model/entity/
```

Current entities include:

```text
CardapioTema
Estabelecimento
Produto
RefreshSession
Role
Usuario
```

---

# Estabelecimento

File:

```text
model/entity/Estabelecimento.java
```

Important concepts include:

- name;
- unique public slug;
- description;
- opening hours;
- phone;
- address;
- contact email;
- Instagram URL;
- WhatsApp;
- logo URL;
- hero image URL;
- menu theme;
- product image-shape setting;
- active state;
- timestamps.

The public menu uses:

```text
slug
```

Do not replace the public slug with an internal database ID.

---

# Produto

File:

```text
model/entity/Produto.java
```

Important concepts include:

- name;
- description;
- `BigDecimal` price;
- active state;
- category;
- category order;
- product order;
- image URL;
- establishment ownership;
- associated user.

Product order and category order are separate.

---

# Usuario

File:

```text
model/entity/Usuario.java
```

Important relationships include:

- establishment;
- roles;
- refresh sessions.

Never expose password data through API responses.

---

# Role

File:

```text
model/entity/Role.java
```

Supported application roles:

```text
ROLE_PLATFORM_ADMIN
ROLE_TENANT_ADMIN
```

---

# RefreshSession

File:

```text
model/entity/RefreshSession.java
```

Persists server-side refresh-session state.

Do not replace persistent refresh sessions with fully stateless refresh JWT behavior.

---

# DTOs

DTOs live under:

```text
src/main/java/com/wilgner/cardapio/model/dto/
```

Current groups include:

```text
auth/
error/
estabelecimento/
product/
```

Important mappers include:

```text
EstabelecimentoMapper
ProdutoMapper
```

Controllers should use DTOs instead of exposing JPA entities.

Reuse existing DTOs/mappers before creating parallel versions.

---

# Repositories

Repositories live in:

```text
src/main/java/com/wilgner/cardapio/repository/
```

Current repositories:

```text
EstabelecimentoRepository
ProdutoRepository
RefreshSessionRepository
RoleRepository
UsuarioRepository
```

Before adding a repository query:

1. search for an equivalent query;
2. determine whether tenant scoping is required;
3. determine whether locking is required;
4. preserve expected ordering;
5. inspect related tests.

Do not use generic `findById()` where tenant ownership is part of authorization.

---

# Transactions and Locks

Existing locks are intentional.

Important concurrent flows include:

- product ordering;
- category ordering;
- storage quota;
- image lifecycle;
- setup/bootstrap;
- refresh rotation.

Do not remove:

- transaction boundaries;
- pessimistic locks;
- `FOR UPDATE`;
- tenant row locking;

without understanding the invariant and relevant tests.

A simpler-looking implementation is not necessarily equivalent.

---

# Product and Category Ordering

Product order and category order are independent.

Preserve:

- product position within a category;
- category position;
- deterministic ordering;
- tenant isolation;
- concurrent update safety.

Before changing ordering, read:

```text
CategoryOrderIntegrationTest
CategoryOrderMigrationTest
```

and other related ordering tests.

---

# Storage Architecture

Storage changes require extra care.

Current storage flow includes:

```text
TenantStorageService
StorageQuotaService
StorageImageLifecycle
SupabaseStorageService
ImageUploadProfile
```

Do not bypass these layers.

The authenticated tenant must come from trusted server-side context.

---

# TenantStorageService

File:

```text
service/TenantStorageService.java
```

Coordinates authenticated tenant uploads.

Conceptual flow:

```text
authenticated tenant
        ↓
backend file validation
        ↓
application-controlled object key
        ↓
persistent quota reservation
        ↓
remote Supabase upload
        ↓
persistent storage state update
```

Do not create a second upload flow.

---

# ImageUploadProfile

File:

```text
service/ImageUploadProfile.java
```

Defines upload constraints/profiles used by the storage flow.

Reuse this behavior instead of hardcoding independent upload limits in controllers.

---

# StorageQuotaService

File:

```text
service/StorageQuotaService.java
```

Storage accounting is persistent.

Existing state concepts include:

```text
RESERVED
STORED
UNCERTAIN
DELETED
LEGACY
```

These states represent real remote-failure scenarios.

Do not simplify them into a boolean.

Do not release quota solely because:

- a controller request failed;
- a local database reference disappeared;
- a remote response timed out.

Remote object state may be ambiguous.

---

# Remote Upload Failures

There is no distributed transaction between:

```text
PostgreSQL
```

and:

```text
Supabase Storage
```

A timeout may occur after Supabase has already accepted an object.

Preserve conservative accounting.

Do not mark storage as free simply because the client received an error.

Read:

```text
StorageQuotaIntegrationTest
TenantStorageFailureTest
```

before changing remote-failure behavior.

---

# StorageImageLifecycle

File:

```text
service/StorageImageLifecycle.java
```

Responsibilities include:

- unused image cleanup;
- image replacement tracking;
- safe remote deletion;
- durable deletion queue;
- retries after failure/restart;
- shared-reference protection;
- external-image protection.

Important invariant:

Do not delete the previous image before the new database reference successfully commits.

Do not delete an object still referenced elsewhere.

Do not treat arbitrary external URLs as managed Supabase objects.

---

# SupabaseStorageService

File:

```text
service/SupabaseStorageService.java
```

Low-level Supabase Storage integration.

Responsibilities include:

- backend file validation;
- managed object key parsing;
- public URL generation;
- upload;
- deletion;
- download;
- Supabase endpoint validation;
- bucket validation;
- redirect rejection;
- timeout behavior;
- sanitized provider errors.

Business/domain code should not communicate with Supabase directly.

Use the existing storage services.

---

# Supabase WebClient Security

Supabase communication must remain constrained.

Preserve:

- redirect rejection;
- endpoint validation;
- bucket validation;
- object-key validation;
- operation timeouts;
- sanitized error handling.

Do not enable automatic redirects casually.

Do not forward arbitrary user-supplied URLs through WebClient.

Do not expose raw provider responses or credentials through logs/errors.

---

# Upload Validation

Backend image validation is authoritative.

Preserve validation of:

- maximum size;
- MIME type;
- magic bytes;
- allowed image formats;
- application-controlled object names.

Do not trust filename extensions.

Do not use raw user filenames as storage identity.

Frontend optimization does not replace backend validation.

---

# Configuration

Backend configuration lives under:

```text
src/main/resources/
```

Current files include:

```text
application.properties
application-dev.properties
application-local-postgres.properties
application-prod.properties
application-supabase.properties
```

Current production deployment uses:

```text
SPRING_PROFILES_ACTIVE=supabase
```

Important production configuration includes:

```text
DATABASE_URL
DATABASE_USERNAME
DATABASE_PASSWORD

JWT_SECRET

COOKIE_SECURE
COOKIE_SAME_SITE

CORS_ALLOWED_ORIGINS

SUPABASE_URL
SUPABASE_KEY
SUPABASE_BUCKET
```

Never introduce fallback production credentials.

Required security-sensitive configuration should fail closed when missing.

---

# Production Logging

Production/Supabase profiles intentionally avoid sensitive/noisy logging.

Do not enable by default:

- SQL value logging;
- Hibernate bind-value logging;
- WebClient wire logging;
- raw HTTP body logging;
- authentication-cookie logging;
- token logging.

Production debugging must not expose secrets.

---

# Flyway

All production schema evolution goes through Flyway.

Migration directory:

```text
src/main/resources/db/migration/
```

Current repository migrations include:

```text
V1__create_initial_schema.sql
V2__assign_role_user_to_existing_users.sql
V3__create_estabelecimento_table.sql
V4__add_endereco_and_seed_sample_data.sql
V7__add_category_order.sql
V8__add_tema_to_estabelecimento.sql
V9__add_tema_check_constraint.sql
V10__clarify_platform_and_tenant_roles.sql
V11__create_refresh_sessions.sql
V12__disable_insecure_seed_credentials.sql
V13__track_tenant_storage.sql
V14__normalize_category_order.sql
V15__add_establishment_hero_image.sql
V16__queue_replaced_image_deletions.sql
V17__add_product_image_shape.sql
V18__add_establishment_social_links.sql
```

Migration history is immutable.

Never edit an already released migration.

Do not renumber migrations.

Do not create fake historical migrations merely to fill missing version numbers.

New schema changes require a new migration.

Migration changes must preserve production data.

Avoid destructive operations unless explicitly required and carefully planned.

Add migration tests for non-trivial data transformations.

---

# Historical Seed Data

Historical migrations may contain old demo/setup data.

Do not rewrite historical migrations to clean them up.

Later migrations may intentionally correct historical state.

Changing an executed migration can break Flyway checksum compatibility with existing databases.

---

# Exception Handling

Exception package:

```text
src/main/java/com/wilgner/cardapio/exception/
```

Current classes include:

```text
ApiExceptionHandler
BusinessException
ConflictException
ExternalServiceException
ResourceNotFoundException
StorageQuotaException
StorageWriteRejectedException
```

Use the centralized exception handler.

Add a new domain exception only when existing exceptions do not represent the condition cleanly.

Do not create exceptions merely to rename ordinary validation behavior.

---

# API Errors

Preserve the centralized:

```text
ApiErrorDTO
```

response behavior.

Use appropriate HTTP status codes.

Do not return `200 OK` for application failures merely to simplify frontend handling.

Do not expose:

- stack traces;
- SQL;
- database internals;
- raw Supabase response bodies;
- credentials;
- internal security details.

---

# Logging

Security/privacy logging has intentional safeguards.

Reuse:

```text
LogSanitizer
```

where applicable.

Security-sensitive audit behavior uses:

```text
SECURITY_AUDIT
```

Do not log:

- passwords;
- authentication tokens;
- JWTs;
- cookies;
- Authorization headers;
- database passwords;
- Supabase keys;
- secret query parameters;
- raw SQL values;
- provider responses containing sensitive data.

Prefer internal IDs over unnecessary personal identifiers.

---

# Health Endpoints

Public health information must remain minimal.

Administrative health information must remain restricted.

Do not expose publicly:

- environment configuration;
- secrets;
- database credentials;
- detailed actuator data;
- sensitive system internals.

Do not expose all Actuator endpoints.

---

# CORS

Production browser origin is configured through:

```text
CORS_ALLOWED_ORIGINS
```

Current production frontend:

```text
https://lunemenu.app
```

Credentialed CORS is required because authentication uses cookies.

Do not replace the origin allowlist with:

```text
*
```

when credentials are involved.

Do not weaken CORS to hide an incorrect frontend configuration.

---

# API Responsibility Boundaries

Keep these areas distinct:

Public:

```text
/public/**
```

Tenant administration:

```text
/painel/**
```

Platform administration:

```text
/plataforma/**
```

Authentication:

```text
/auth/**
```

Exceptional bootstrap:

```text
/setup/**
```

Do not place tenant functionality under public routes.

Do not expose platform functionality through tenant routes.

---

# Backend Tests

Tests live under:

```text
src/test/java/
```

Important suites include:

```text
CardapioApplicationTests
DownloadPerformanceTest
EstabelecimentoTests
PlatformDashboardIntegrationTest

BootstrapAdminRunnerIntegrationTest
BootstrapAdminRunnerTest
CategoryOrderMigrationTest
DeploymentSecurityConfigurationTest
HeroImageMigrationTest
RoleMigrationTest
SeedCredentialMigrationTest
StorageMigrationTest

InactiveEstablishmentSecurityIntegrationTest
LogPrivacyTest
RefreshSessionIntegrationTest
RefreshSessionRestartTest
RequestRateLimitIntegrationTest
RequestRateLimiterTest
SecurityIntegrationTests
SetupSecurityIntegrationTest
SpaCsrfIntegrationTests

CategoryOrderIntegrationTest
HeroImageIntegrationTest
ImageReplacementIntegrationTest
PublicProdutoServiceImplTest
SecurityUtilsTest
StorageQuotaIntegrationTest
StorageRedirectTest
SupabaseStorageServiceTest
TenantStorageFailureTest
```

Security tests are part of the security model.

Do not weaken assertions merely to accommodate a new implementation.

---

# Which Tests to Read First

Authentication:

```text
SecurityIntegrationTests
RefreshSessionIntegrationTest
RefreshSessionRestartTest
```

CSRF:

```text
SpaCsrfIntegrationTests
```

Tenant activation:

```text
InactiveEstablishmentSecurityIntegrationTest
```

Setup/bootstrap:

```text
SetupSecurityIntegrationTest
BootstrapAdminRunnerIntegrationTest
```

Rate limiting:

```text
RequestRateLimitIntegrationTest
RequestRateLimiterTest
```

Storage/quota:

```text
StorageQuotaIntegrationTest
TenantStorageFailureTest
SupabaseStorageServiceTest
StorageRedirectTest
```

Image replacement:

```text
ImageReplacementIntegrationTest
HeroImageIntegrationTest
```

Category ordering:

```text
CategoryOrderIntegrationTest
CategoryOrderMigrationTest
```

Platform administration:

```text
PlatformDashboardIntegrationTest
```

---

# Running Backend Tests

From the repository root:

```bash
./mvnw test
```

Windows:

```text
mvnw.cmd test
```

Or with installed Maven:

```bash
mvn -B test
```

Run focused tests during development when useful.

Before completing a security-sensitive, persistence-sensitive or storage-sensitive change, run the relevant integration tests and preferably the complete backend suite.

Never claim tests passed unless they actually ran successfully.

---

# Common Backend Change Map

Use this map before creating a new class.

```text
Login / refresh / logout / session
→ controller/AuthenticationController.java

JWT creation and validation
→ security/TokenService.java

Authentication cookies
→ security/TokenCookieService.java

Refresh session persistence/rotation
→ security/RefreshSessionService.java

Access-token authentication
→ security/SecurityFilter.java

Authorization / CORS / CSRF
→ config/SecurityConfig.java

SPA CSRF handling
→ security/SpaCsrfTokenRequestHandler.java

Rate limiting
→ security/RequestRateLimiter.java
→ security/RequestRateLimitFilter.java

Tenant product CRUD
→ controller/AdminProdutoController.java
→ service/AdminProdutoServiceImpl.java

Product ordering
→ service/AdminProdutoServiceImpl.java

Category ordering
→ controller/AdminCategoriaController.java
→ service/AdminProdutoServiceImpl.java

Tenant establishment
→ controller/EstabelecimentoController.java
→ service/EstabelecimentoServiceImpl.java

Platform establishments/customers
→ controller/AdminEstabelecimentoController.java
→ service/AdminEstabelecimentoServiceImpl.java

Platform administrators
→ controller/PlatformAdminController.java
→ service/AdminEstabelecimentoServiceImpl.java

Public menu
→ controller/PublicProdutoController.java
→ service/PublicProdutoServiceImpl.java

Tenant upload endpoint
→ controller/SupabaseStorageController.java

Public managed image delivery
→ controller/PublicStorageController.java

Upload orchestration
→ service/TenantStorageService.java

Storage quota/inventory
→ service/StorageQuotaService.java

Image replacement/deletion lifecycle
→ service/StorageImageLifecycle.java

Low-level Supabase Storage integration
→ service/SupabaseStorageService.java

Authenticated user loading
→ service/CustomUserDetailsService.java

Authenticated tenant/domain context
→ service/SecurityUtils.java

Database entities
→ model/entity/

API contracts
→ model/dto/

Persistence queries
→ repository/

Database schema
→ resources/db/migration/

Central API errors
→ exception/ApiExceptionHandler.java
```

If requested behavior already belongs to one of these paths, extend the existing path instead of creating a parallel implementation.

---

# Final Backend Review

Before finishing a backend change, verify:

- Did I read the root `AGENTS.md`?
- Did I search for an existing implementation?
- Is tenant identity derived from authenticated context?
- Is cross-tenant access still impossible?
- Is platform/tenant role separation intact?
- Are protected routes still protected?
- Are JWTs still hidden from JSON and browser JavaScript?
- Are authentication cookies still HttpOnly?
- Is CSRF still enforced?
- Is refresh replay protection intact?
- Can inactive tenants still be blocked?
- Is rate limiting preserved?
- Are DTO validation limits preserved?
- Are monetary values still `BigDecimal`?
- Is `open-in-view` still disabled?
- Is schema mode still `validate`?
- Did I avoid modifying historical Flyway migrations?
- Are transaction and locking guarantees preserved?
- Is product/category ordering deterministic?
- Did I use the existing storage architecture?
- Can storage quota still not be bypassed?
- Can image cleanup still not delete another tenant's object?
- Are remote storage failures handled conservatively?
- Are sensitive values absent from logs and errors?
- Is PostgreSQL compatibility preserved?
- Are relevant tests passing?

If any answer is uncertain, investigate before finishing.

---

# Backend Principle

Security mechanisms in this repository are intentional application behavior, not disposable framework boilerplate.

Tenant isolation, authentication, refresh rotation, CSRF, rate limiting, database constraints, locking and storage lifecycle rules are production invariants.

Do not remove complexity before understanding why it exists.

Investigate first.

Reuse existing paths.

Preserve invariants.

Then make the smallest safe change.