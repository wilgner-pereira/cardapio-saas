# Backend AGENTS.md

This file extends the repository root `AGENTS.md`.

All root rules remain mandatory.

This file governs:

- backend source code;
- Spring Security;
- persistence;
- database migrations;
- storage integration;
- backend tests;
- resources under `src`.

Do not duplicate root-level project or deployment documentation here unless it
directly affects backend implementation.

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
- Spring WebClient for Supabase Storage
- Maven

Do not replace these technologies without explicit architectural justification.

Do not add Lombok or MapStruct merely to reduce boilerplate.

Prefer the existing Spring/JPA patterns already used by the application.

---

# Backend Structure

Backend source root:

```text
src/main/java/com/wilgner/cardapio/



Application entry point:
CardapioApplication.java

Current main packages:
config/
controller/
exception/
model/
repository/
security/
service/
util/

Resources:
src/main/resources/

Tests:
src/test/java/

Package Responsibilities
config
Application and framework configuration.
Current important classes include:
BootstrapAdminRunner
MultipartConfig
OpenApiConfig
SecurityConfig
WebClientConfig

Do not place normal business rules in configuration classes.
controller
HTTP boundary.
Controllers should:
- parse HTTP input;
- receive validated DTOs;
- enforce route-level authorization where appropriate;
- delegate business behavior;
- return appropriate HTTP responses.
Controllers should NOT:
- own business rules;
- implement tenant authorization manually when a service/helper already exists;
- perform direct Supabase operations;
- contain persistence logic;
- expose JPA entities directly.
service
Business/domain behavior.
Services should:
- enforce business rules;
- resolve tenant context where appropriate;
- coordinate persistence;
- manage transactions;
- preserve concurrency guarantees;
- coordinate external storage when required.
Business rules belong here rather than in controllers.
repository
Persistence queries.
Repositories should:
- use Spring Data JPA patterns already present;
- encode tenant-scoped lookups when ownership matters;
- support locking where required;
- avoid generic queries that accidentally bypass tenant boundaries.
model/dto
External API contracts.
DTOs should:
- define request/response shapes;
- validate external input;
- avoid exposing persistence internals.
Use Jakarta Bean Validation for user-controlled request data.
model/entity
Persistence entities.
Entities represent database state.
Do not expose entities directly as API response models.
Use DTOs and existing mappers.
security
Authentication and authorization infrastructure.
This package owns behavior such as:
- JWT handling;
- authentication filters;
- refresh sessions;
- cookie handling;
- request rate limiting;
- audit logging;
- authentication/authorization error handling;
- CSRF support.
Security code is not boilerplate.
Understand its invariants before modifying it.
Controller Map
Controllers currently live in:
src/main/java/com/wilgner/cardapio/controller/

Current controllers:
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

Use the map below before creating another controller.
AuthenticationController
File:
controller/AuthenticationController.java

Base route:
/auth

Current endpoints include:
POST /auth/login
POST /auth/refresh
POST /auth/logout

GET /auth/validate
GET /auth/session
GET /auth/csrf

Authentication uses HttpOnly cookies.
Do not return access or refresh JWTs in JSON responses.
PublicProdutoController
File:
controller/PublicProdutoController.java

Base route:
/public/{slug}/cardapio

Current endpoints:
GET /public/{slug}/cardapio
GET /public/{slug}/cardapio/info

Business implementation:
service/PublicProdutoServiceImpl.java

These routes power the public restaurant menu.
They do not require an authenticated tenant session.
Public behavior must still respect:
- establishment activation;
- safe field exposure;
- rate limiting;
- deterministic product/category ordering.
AdminProdutoController
File:
controller/AdminProdutoController.java

Base route:
/painel/produtos

Required role:
ROLE_TENANT_ADMIN

Current operations include:
POST   /painel/produtos
PUT    /painel/produtos/{id}
PATCH  /painel/produtos/{id}/status
PATCH  /painel/produtos/{id}/ordem
DELETE /painel/produtos/{id}
GET    /painel/produtos/{id}
GET    /painel/produtos

Business implementation:
service/AdminProdutoServiceImpl.java

Product authorization must remain tenant-scoped.
Never use a browser-provided tenant ID as proof of ownership.
AdminCategoriaController
File:
controller/AdminCategoriaController.java

Base route:
/painel/categorias

Required role:
ROLE_TENANT_ADMIN

Current operation:
PATCH /painel/categorias/ordem

Category ordering is tied to tenant product ordering.
The existing concurrency behavior must be preserved.
EstabelecimentoController
File:
controller/EstabelecimentoController.java

Base route:
/painel/estabelecimento

Required role:
ROLE_TENANT_ADMIN

Current operations include:
GET /painel/estabelecimento
PUT /painel/estabelecimento
PUT /painel/estabelecimento/logo
PUT /painel/estabelecimento/tema

Business implementation:
service/EstabelecimentoServiceImpl.java

These endpoints operate on the authenticated tenant's establishment.
Do not add an arbitrary estabelecimentoId parameter to tenant endpoints as
authorization.
AdminEstabelecimentoController
File:
controller/AdminEstabelecimentoController.java

Base route:
/plataforma/estabelecimentos

Required role:
ROLE_PLATFORM_ADMIN

Current operations include:
POST   /plataforma/estabelecimentos
GET    /plataforma/estabelecimentos
GET    /plataforma/estabelecimentos/{id}
PATCH  /plataforma/estabelecimentos/{id}/ativar
PATCH  /plataforma/estabelecimentos/{id}/desativar
DELETE /plataforma/estabelecimentos/{id}

Business implementation:
service/AdminEstabelecimentoServiceImpl.java

These are platform-level operations and must not be exposed to tenant
administrators.
PlatformAdminController
File:
controller/PlatformAdminController.java

Base route:
/plataforma/admins

Required role:
ROLE_PLATFORM_ADMIN

Current main operation:
POST /plataforma/admins

Do not allow tenant administration endpoints to grant platform roles.
SupabaseStorageController
File:
controller/SupabaseStorageController.java

Base route:
/painel/storage

Required role:
ROLE_TENANT_ADMIN

Current responsibilities include:
- authenticated tenant image upload;
- unused uploaded image cleanup.
Storage operations MUST use the existing storage service chain.
Do not upload directly to Supabase from the controller.
PublicStorageController
File:
controller/PublicStorageController.java

Base route:
/public/storage

Current public resource route:
GET /public/storage/{fileName}

This provides controlled access to managed storage objects.
Do not turn this endpoint into a generic proxy for arbitrary URLs.
SetupController
File:
controller/SetupController.java

Route:
POST /setup/admin

This is exceptional bootstrap infrastructure.
It is not a public registration feature.
It must remain:
- disabled by default;
- protected by a strong setup token when enabled;
- protected by CSRF;
- concurrency safe;
- unable to create multiple first platform administrators.
Do not weaken setup security for development convenience.
Service Map
Services currently live in:
src/main/java/com/wilgner/cardapio/service/

Current important services:
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

AdminProdutoServiceImpl
File:
service/AdminProdutoServiceImpl.java

Owns tenant product behavior such as:
- product creation;
- product editing;
- product deletion;
- status changes;
- tenant product listing;
- product ordering;
- category ordering.
Important methods currently include concepts equivalent to:
criarProduto
atualizarProduto
deleteProduto
atualizarStatus
pesquisarProdutoPorId
listarProdutoDinamico
atualizarOrdem
trocarOrdemCategorias

Product and category ordering are separate concepts.
Do not collapse them into one ordering field.
Preserve:
- deterministic ordering;
- tenant isolation;
- transaction boundaries;
- concurrency safety.
EstabelecimentoServiceImpl
File:
service/EstabelecimentoServiceImpl.java

Owns the authenticated tenant establishment.
Current behavior includes:
obterMeuEstabelecimento
atualizarMeuEstabelecimento
atualizarLogo
atualizarTema

Tenant identity must come from authenticated server-side context.
AdminEstabelecimentoServiceImpl
File:
service/AdminEstabelecimentoServiceImpl.java

Owns platform-level establishment/customer behavior.
Current responsibilities include:
- creating establishments;
- listing establishments;
- reading establishment details;
- activating establishments;
- deactivating establishments;
- deleting establishments;
- creating the first platform administrator;
- creating additional platform administrators.
Keep platform operations explicitly separate from tenant operations.
PublicProdutoServiceImpl
File:
service/PublicProdutoServiceImpl.java

Owns public menu access.
Current responsibilities include:
listarProdutoDinamico
obterInfoEstabelecimento

Public establishment lookup uses the unique establishment:
slug

CustomUserDetailsService
File:
service/CustomUserDetailsService.java

Loads application users for Spring Security.
Before changing user loading or role resolution, inspect:
- authentication tests;
- inactive-establishment tests;
- refresh-session tests.
SecurityUtils
File:
service/SecurityUtils.java

Provides authenticated security/domain context helpers.
Reuse existing helpers instead of repeatedly reconstructing authentication or
tenant lookup logic inside controllers.
Tenant Isolation
The authenticated user's Estabelecimento is the tenant boundary.
A tenant must never read, modify or delete another tenant's protected data.
Never trust a tenant/establishment ID supplied by the frontend as
authorization.
For tenant resources, preserve patterns equivalent to:
findByEstabelecimentoAndId(estabelecimento, id)

rather than generic:
findById(id)

when ownership matters.
Cross-tenant resources should not reveal their existence.
Preserve not-found behavior for unauthorized tenant resource access where the
current application uses it.
Tenant isolation also applies to:
- products;
- ordering;
- image ownership;
- storage quota;
- storage deletion;
- establishment configuration.
Roles
Current roles:
ROLE_PLATFORM_ADMIN
ROLE_TENANT_ADMIN

Do not reintroduce historical roles such as:
ROLE_ADMIN
ROLE_USER

A platform administrator does not automatically have tenant permissions.
A user must explicitly have the tenant role to access:
/painel/**

A user must explicitly have the platform role to access:
/plataforma/**

Preserve method-level authorization already used by controllers.
Do not rely only on frontend route protection.
Authentication Architecture
Authentication currently uses:
- short-lived JWT access tokens;
- JWT refresh tokens;
- HttpOnly cookies;
- persistent refresh sessions;
- refresh session ID (sid);
- rotating jti;
- SHA-256 hash of the current refresh identifier in the database;
- absolute refresh-session expiration;
- refresh replay rejection.
Do not replace this architecture with stateless refresh tokens.
Do not store refresh JWTs directly in the database.
Do not return JWTs in JSON.
Do not make logout purely client-side.
TokenService
File:
security/TokenService.java

Owns JWT creation and validation.
Current behavior includes:
generateAccessToken
generateRefreshToken
validateAccessToken
validateRefreshToken
verifyRefreshToken

Access and refresh tokens are different token types.
Do not make them interchangeable.
Do not move all authorization state into JWT claims.
The database remains authoritative for relevant authorization state.
TokenCookieService
File:
security/TokenCookieService.java

Owns authentication cookie creation/deletion.
Reuse this service instead of duplicating cookie construction.
Authentication cookies must remain:
- HttpOnly;
- Secure in production;
- configured with the expected SameSite policy;
- bounded by token/session lifetime.
Do not expose authentication cookies to JavaScript.
RefreshSessionService
File:
security/RefreshSessionService.java

Owns persistent refresh-session behavior.
Preserve these invariants:
- multiple independent user sessions are allowed;
- refresh rotates the identifier;
- consumed refresh tokens cannot be replayed;
- only one concurrent refresh of the same token succeeds;
- logout revokes the relevant server-side session;
- revocation survives application restarts;
- inactive tenants cannot refresh;
- access and refresh tokens cannot be interchanged.
Read:
RefreshSessionIntegrationTest
RefreshSessionRestartTest

before modifying this subsystem.
SecurityFilter
File:
security/SecurityFilter.java

Handles access-token authentication integration with Spring Security.
Do not create a parallel authentication filter.
Do not make token validation weaker to work around frontend issues.
Spring Security Configuration
Main configuration:
config/SecurityConfig.java

This file controls behavior such as:
- endpoint authorization;
- authentication;
- role boundaries;
- CORS;
- CSRF;
- session behavior;
- security filters;
- unauthorized responses;
- forbidden responses;
- health endpoint access.
Important authorization boundaries:
/plataforma/**
→ ROLE_PLATFORM_ADMIN

/painel/**
→ ROLE_TENANT_ADMIN

Do not make protected routes public to fix authentication problems.
CSRF
CSRF protection is intentional because authentication uses cookies.
Current CSRF architecture includes:
CookieCsrfTokenRepository
SpaCsrfTokenRequestHandler

Frontend CSRF bootstrap:
GET /auth/csrf

Preserve CSRF protection on mutable cookie-authenticated requests.
This includes areas such as:
- logout;
- setup;
- tenant mutations;
- platform mutations where applicable.
Do not disable CSRF globally.
Do not create a second independent CSRF mechanism.
Read:
SpaCsrfIntegrationTests

before changing CSRF behavior.
Rate Limiting
Current rate-limit implementation:
security/RequestRateLimiter.java
security/RequestRateLimitFilter.java

Important protected scopes include:
- login;
- refresh;
- setup;
- public traffic.
Do not create equivalent routes that bypass the existing filter.
Do not remove limits merely to make development or tests easier.
Do not trust forwarded headers as client identity unless trusted proxy behavior
is explicitly configured and understood.
Tenant Activation
Tenant active/inactive state is authorization-sensitive.
An inactive establishment must be prevented from normal tenant use even when
an access token was issued before deactivation.
Authorization state is intentionally reloaded from persistent state.
Do not use JWT claims as the sole source of tenant active state.
Validation
Use Jakarta Bean Validation for external request input.
Do not rely only on frontend validation.
Preserve length and format limits for fields such as:
- username;
- password;
- email;
- establishment name;
- product name;
- description;
- category;
- image URL;
- phone;
- social URLs;
- other establishment metadata.
Avoid accepting unbounded strings.
Frontend validation improves UX.
Backend validation remains authoritative.
Monetary Values
Product prices use:
BigDecimal

Never replace monetary values with:
float
double

Preserve database precision and scale.
JPA
The project intentionally uses:
spring.jpa.open-in-view=false
spring.jpa.hibernate.ddl-auto=validate

Do not change these settings to hide persistence problems.
Do not enable Open Session in View to solve lazy-loading issues.
Fetch required information explicitly.
Watch for N+1 queries when changing relationships or DTO mapping.
Entities
Entities live in:
src/main/java/com/wilgner/cardapio/model/entity/

Current entities include:
CardapioTema
Estabelecimento
Produto
RefreshSession
Role
Usuario

Estabelecimento
File:
model/entity/Estabelecimento.java

Important domain concepts include:
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
- product image-shape configuration;
- active state;
- timestamps.
The public menu uses the establishment:
slug

Do not use internal database IDs in public menu URLs when the existing slug
model already applies.
Produto
File:
model/entity/Produto.java

Important concepts include:
- name;
- description;
- BigDecimal price;
- active state;
- category;
- category order;
- product order;
- image URL;
- establishment ownership;
- associated user.
Product order and category order are separate.
Usuario
File:
model/entity/Usuario.java

Important relationships include:
- establishment;
- roles;
- refresh sessions.
Never expose encoded passwords in API responses.
Role
File:
model/entity/Role.java

Current supported application roles are:
ROLE_PLATFORM_ADMIN
ROLE_TENANT_ADMIN

RefreshSession
File:
model/entity/RefreshSession.java

Persists server-side refresh-session state.
Do not remove persistent refresh state in favor of purely stateless JWT refresh
behavior.
DTOs
DTOs live under:
src/main/java/com/wilgner/cardapio/model/dto/

Current groups include:
auth/
error/
estabelecimento/
product/

Important mapping classes include:
EstabelecimentoMapper
ProdutoMapper

Controllers should communicate through DTOs rather than JPA entities.
Use existing DTOs/mappers before creating parallel versions.
Repositories
Repositories live in:
src/main/java/com/wilgner/cardapio/repository/

Current repositories:
EstabelecimentoRepository
ProdutoRepository
RefreshSessionRepository
RoleRepository
UsuarioRepository

Before adding another repository method:
1. search for an existing equivalent query;
2. determine whether tenant scoping is required;
3. determine whether a lock is required;
4. preserve expected ordering;
5. verify relevant tests.
Do not use generic entity lookup when tenant ownership is part of
authorization.
Transactions and Locks
Existing locks are intentional.
Important concurrent flows include:
- product ordering;
- category ordering;
- storage quota;
- image lifecycle;
- setup/bootstrap;
- refresh-token rotation.
Do not remove:
- transaction boundaries;
- pessimistic locks;
- FOR UPDATE;
- tenant row locks;
without understanding the associated invariant and tests.
A shorter implementation is not necessarily equivalent.
Product Ordering
Product order and category order are independent.
Preserve:
- product position inside a category;
- category position;
- deterministic ordering;
- tenant isolation;
- concurrent update safety.
Before changing ordering behavior, read:
CategoryOrderIntegrationTest
CategoryOrderMigrationTest

and any related product ordering tests.
Storage Architecture
Storage changes require extra care.
Current storage flow includes:
TenantStorageService
StorageQuotaService
StorageImageLifecycle
SupabaseStorageService
ImageUploadProfile

Do not bypass these layers.
The authenticated tenant must come from trusted server-side context.
TenantStorageService
File:
service/TenantStorageService.java

Coordinates authenticated tenant uploads.
Conceptual flow:
authenticated tenant
        ↓
backend image validation
        ↓
storage key generation
        ↓
persistent quota reservation
        ↓
remote Supabase upload
        ↓
persistent storage state update

Do not create a second upload flow.
ImageUploadProfile
File:
service/ImageUploadProfile.java

Defines upload/profile constraints used by the existing storage flow.
Reuse this behavior rather than hardcoding independent limits in controllers.
StorageQuotaService
File:
service/StorageQuotaService.java

Quota accounting is persistent.
Existing state concepts include:
RESERVED
STORED
UNCERTAIN
DELETED
LEGACY

These states represent real failure scenarios.
Do not simplify them into a boolean.
Do not release quota simply because:
- a controller request failed;
- a database reference disappeared;
- the remote response timed out.
Remote state may be ambiguous.
Existing tenants that require storage inventory reconciliation must not silently
bypass the existing storage rules.
Remote Upload Failures
There is no distributed transaction between:
PostgreSQL

and:
Supabase Storage

A timeout or connection failure may happen after the provider already accepted
the object.
Preserve conservative accounting.
Do not mark storage as free merely because the client received an error.
Before changing this behavior, read:
StorageQuotaIntegrationTest
TenantStorageFailureTest

StorageImageLifecycle
File:
service/StorageImageLifecycle.java

Responsible for image lifecycle operations such as:
- unused image cleanup;
- replacement tracking;
- safe remote deletion;
- durable deletion queue;
- retry after failures/restarts;
- shared-reference protection;
- external-image protection.
Important rule:
Do not delete the previous image before the new database reference has
successfully committed.
Do not delete an object still referenced elsewhere.
Do not treat arbitrary external URLs as managed Supabase objects.
SupabaseStorageService
File:
service/SupabaseStorageService.java

This is the low-level Supabase Storage integration.
Current responsibilities include:
- backend file validation;
- managed object key generation/parsing;
- public URL generation;
- object upload;
- object deletion;
- object download;
- Supabase endpoint validation;
- bucket validation;
- redirect rejection;
- timeout behavior;
- sanitized provider errors.
New business code should not communicate directly with Supabase.
Go through the existing storage services.
Supabase WebClient Security
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
Do not put raw provider response bodies or credentials into exceptions or logs.
Upload Validation
Backend validation is authoritative.
Preserve validation of:
- maximum size;
- MIME type;
- magic bytes;
- allowed image format;
- application-controlled object names.
Do not trust filename extensions.
Do not use raw uploaded filenames as storage object identity.
Frontend optimization or validation does not replace backend validation.
Database Configuration
Backend resource configuration lives under:
src/main/resources/

Current configuration files include:
application.properties
application-dev.properties
application-local-postgres.properties
application-prod.properties
application-supabase.properties

Production currently uses:
SPRING_PROFILES_ACTIVE=supabase

Important production configuration comes from environment variables.
Examples include:
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

Do not introduce production fallback credentials.
Required security-sensitive production configuration should fail closed when
missing.
Production Logging
Production/Supabase profiles intentionally avoid sensitive noisy logging.
Do not enable by default:
- SQL value logging;
- Hibernate bind-value logging;
- WebClient wire logging;
- raw HTTP body logging;
- authentication cookie logging;
- token logging.
Production debugging must not expose secrets.
Flyway
All production schema evolution goes through Flyway.
Migration directory:
src/main/resources/db/migration/

Current migration history in this repository includes:
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

Migration history is immutable.
NEVER edit an already released migration.
Do not renumber migrations.
Do not recreate missing historical version numbers merely to make numbering
continuous.
New schema changes require a new migration with the next appropriate version.
Migration changes must preserve production data.
Avoid destructive operations unless explicitly required and carefully planned.
Add migration tests for non-trivial data transformations.
Historical Seed Data
Historical migrations may contain old demonstration/setup data.
Do not rewrite historical migrations to clean them up.
For example, insecure historical credentials are handled by later migrations
rather than by modifying the original historical migration.
Changing an already executed migration can break Flyway checksum compatibility
with production databases.
Exception Handling
Exception package:
src/main/java/com/wilgner/cardapio/exception/

Current exceptions include:
ApiExceptionHandler
BusinessException
ConflictException
ExternalServiceException
ResourceNotFoundException
StorageQuotaException
StorageWriteRejectedException

Use the centralized exception handler.
Add a new domain exception only when an existing one does not represent the
condition cleanly.
Do not create exceptions simply to rename ordinary validation failures.
API Errors
Preserve the existing centralized:
ApiErrorDTO

error response behavior.
Use appropriate HTTP status codes.
Do not return:
200 OK

for application errors merely to simplify frontend handling.
Do not expose:
- stack traces;
- SQL;
- database internals;
- raw Supabase provider responses;
- credentials;
- internal security details.
Logging
Security/privacy logging has intentional safeguards.
Reuse the existing:
LogSanitizer

where applicable.
Security-sensitive audit behavior uses:
SECURITY_AUDIT

Do not log sensitive information.
Avoid logging:
- passwords;
- emails when unnecessary;
- authentication tokens;
- JWTs;
- cookies;
- Authorization headers;
- database passwords;
- Supabase keys;
- raw request URLs containing secrets;
- raw SQL values;
- full exception content that may expose sensitive provider data.
Prefer internal IDs over unnecessary personal identifiers when logging.
Health Endpoints
Public health information must remain minimal.
The production public health route is intended for simple liveness/status
checking.
Administrative health information must remain restricted.
Do not expose:
- environment configuration;
- secrets;
- database credentials;
- full actuator details;
- sensitive system internals.
Do not expose all Actuator endpoints publicly.
CORS
Production browser origin is configured through:
CORS_ALLOWED_ORIGINS

Current production frontend uses:
https://lunemenu.app

Credentialed CORS is required because authentication uses cookies.
Do not replace the configured allowlist with:
*

when credentials are involved.
Do not weaken CORS merely to hide an incorrect frontend API configuration.
Public API vs Administrative API
Keep these responsibilities distinct.
Public:
/public/**

Tenant administration:
/painel/**

Platform administration:
/plataforma/**

Authentication:
/auth/**

Exceptional bootstrap:
/setup/**

Do not place authenticated tenant behavior under public routes.
Do not expose platform operations under tenant routes.
Backend Test Map
Backend tests live under:
src/test/java/

Important suites currently include:
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

Security tests are part of the security model.
Do not weaken tests merely to make a new implementation pass.
Which Tests to Read First
When changing authentication:
SecurityIntegrationTests
RefreshSessionIntegrationTest
RefreshSessionRestartTest

When changing CSRF:
SpaCsrfIntegrationTests

When changing tenant activation:
InactiveEstablishmentSecurityIntegrationTest

When changing setup/bootstrap:
SetupSecurityIntegrationTest
BootstrapAdminRunnerIntegrationTest

When changing rate limiting:
RequestRateLimitIntegrationTest
RequestRateLimiterTest

When changing storage/quota:
StorageQuotaIntegrationTest
TenantStorageFailureTest
SupabaseStorageServiceTest
StorageRedirectTest

When changing image replacement:
ImageReplacementIntegrationTest
HeroImageIntegrationTest

When changing category ordering:
CategoryOrderIntegrationTest
CategoryOrderMigrationTest

When changing platform administration:
PlatformDashboardIntegrationTest

Running Backend Tests
Full backend suite from repository root:
./mvnw test

On Windows PowerShell/cmd:
mvnw.cmd test

When Maven is already installed and correctly configured:
mvn -B test

Run focused tests during development when useful.
Before completing a security-sensitive or persistence-sensitive change, run the
relevant integration tests and preferably the complete backend suite.
Never claim tests passed unless they were actually executed successfully.
Common Backend Change Map
Use this map before creating a new class.
Login / refresh / logout / session
→ controller/AuthenticationController.java

JWT creation and validation
→ security/TokenService.java

Authentication cookies
→ security/TokenCookieService.java

Refresh session persistence/rotation
→ security/RefreshSessionService.java

Access-token filter
→ security/SecurityFilter.java

Authorization / CORS / CSRF
→ config/SecurityConfig.java

CSRF SPA handling
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

Authenticated tenant upload endpoint
→ controller/SupabaseStorageController.java

Public managed image delivery
→ controller/PublicStorageController.java

Upload orchestration
→ service/TenantStorageService.java

Storage quota/inventory
→ service/StorageQuotaService.java

Image replacement/deletion lifecycle
→ service/StorageImageLifecycle.java

Low-level Supabase Storage communication
→ service/SupabaseStorageService.java

Authenticated user loading
→ service/CustomUserDetailsService.java

Authenticated domain context
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

If the requested behavior already belongs to one of these paths, extend that
path rather than creating a parallel implementation.
Final Backend Review
Before finishing a backend change, verify:
- Did I read the root AGENTS.md?
- Did I search for an existing implementation?
- Is tenant identity derived from authenticated context?
- Is cross-tenant access still impossible?
- Is platform/tenant role separation intact?
- Are protected routes still protected?
- Are JWTs still hidden from JSON/browser JavaScript?
- Are authentication cookies still HttpOnly?
- Is CSRF still enforced?
- Is refresh replay protection intact?
- Can inactive tenants still be blocked?
- Is rate limiting preserved?
- Are DTO validation limits preserved?
- Are monetary values still BigDecimal?
- Is open-in-view still disabled?
- Is schema mode still validate?
- Did I avoid modifying historical Flyway migrations?
- Are transaction and locking guarantees preserved?
- Is product/category ordering still deterministic?
- Did I use the existing storage architecture?
- Can storage quota still not be bypassed?
- Can image cleanup still not delete another tenant's object?
- Are remote storage failures handled conservatively?
- Are sensitive values absent from logs and API errors?
- Is PostgreSQL compatibility preserved?
- Are relevant tests passing?
If any answer is uncertain, investigate before finishing.
Backend Principle
Security mechanisms in this repository are intentional application behavior,
not disposable framework boilerplate.
Tenant isolation, authentication, refresh rotation, CSRF, rate limiting,
database constraints, locking and storage lifecycle rules are production
invariants.
Do not remove complexity before understanding why it exists.
Investigate first.
Reuse existing paths.
Preserve invariants.
Then make the smallest safe change.

Esse agora fica **exclusivamente focado no backend dentro de `src`**: Controllers → Services → Security → JPA → Flyway → Storage → testes. Ele também corrige algumas partes do arquivo anterior que estavam com palavras grudadas, como `tenantestablishment`, `testsdev`, `modeldto`, `authenticationsecurity` etc.

O próximo, quando você mandar, fazemos **somente o `frontend/AGENTS.md`**.