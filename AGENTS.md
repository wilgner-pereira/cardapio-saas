# AGENTS.md

## Project

This repository is a real production SaaS for digital restaurant menus.

This is not a tutorial, prototype, demo, or disposable project.

Current stack:

- Java 21
- Spring Boot 3.3
- Spring Security
- Spring Data JPA
- Flyway
- PostgreSQL in production
- H2 in PostgreSQL compatibility mode for development/tests
- React 18
- Vite
- Vitest + React Testing Library
- Supabase Storage
- Docker
- GitHub Actions + CodeQL

Production code may handle real users, authentication, tenant data, images and business data.

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

## Source of truth

Before changing behavior, inspect the existing implementation and tests.

Use this priority when documentation conflicts:

1. Current code and automated tests
2. `STATUS_SEGURANCA_ATUALIZADO.md`
3. `README.md`
4. `OPERACAO_STORAGE_E_LOGS.md`
5. Recent correction reports
6. `AUDITORIA_SEGURANCA_LGPD.md`

`AUDITORIA_SEGURANCA_LGPD.md` is historical.

Many findings from that audit have already been fixed.

DO NOT reimplement old audit recommendations blindly.

Always verify the current code and current status before changing security behavior.

---

## Mandatory workflow

Before implementing a task:

1. Understand the requested behavior.
2. Search the repository for an existing implementation.
3. Read the directly related code.
4. Read relevant tests.
5. Identify security, tenant and persistence implications.
6. Implement the smallest coherent change.
7. Run the relevant tests.
8. Remove temporary/debug code.
9. Review the diff for unrelated changes.

Never create code before checking whether equivalent code already exists.

---

## Reuse before creation

Before creating any new:

- component;
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

search for an existing implementation first.

Prefer:

1. reuse;
2. small extension;
3. small refactor;

before creating a parallel implementation.

Do not create `NewX`, `X2`, `CustomX`, `XHelper` or similar alternatives merely to avoid understanding the existing implementation.

At the same time, do not create complex abstractions merely to remove a few duplicated lines.

DRY applies primarily to duplicated behavior and business rules.

---

## Keep changes focused

Modify only what is necessary for the task.

Do not:

- redesign unrelated code;
- reformat unrelated files;
- rename unrelated classes;
- replace working libraries;
- introduce a new architecture during a feature task;
- perform large refactors together with small fixes.

If a task requires three files, do not touch fifteen without a concrete reason.

---

## Existing architecture wins

Prefer the pattern already used by the repository over personal preference.

Do not introduce a replacement technology when the current stack solves the problem.

Examples:

- do not replace Flyway;
- do not replace Spring Security;
- do not replace the existing React API layer;
- do not add another state-management system without a demonstrated requirement;
- do not add another CSS framework merely for convenience;
- do not add another HTTP client when existing mechanisms are sufficient.

A new dependency requires a concrete benefit.

---

## Production safety

Assume all changes may be deployed to production.

Never:

- hardcode credentials;
- commit secrets;
- expose JWTs;
- log passwords, cookies, Authorization headers or credentials;
- expose stack traces to clients;
- disable security controls to make a feature work;
- trust client-side authorization;
- trust IDs supplied by the frontend as proof of ownership;
- perform destructive database changes casually.

Frontend environment variables beginning with `VITE_` are public.

Never put secrets in them.

---

## Multi-tenancy

Tenant isolation is a security boundary.

A restaurant must never read, modify or delete another restaurant's data.

Tenant identity must come from authenticated server-side context whenever applicable.

Do not trust a tenant ID supplied by the browser.

Cross-tenant access must not be introduced through generic repository methods or direct entity lookup.

Preserve the project's existing tenant-scoped access patterns.

Never weaken tenant isolation for convenience.

---

## Authentication and authorization

The current security architecture is intentional.

Preserve:

- `ROLE_PLATFORM_ADMIN`;
- `ROLE_TENANT_ADMIN`;
- explicit separation between platform and tenant permissions;
- database-backed authorization state;
- HttpOnly authentication cookies;
- access/refresh token separation;
- refresh-session persistence;
- refresh rotation and replay protection;
- CSRF protection;
- setup protection;
- rate limiting.

Being a platform administrator does NOT implicitly grant tenant-panel permissions.

Do not change this behavior accidentally.

---

## Database and Flyway

Flyway migrations are append-only production history.

NEVER edit a migration that may already have been executed.

Create a new migration instead.

Do not renumber existing migrations.

Do not bypass Flyway with manual schema assumptions.

Production uses PostgreSQL.

Tests using H2 must remain PostgreSQL-compatible where possible.

Preserve:

- constraints;
- foreign keys;
- indexes;
- tenant ownership;
- transaction boundaries;
- concurrency guarantees.

Do not introduce `ddl-auto=create` or `update`.

The current expected mode is schema validation.

---

## Concurrency

Do not remove locking or transactional code merely because it appears verbose.

The repository intentionally handles concurrency in areas including:

- category ordering;
- product ordering;
- setup/bootstrap;
- refresh rotation;
- storage quota;
- image lifecycle.

Before changing a lock, transaction or `FOR UPDATE` behavior, read its tests.

---

## Storage

Image storage is not a simple upload helper.

The repository has:

- per-tenant quota;
- persistent storage inventory;
- ownership validation;
- RESERVED/STORED/UNCERTAIN/DELETED/LEGACY states;
- replacement lifecycle;
- durable deletion queue;
- retry after failures;
- Supabase validation;
- remote failure handling.

Do not bypass this flow by calling Supabase directly from new application code.

Never free quota merely because a local database reference was removed.

Remote deletion must follow the existing lifecycle guarantees.

---

## Security documentation

When changing security-sensitive behavior, verify whether these files also require updates:

- `README.md`
- `STATUS_SEGURANCA_ATUALIZADO.md`
- `OPERACAO_STORAGE_E_LOGS.md`

Do not modify historical audit reports merely to make them match the current implementation unless explicitly requested.

---

## Dependencies

Before adding a dependency ask:

1. Is it already solved by Java, Spring, React or the browser?
2. Is there already a dependency in the project that solves it?
3. Does adding this dependency meaningfully reduce complexity?
4. Is it maintained and appropriate for production?

Do not add a dependency to save a few lines of code.

---

## Tests

Tests are part of the contract.

Do not change or delete a test merely because the implementation now fails it.

First determine whether the test represents an intentional invariant.

When fixing a bug, add a regression test when practical.

Run relevant suites after changes.

Backend:

```bash
./mvnw test
```

or in environments already configured with Maven:

```bash
mvn -B test
```

Frontend:

```bash
cd frontend
npm ci
npm test
npm run build
```

Do not claim a test passed unless it was actually executed successfully.

---

## CI

GitHub Actions currently validates:

- backend tests;
- frontend tests;
- frontend production build;
- dependency review;
- CodeQL for Java and JavaScript/TypeScript.

Changes should remain compatible with CI.

Do not weaken CI to make failing code merge.

---

## Git

Do not commit, push, merge or rewrite history unless explicitly requested.

Never use destructive commands such as:

```text
git reset --hard
git clean -fd
git push --force
```

without explicit authorization.

Do not commit `.env` files or secrets.

Prefer focused commits when commits are explicitly requested.

---

## Generated files

Do not manually edit generated dependency lock contents unless dependency changes require regeneration.

Relevant generated/support files include:

- `frontend/package-lock.json`
- Maven wrapper scripts

Update them only through the appropriate package/build tooling.

---

## Code quality

Prefer clear code over clever code.

Avoid:

- unnecessary factories;
- unnecessary interfaces;
- speculative abstractions;
- generic helper classes without domain meaning;
- premature caching;
- premature microservices;
- event-driven infrastructure without a concrete requirement;
- hidden side effects.

Use meaningful domain names.

Keep one source of truth for important business rules.

---

## Final review

Before finishing any implementation, verify:

- Did I search for an existing implementation first?
- Did I introduce duplicate logic?
- Did I add an unnecessary dependency?
- Did I change unrelated files?
- Is tenant isolation preserved?
- Is authorization preserved?
- Is CSRF/authentication behavior preserved?
- Is database integrity preserved?
- Did I alter migration history?
- Did I weaken concurrency handling?
- Did I bypass storage lifecycle rules?
- Did I expose sensitive information?
- Are relevant tests passing?
- Is the production build still valid?

If any answer is uncertain, investigate before finishing.

---

## Governing principle

Act as a maintainer of a real production SaaS, not as a code generator starting a greenfield project.

Investigate first.

Reuse second.

Change as little as necessary.

Preserve security and data integrity.

Prefer simple, explicit and testable code.