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
```

Current production services:

- Main domain: `lunemenu.app`
- Frontend: Cloudflare Pages
- Frontend production URL: `https://lunemenu.app`
- Backend: Heroku
- Heroku application: `lunemenu-api`
- Backend production URL: `https://api.lunemenu.app`
- Database: Supabase PostgreSQL
- Image storage: Supabase Storage
- DNS / proxy / TLS edge: Cloudflare
- Production Git branch: `main`

There is currently no Heroku Postgres database.

The Spring Boot backend connects to Supabase PostgreSQL.

Never hardcode production infrastructure credentials in source code.

---

# Repository Structure

High-level repository layout:

```text
/
├── AGENTS.md
├── Procfile
├── Dockerfile
├── docker-compose.yml
├── pom.xml
├── .env.example
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
```

Instruction scope:

```text
/AGENTS.md
→ global repository rules

/frontend/AGENTS.md
→ frontend-specific architecture and rules

/src/AGENTS.md
→ backend, database and security-specific architecture and rules
```

When changing code under `frontend/`, follow:

1. root `AGENTS.md`;
2. `frontend/AGENTS.md`.

When changing code under `src/`, follow:

1. root `AGENTS.md`;
2. `src/AGENTS.md`.

The more specific AGENTS file extends the root rules.

It does not replace them.

---

# Source of Truth

Before changing behavior, inspect the existing implementation and tests.

Use this priority when information conflicts:

1. Current production code
2. Automated tests
3. Root `AGENTS.md`
4. Closest scoped `AGENTS.md`

Code and tests are authoritative over assumptions or historical knowledge.

Do not implement an old recommendation blindly.

Always verify whether the current implementation already solves the problem.

---

# Main Application Areas

The application has three main user-facing areas.

## Public menu

Public restaurant menus use:

```text
https://lunemenu.app/cardapio/{slug}
```

These routes are anonymous.

Example:

```text
https://lunemenu.app/cardapio/pizzaria-do-joao
```

---

## Tenant administration

Restaurant/customer administrators authenticate through:

```text
https://lunemenu.app/login
```

Tenant role:

```text
ROLE_TENANT_ADMIN
```

Tenant administrative routes:

```text
/painel/**
```

Current primary tenant route:

```text
/painel/produtos
```

A tenant administrator must only access their own establishment.

---

## Platform administration

Lune platform administrators authenticate through:

```text
https://lunemenu.app/login
```

Platform role:

```text
ROLE_PLATFORM_ADMIN
```

Platform routes:

```text
/plataforma
/plataforma/clientes
/plataforma/clientes/{id}
/plataforma/administradores
```

Being a platform administrator does not automatically grant tenant-panel permissions.

Keep platform and tenant permissions explicitly separated.

---

# Frontend Deployment

The frontend is deployed through Cloudflare Pages.

Current build configuration:

```text
Root directory: frontend
Build command: npm run build
Build output directory: dist
Production branch: main
```

Important production frontend configuration:

```text
VITE_API_BASE_URL=https://api.lunemenu.app
VITE_MAX_SOURCE_IMAGE_BYTES=10485760
```

Variables beginning with `VITE_` are delivered to the browser.

They are not secret storage.

Never put the following in frontend environment variables:

- database passwords;
- Supabase service keys;
- JWT secrets;
- setup tokens;
- private API keys;
- credentials.

---

# Backend Deployment

The backend is deployed on Heroku.

The Maven application is built from the repository root.

Runtime command is defined by:

```text
Procfile
```

Current process:

```text
web: java -Dserver.port=$PORT -jar target/cardapio-0.0.1-SNAPSHOT.jar
```

Use the Heroku-provided `$PORT`.

Do not hardcode a production port.

Backend production URL:

```text
https://api.lunemenu.app
```

Production health check:

```text
GET https://api.lunemenu.app/actuator/health
```

The API root `/` is not a public frontend page.

A `401 Unauthorized` response from `/` does not mean the deployment is broken.

Do not make protected API routes public merely to make the root URL return 200.

---

# Production Configuration

Production configuration comes from environment variables.

Never commit real production values.

Important configuration includes:

```text
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
```

Current production Spring profile:

```text
SPRING_PROFILES_ACTIVE=supabase
```

`.env.example` may document variable names and safe placeholders.

It must not contain real production secrets.

---

# Mandatory Workflow

Before implementing a task:

1. Understand the requested behavior.
2. Search the repository for an existing implementation.
3. Read directly related code.
4. Read relevant tests.
5. Identify security implications.
6. Identify tenant-isolation implications.
7. Identify persistence implications.
8. Identify storage implications when images are involved.
9. Implement the smallest coherent change.
10. Run relevant tests.
11. Remove temporary/debug code.
12. Review the diff for unrelated changes.

Never create code before checking whether equivalent behavior already exists.

---

# Reuse Before Creation

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

Do not create alternatives such as:

```text
NewX
X2
CustomX
AlternativeX
XHelper
```

merely to avoid understanding the existing implementation.

Do not create complex abstractions solely to remove a few duplicated lines.

DRY applies primarily to duplicated behavior and business rules.

---

# Keep Changes Focused

Modify only what is necessary for the requested task.

Do not:

- redesign unrelated code;
- reformat unrelated files;
- rename unrelated classes;
- replace working libraries;
- introduce a new architecture during a feature task;
- perform large refactors together with small fixes;
- modify unrelated tests;
- make opportunistic changes without a concrete reason.

If a task appears to require touching many unrelated files, investigate why before proceeding.

---

# Existing Architecture Wins

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

---

# Production Safety

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
- expose administrative routes publicly without authorization.

Frontend environment variables are not secret storage.

---

# Multi-Tenancy

Tenant isolation is a security boundary.

A restaurant must never be able to:

- read another restaurant's private data;
- modify another restaurant's data;
- delete another restaurant's data;
- upload images against another restaurant's quota;
- manipulate another restaurant's product/category order;
- access protected resources through guessed IDs.

Tenant identity must come from authenticated server-side context whenever applicable.

Do not trust a tenant or establishment ID supplied by the browser as authorization.

Cross-tenant access must not be introduced through generic repository methods or direct entity lookup.

Preserve the existing tenant-scoped access patterns.

Never weaken tenant isolation for convenience.

---

# Authentication and Authorization

The current security architecture is intentional.

Preserve:

- `ROLE_PLATFORM_ADMIN`;
- `ROLE_TENANT_ADMIN`;
- explicit platform/tenant permission separation;
- database-backed authorization state;
- HttpOnly authentication cookies;
- access/refresh token separation;
- persistent refresh sessions;
- refresh rotation;
- replay protection;
- CSRF protection;
- bootstrap/setup protection;
- rate limiting.

Being a platform administrator does not implicitly grant tenant-panel permissions.

Frontend authorization checks are UX behavior only.

The backend remains authoritative.

---

# Database and Flyway

Production uses PostgreSQL.

Database schema evolution uses Flyway.

Flyway migrations are append-only production history.

Never edit a migration that may already have executed in any environment.

Do not:

- rewrite existing migrations;
- renumber migrations;
- remove migrations merely to make history cleaner;
- bypass Flyway with manual schema assumptions;
- introduce `ddl-auto=create`;
- introduce `ddl-auto=update`.

Create a new migration for schema changes.

Production expects schema validation.

Preserve:

- constraints;
- foreign keys;
- indexes;
- tenant ownership;
- transaction boundaries;
- concurrency guarantees;
- existing production data.

Tests using H2 should remain PostgreSQL-compatible where practical.

---

# Concurrency

Do not remove locking or transactional behavior merely because it appears verbose.

The repository intentionally handles concurrent operations in areas including:

- category ordering;
- product ordering;
- setup/bootstrap;
- refresh rotation;
- storage quota;
- image lifecycle.

Before changing:

```text
@Transactional
FOR UPDATE
pessimistic locks
tenant row locks
```

read the related implementation and tests.

A shorter implementation is not automatically equivalent.

---

# Storage

Image storage is not a simple upload helper.

The application has behavior for:

- per-tenant quota;
- persistent storage inventory;
- ownership validation;
- storage reservation;
- remote upload;
- ambiguous remote failures;
- image replacement;
- unused upload cleanup;
- durable deletion queue;
- deletion retry;
- shared-reference protection;
- Supabase validation.

Storage state concepts include:

```text
RESERVED
STORED
UNCERTAIN
DELETED
LEGACY
```

Do not bypass the storage flow by communicating directly with Supabase from unrelated application code.

Never release quota merely because a local database reference disappeared.

Remote deletion must follow the existing lifecycle guarantees.

---

# External Services

Current external infrastructure includes:

- Cloudflare;
- Heroku;
- Supabase;
- GitHub.

Do not unnecessarily couple business logic to infrastructure providers.

Infrastructure-specific configuration belongs in configuration/deployment layers where practical.

Provider errors must not expose:

- credentials;
- authorization headers;
- private tokens;
- unsafe raw provider responses.

---

# Dependencies

Before adding a dependency, ask:

1. Can Java, Spring, React or the browser already solve this?
2. Is an existing dependency already capable of solving it?
3. Does the new dependency meaningfully reduce complexity?
4. Is it maintained?
5. Is it suitable for a production SaaS?
6. Does it create meaningful supply-chain/security risk?

Do not add dependencies merely to save a few lines of code.

---

# Tests

Tests are part of the application contract.

Do not change or delete a test merely because a new implementation fails it.

Determine whether the test represents an intentional invariant.

When fixing a bug, add a regression test when practical.

Never claim a test passed unless it actually ran successfully.

Backend:

```bash
./mvnw test
```

Windows:

```text
mvnw.cmd test
```

Frontend:

```bash
cd frontend
npm ci
npm test
npm run build
```

---

# CI

GitHub Actions validates production-relevant behavior including:

- backend tests;
- frontend tests;
- frontend production build;
- dependency review;
- CodeQL for Java;
- CodeQL for JavaScript/TypeScript.

Changes must remain compatible with CI.

Do not weaken CI to make a failing implementation pass.

---

# Git

Do not commit, push, merge or rewrite history unless explicitly requested.

Never use destructive commands such as:

```text
git reset --hard
git clean -fd
git push --force
```

without explicit authorization.

Do not commit secrets or `.env` files containing real credentials.

Prefer focused commits when commits are explicitly requested.

---

# Generated Files

Do not manually edit generated dependency lock contents unless dependency changes require regeneration.

Examples:

```text
frontend/package-lock.json
Maven wrapper files
```

Update generated files through the appropriate tooling.

---

# Documentation

Keep documentation consistent with the actual implementation.

Current AGENTS hierarchy:

```text
/AGENTS.md
/frontend/AGENTS.md
/src/AGENTS.md
```

General project rules belong in the root file.

Frontend-specific architecture belongs in `frontend/AGENTS.md`.

Backend/database/security-specific architecture belongs in `src/AGENTS.md`.

Do not duplicate large amounts of scoped documentation into the root file.

---

# Code Quality

Prefer clear code over clever code.

Avoid:

- unnecessary factories;
- unnecessary interfaces;
- speculative abstractions;
- generic helper classes without domain meaning;
- premature caching;
- premature microservices;
- unnecessary event-driven infrastructure;
- hidden side effects;
- parallel implementations of the same rule.

Use meaningful domain names.

Keep one source of truth for important business rules.

Prefer explicit behavior over magic.

---

# Final Review

Before finishing any implementation, verify:

- Did I search for an existing implementation first?
- Did I read the closest scoped `AGENTS.md`?
- Did I introduce duplicate logic?
- Did I add an unnecessary dependency?
- Did I change unrelated files?
- Is tenant isolation preserved?
- Is platform/tenant role separation preserved?
- Is authorization preserved?
- Is authentication preserved?
- Is CSRF behavior preserved?
- Is refresh-session behavior preserved?
- Is database integrity preserved?
- Did I alter historical migration behavior?
- Did I weaken concurrency handling?
- Did I bypass the storage lifecycle?
- Did I expose sensitive information?
- Are relevant tests passing?
- Is the production build valid?
- Could this change safely be deployed?

If any answer is uncertain, investigate before finishing.

---

# Governing Principle

Act as a maintainer of a real production SaaS, not as a code generator starting a greenfield project.

Investigate first.

Reuse existing behavior.

Change as little as necessary.

Preserve security.

Preserve tenant isolation.

Preserve data integrity.

Prefer simple, explicit and testable code.

Production safety is more important than implementation convenience.