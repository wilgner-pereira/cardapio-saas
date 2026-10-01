# AGENTS.md

## Project

This repository is the production codebase for Lune Menu, a SaaS for digital restaurant menus.

This is not a tutorial, prototype, demo, or disposable project.

Production code may handle:

- real users;
- authentication;
- tenant data;
- restaurant information;
- products;
- images;
- business data;
- platform administration.

Current stack:

- Java 21
- Spring Boot 3.3
- Spring Security
- Spring Data JPA
- Flyway
- PostgreSQL
- H2 in PostgreSQL compatibility mode for development/tests
- React 18
- Vite
- Vitest
- React Testing Library
- Supabase Storage
- Docker
- GitHub Actions
- CodeQL

Priorities:

1. Security
2. Data integrity
3. Correctness
4. Simplicity
5. Reliability
6. Maintainability
7. Performance when measurable

Prefer the smallest safe solution.

Do not overengineer.

---

# Production Architecture

Current production architecture:

```text
Browser
│
├── https://lunemenu.app
│       ↓
│   Cloudflare
│       ↓
│   Cloudflare Pages
│       ↓
│   React / Vite frontend
│
└── https://api.lunemenu.app
        ↓
    Cloudflare
        ↓
    Heroku
        ↓
    Spring Boot API
        ↓
    Supabase
        ├── PostgreSQL
        └── Storage
		
		
		Current production services:
- Main domain: lunemenu.app
- Frontend: Cloudflare Pages
- Frontend production URL: https://lunemenu.app
- Backend: Heroku
- Heroku application: lunemenu-api
- Backend production URL: https://api.lunemenu.app
- Database: Supabase PostgreSQL
- Image storage: Supabase Storage
- DNS / proxy / TLS edge: Cloudflare
- Production Git branch: main
There is currently no Heroku Postgres database.
The Spring Boot backend connects to Supabase PostgreSQL.
Never hardcode production infrastructure credentials in source code.
Repository Structure
High-level repository layout:

/
├── AGENTS.md
├── Procfile
├── Dockerfile
├── docker-compose.yml
├── pom.xml
├── .env.example
├── DASHBOARD_PLATAFORMA.md
├── OTIMIZACAO_IMAGENS.md
│
├── frontend/
│   ├── AGENTS.md
│   ├── package.json
│   ├── vite.config.js
│   ├── csp.mjs
│   └── src/
│
└── src/
    ├── AGENTS.md
    ├── main/
    │   ├── java/com/wilgner/cardapio/
    │   └── resources/
    └── test/
	
	
	
	Instruction scope:
/AGENTS.md
→ global repository rules

/frontend/AGENTS.md
→ frontend-specific architecture and rules

/src/AGENTS.md
→ backend, database and security-specific architecture and rules

When changing code under frontend/, follow:
1. root AGENTS.md;
2. frontend/AGENTS.md.
When changing code under src/, follow:
1. root AGENTS.md;
2. src/AGENTS.md.
The more specific AGENTS file extends the root rules.
It does not replace them.
Source of Truth
Before changing behavior, inspect the current implementation.
Use this priority when information conflicts:
1. Current production code
2. Automated tests
3. Root AGENTS.md
4. Closest scoped AGENTS.md
5. DASHBOARD_PLATAFORMA.md
6. OTIMIZACAO_IMAGENS.md
Code and tests represent the current behavior more reliably than historical documentation.
Do not implement an old recommendation blindly.
Always verify whether the current code already solves the problem.
Main Application Areas
The application has three main user-facing areas.
Public menu
Public restaurant menus use URLs in the form:
https://lunemenu.app/cardapio/{slug}

Examples:
/cardapio/pizzaria-do-joao
/cardapio/restaurante-central

These routes are public and do not require authentication.
Tenant administration
Restaurant/customer administrators authenticate through:
https://lunemenu.app/login

Tenant administrators use:
ROLE_TENANT_ADMIN

Their administrative area is under:
/painel/**

The current main tenant route is:
/painel/produtos

A tenant administrator manages only their own establishment.
Platform administration
Lune platform administrators also authenticate through:
https://lunemenu.app/login

Platform administrators use:
ROLE_PLATFORM_ADMIN

Their application area is under:
/plataforma/**

Current platform routes include:
/plataforma
/plataforma/clientes
/plataforma/clientes/{id}
/plataforma/administradores

A platform administrator does not automatically receive tenant-panel permission.
Keep platform and tenant permissions explicitly separated.
Deployment
Frontend
The frontend is deployed through Cloudflare Pages.
Current build configuration:
Root directory: frontend
Build command: npm run build
Build output directory: dist
Production branch: main

Important production frontend variables currently include:
VITE_API_BASE_URL=https://api.lunemenu.app
VITE_MAX_SOURCE_IMAGE_BYTES=10485760

Variables beginning with:
VITE_

are public browser configuration.
Never put secrets in them.
Do not put any of the following in frontend environment variables:
- database passwords;
- Supabase service keys;
- JWT secrets;
- setup tokens;
- private API keys;
- credentials.
Cloudflare Pages is connected to the repository and production deployments are
associated with the main branch.
Backend
The backend is deployed on Heroku.
The application is built from the root Maven project.
Runtime command is defined by:
Procfile

Current process:
web: java -Dserver.port=$PORT -jar target/cardapio-0.0.1-SNAPSHOT.jar

The backend uses the Heroku-provided:
$PORT

Do not hardcode a production port.
Current backend production URL:
https://api.lunemenu.app

Public health check:
GET https://api.lunemenu.app/actuator/health

The API root:
/

is not a public frontend page.
A 401 Unauthorized response from the backend root does not mean the deployment
is broken.
Do not make protected routes public merely to make the root URL return 200.
Production Configuration
Production configuration comes from environment variables.
Never commit real production values.
Important categories include:
Database
JWT
Cookies
CORS
Supabase
Storage
Bootstrap/setup
Rate limiting

Examples of production variables include:
DATABASE_URL
DATABASE_USERNAME
DATABASE_PASSWORD

SPRING_PROFILES_ACTIVE

JWT_SECRET

CORS_ALLOWED_ORIGINS

COOKIE_SECURE
COOKIE_SAME_SITE

SUPABASE_URL
SUPABASE_KEY
SUPABASE_BUCKET

Current production Spring profile:
SPRING_PROFILES_ACTIVE=supabase

Secrets must remain outside the repository.
.env.example may document variable names and safe example values only.
Mandatory Workflow
Before implementing a task:
1. Understand the requested behavior.
2. Search the repository for an existing implementation.
3. Read the directly related code.
4. Read relevant tests.
5. Identify security implications.
6. Identify tenant-isolation implications.
7. Identify persistence/database implications.
8. Identify storage implications when images are involved.
9. Implement the smallest coherent change.
10. Run the relevant tests.
11. Remove temporary/debug code.
12. Review the diff for unrelated changes.
Never create code before checking whether equivalent behavior already exists.
Reuse Before Creation
Before creating any new:
- component;
- page;
- service;
- controller;
- repository;
- DTO;
- mapper;
- hook;
- API function;
- validator;
- utility;
- exception;
- CSS pattern;
- security mechanism;
- storage mechanism;
search for an existing implementation first.
Prefer:
1. reuse;
2. small extension;
3. small refactor;
before creating a parallel implementation.
Do not create names such as:
NewX
X2
CustomX
XHelper
AlternativeX

merely to avoid understanding the current implementation.
At the same time, do not create complex abstractions only to remove a few
duplicated lines.
DRY applies primarily to duplicated behavior and duplicated business rules.
Keep Changes Focused
Modify only what is necessary for the task.
Do not:
- redesign unrelated code;
- reformat unrelated files;
- rename unrelated classes;
- replace working libraries;
- introduce a new architecture during a feature task;
- perform a large refactor together with a small bug fix;
- modify unrelated tests;
- make opportunistic changes without a concrete reason.
If a task logically requires three files, do not touch fifteen files without a
clear technical reason.
If a broader refactor is genuinely necessary, explain why before implementing it.
Existing Architecture Wins
Prefer the architecture already used by the repository over personal preference.
Do not introduce replacement technologies simply because they are popular.
Examples:
- do not replace Flyway;
- do not replace Spring Security;
- do not replace the existing React API layer;
- do not add another state-management system without a real requirement;
- do not add another CSS framework merely for convenience;
- do not add another HTTP client when the existing one is sufficient;
- do not bypass the existing storage architecture;
- do not introduce a second authentication model.
A new dependency requires a concrete benefit.
Production Safety
Assume every merged change may reach production.
Never:
- hardcode credentials;
- commit secrets;
- expose JWTs;
- expose refresh tokens;
- log passwords;
- log cookies;
- log Authorization headers;
- log credentials;
- expose stack traces to clients;
- expose raw provider responses containing sensitive information;
- disable security controls to make a feature work;
- trust client-side authorization;
- trust IDs supplied by the browser as proof of ownership;
- perform destructive database changes casually;
- expose internal administration routes publicly without authorization.
Frontend environment variables are not secret storage.
Multi-Tenancy
Tenant isolation is a security boundary.
A restaurant must never be able to:
- read another restaurant's private data;
- edit another restaurant's data;
- delete another restaurant's data;
- upload images against another restaurant's quota;
- manipulate another restaurant's product ordering;
- discover protected tenant resources through insecure IDs.
Tenant identity must come from authenticated server-side context whenever
applicable.
Do not trust a tenant or establishment ID supplied by the browser as
authorization.
Cross-tenant access must not be introduced through generic repository methods or
direct entity lookup.
Preserve the existing tenant-scoped access patterns.
Never weaken tenant isolation for convenience.
Authentication and Authorization
The existing authentication/security architecture is intentional.
Preserve:
- ROLE_PLATFORM_ADMIN;
- ROLE_TENANT_ADMIN;
- explicit platform/tenant permission separation;
- database-backed authorization state;
- HttpOnly authentication cookies;
- short-lived access tokens;
- refresh tokens;
- refresh-session persistence;
- refresh rotation;
- replay protection;
- CSRF protection;
- setup/bootstrap protection;
- rate limiting.
Being a platform administrator does not implicitly grant tenant-panel
permissions.
Do not accidentally combine the roles.
Frontend checks are UX only.
The backend remains authoritative for authorization.
Database and Flyway
Production uses PostgreSQL.
Database schema evolution uses Flyway.
Flyway migrations are append-only production history.
NEVER edit a migration that may already have been executed.
Do not:
- rewrite an existing migration;
- renumber migrations;
- remove migrations to make history cleaner;
- bypass Flyway with manual schema assumptions;
- introduce ddl-auto=create;
- introduce ddl-auto=update.
Create a new migration for new schema changes.
The expected Hibernate production behavior is schema validation.
Preserve:
- constraints;
- foreign keys;
- indexes;
- tenant ownership;
- transaction boundaries;
- concurrency guarantees;
- production data.
Tests using H2 should remain PostgreSQL-compatible where practical.
Concurrency
Do not remove locking or transactional behavior merely because it appears
verbose.
The repository intentionally handles concurrent operations in areas such as:
- category ordering;
- product ordering;
- initial setup/bootstrap;
- refresh rotation;
- storage quota;
- image lifecycle.
Before changing:
@Transactional
FOR UPDATE
pessimistic locks
tenant row locks

read the related implementation and tests.
A simpler-looking implementation is not automatically equivalent.
Storage
Image storage is not a simple upload helper.
The project has storage behavior involving:
- per-tenant quota;
- persistent storage inventory;
- tenant ownership validation;
- storage reservation;
- remote upload;
- ambiguous remote failures;
- replacement lifecycle;
- unused upload cleanup;
- durable deletion queue;
- deletion retry;
- shared-reference protection;
- Supabase validation.
Persistent storage states include concepts such as:
RESERVED
STORED
UNCERTAIN
DELETED
LEGACY

Do not bypass the storage flow by communicating directly with Supabase from new
application code.
Never free quota merely because a local database reference was removed.
Remote deletion must follow the existing lifecycle guarantees.
When modifying storage behavior, read the relevant storage code and integration
tests first.
External Services
External services currently include:
- Cloudflare;
- Heroku;
- Supabase;
- GitHub.
Do not couple domain/business logic unnecessarily to a hosting provider.
Infrastructure-specific configuration should stay in deployment/configuration
layers whenever practical.
When handling Supabase errors:
- do not expose credentials;
- do not expose raw provider bodies unnecessarily;
- do not log private headers;
- sanitize failures.
Dependencies
Before adding a dependency, ask:
1. Is the problem already solved by Java, Spring, React or the browser?
2. Is there already a project dependency that solves it?
3. Does the new dependency meaningfully reduce complexity?
4. Is it maintained?
5. Is it appropriate for a production SaaS?
6. Does it introduce security or supply-chain concerns?
Do not add a dependency merely to save a few lines of code.
Tests
Tests are part of the application contract.
Do not change or delete a test merely because the current implementation fails it.
First determine whether the test represents an intentional invariant.
When fixing a bug, add a regression test when practical.
Never claim a test passed unless it was actually executed successfully.
Backend tests
Full backend suite:
./mvnw test

or, when Maven is already configured:
mvn -B test

Run focused tests first when useful, then the broader relevant suite.
Frontend tests
From the frontend directory:
cd frontend
npm ci
npm test
npm run build

Production build success matters because Cloudflare Pages deploys the generated
frontend.
CI
GitHub Actions currently validates areas including:
- backend tests;
- frontend tests;
- frontend production build;
- dependency review;
- CodeQL for Java;
- CodeQL for JavaScript/TypeScript.
Changes must remain compatible with CI.
Do not weaken CI or security checks merely to make a failing implementation
merge.
Git
Do not commit, push, merge or rewrite history unless explicitly requested.
Never use destructive Git commands without explicit authorization.
Examples:
git reset --hard
git clean -fd
git push --force

Do not commit:
.env
production secrets
database passwords
JWT secrets
Supabase service keys
setup tokens

Prefer focused commits when commits are explicitly requested.
Generated Files
Do not manually modify generated dependency-lock content unless a dependency
change requires regeneration.
Examples:
frontend/package-lock.json
Maven wrapper files

Update generated files through the appropriate build/package tooling.
Documentation
Keep documentation consistent with actual implementation.
Current project documentation includes:
AGENTS.md
frontend/AGENTS.md
src/AGENTS.md
DASHBOARD_PLATAFORMA.md
OTIMIZACAO_IMAGENS.md

Do not create documentation describing architecture that does not exist.
When architecture changes materially, update the appropriate AGENTS file or
operational documentation.
General architecture belongs in:
/AGENTS.md

Frontend-specific architecture belongs in:
/frontend/AGENTS.md

Backend/database/security architecture belongs in:
/src/AGENTS.md

Avoid duplicating large amounts of scoped documentation into the root file.
Code Quality
Prefer clear code over clever code.
Avoid:
- unnecessary factories;
- unnecessary interfaces;
- speculative abstractions;
- generic helper classes without domain meaning;
- premature caching;
- premature microservices;
- event-driven infrastructure without a demonstrated requirement;
- hidden side effects;
- parallel implementations of the same rule.
Use meaningful domain names.
Keep one source of truth for important business rules.
Prefer explicit behavior over magic.
Final Review
Before finishing any implementation, verify:
- Did I search for an existing implementation first?
- Did I read the closest scoped AGENTS file?
- Did I introduce duplicate logic?
- Did I add an unnecessary dependency?
- Did I change unrelated files?
- Is tenant isolation preserved?
- Is role separation preserved?
- Is authorization preserved?
- Is authentication preserved?
- Is CSRF behavior preserved?
- Is refresh-token behavior preserved?
- Is database integrity preserved?
- Did I alter migration history?
- Did I weaken concurrency handling?
- Did I bypass storage lifecycle rules?
- Did I expose sensitive information?
- Are relevant tests passing?
- Is the production build still valid?
- Could this change safely be deployed to production?
If any answer is uncertain, investigate before finishing.
Governing Principle
Act as a maintainer of a real production SaaS, not as a code generator starting a
greenfield project.
Investigate first.
Reuse existing behavior second.
Change as little as necessary.
Preserve security.
Preserve tenant isolation.
Preserve data integrity.
Prefer simple, explicit and testable code.
Production safety is more important than implementation convenience.

Esse é **só o geral**. Ele explica o projeto inteiro e as regras que valem para qualquer alteração, sem entrar demais em qual componente React ou qual `Controller` Java faz cada coisa.

Depois fazemos **somente o `frontend/AGENTS.md`**, e por último **somente o `src/AGENTS.md`**.