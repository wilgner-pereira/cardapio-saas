# Frontend AGENTS.md

This file extends the repository root `AGENTS.md`.

All root rules remain mandatory.

This file governs the frontend application under:

```text
frontend/
```

It covers:

- React application structure;
- routing;
- authentication state;
- API communication;
- public menu UI;
- tenant administration UI;
- platform administration UI;
- image optimization;
- upload validation;
- styling;
- themes;
- accessibility;
- CSP;
- frontend tests;
- production frontend build behavior.

Do not duplicate root-level infrastructure rules here unless they directly
affect frontend implementation.

---

# Frontend Stack

The frontend intentionally uses:

- React 18
- JavaScript / JSX
- Vite
- Vitest
- React Testing Library
- `@testing-library/user-event`
- `lucide-react`
- native `fetch`
- custom CSS

The frontend currently does NOT use:

- TypeScript
- React Router
- Axios
- TanStack Query
- Redux
- Zustand
- Tailwind
- Bootstrap
- Material UI
- shadcn/ui
- CSS-in-JS

Do not introduce any of these merely because they are common choices.

A stack change requires an explicit requirement and concrete architectural
benefit.

Prefer the existing frontend architecture.

---

# Frontend Production

Production frontend:

```text
https://lunemenu.app
```

Hosting:

```text
Cloudflare Pages
```

Current Cloudflare Pages configuration:

```text
Root directory: frontend
Build command: npm run build
Build output: dist
Production branch: main
```

Production backend:

```text
https://api.lunemenu.app
```

Frontend API configuration:

```text
VITE_API_BASE_URL=https://api.lunemenu.app
```

Current source image limit configuration:

```text
VITE_MAX_SOURCE_IMAGE_BYTES=10485760
```

Remember:

```text
VITE_*
```

variables are public browser configuration.

Never put secrets in Vite environment variables.

Do not put any of the following in frontend environment variables:

- database passwords;
- JWT secrets;
- refresh tokens;
- Supabase service keys;
- setup tokens;
- private API keys;
- credentials.

---

# Frontend Structure

Main frontend directory:

```text
frontend/
```

Main source directory:

```text
frontend/src/
```

Important structure:

```text
frontend/
├── AGENTS.md
├── package.json
├── package-lock.json
├── vite.config.js
├── index.html
├── csp.mjs
├── csp.test.js
├── benchmark.mjs
│
└── src/
    ├── main.jsx
    ├── App.jsx
    ├── App.test.jsx
    ├── styles.css
    │
    ├── api/
    ├── auth/
    ├── assets/
    ├── components/
    └── platform/
```

Before creating a new directory or architectural layer, search for the current
place where equivalent behavior lives.

---

# Application Entry Point

React entry point:

```text
src/main.jsx
```

It mounts the React application.

High-level application orchestration:

```text
src/App.jsx
```

`App.jsx` currently owns or coordinates:

- lightweight routing;
- top-level route selection;
- home page;
- login page;
- public menu page;
- tenant administration page;
- top-level navigation between those areas.

Do not move unrelated business logic into `App.jsx`.

---

# Do Not Grow App.jsx Unnecessarily

`App.jsx` is already a large orchestration file.

When adding substantial frontend functionality:

- keep high-level routing/orchestration in `App.jsx`;
- extract a meaningful component when it has a clear responsibility;
- prefer existing component directories when appropriate;
- do not dump another large independent screen into `App.jsx`.

However, do not create tiny components merely to reduce line count.

Extract by responsibility, not by arbitrary file-size targets.

Prefer:

```text
clear responsibility
```

over:

```text
more files
```

---

# Routing Architecture

The frontend intentionally does not use React Router.

Current routing is implemented in:

```text
src/App.jsx
```

using browser primitives such as:

```text
window.location.pathname
window.history.pushState
window.history.replaceState
popstate
parseRoute()
```

Do not introduce React Router merely to implement another simple route.

Follow the existing lightweight routing architecture unless:

1. routing complexity has genuinely outgrown it; or
2. the user explicitly requests a routing migration.

---

# Current Browser Routes

## Home

```text
/
```

Public application landing page.

---

## Login

```text
/login
```

Shared login page for:

```text
ROLE_PLATFORM_ADMIN
ROLE_TENANT_ADMIN
```

The login destination depends on the authenticated user's role.

---

## Tenant administration

Tenant administration is under:

```text
/painel
/painel/*
```

Current primary tenant route:

```text
/painel/produtos
```

Tenant administrators use:

```text
ROLE_TENANT_ADMIN
```

---

## Platform administration

Platform administration routes:

```text
/plataforma
/plataforma/clientes
/plataforma/clientes/{id}
/plataforma/administradores
```

Platform administrators use:

```text
ROLE_PLATFORM_ADMIN
```

---

## Public restaurant menu

Public menus use:

```text
/cardapio/{slug}
```

Example:

```text
/cardapio/pizzaria-do-joao
```

The `slug` identifies the public establishment.

Public menu routes do not require authentication.

---

# Role Routing

Role definitions and routing helpers live in:

```text
src/auth/roles.js
```

Current roles:

```text
ROLE_PLATFORM_ADMIN
ROLE_TENANT_ADMIN
```

Current administrative home resolution:

```text
ROLE_PLATFORM_ADMIN
→ /plataforma
```

```text
ROLE_TENANT_ADMIN
→ /painel/produtos
```

When both roles are explicitly present, the existing role helper determines
the destination.

Do not independently reproduce role-routing logic inside random components.

Use the existing role helpers.

A platform administrator does not automatically receive tenant permissions.

Preserve this separation.

---

# Authentication Architecture

Frontend authentication lives under:

```text
src/auth/
```

Current files:

```text
src/auth/AuthContext.jsx
src/auth/AuthContext.test.jsx
src/auth/ProtectedArea.jsx
src/auth/roles.js
```

---

# AuthContext

Authentication state is owned by:

```text
src/auth/AuthContext.jsx
```

`AuthContext` is the frontend source of authentication state.

The application intentionally reconstructs authentication from backend state.

Do not create a second authentication state system.

Do not create an independent global user store.

---

# ProtectedArea

Protected route behavior lives in:

```text
src/auth/ProtectedArea.jsx
```

It handles authenticated/role-restricted frontend areas.

Frontend authorization is only a UX/navigation layer.

The backend remains authoritative.

Never assume that hiding a frontend component protects a backend operation.

---

# Browser Authentication Storage

Authentication cookies are:

```text
HttpOnly
```

and controlled by the backend.

JavaScript must not attempt to read authentication JWT cookies.

Never store JWTs in:

```text
localStorage
sessionStorage
IndexedDB
React persisted state
```

The application intentionally removes legacy:

```text
cardapio.authUser
```

browser storage.

Do not reintroduce persisted authentication metadata merely to simplify login
state.

---

# API Architecture

All browser/backend application communication must go through:

```text
src/api/client.js
```

This is the central frontend API layer.

Do not scatter raw `fetch()` calls throughout React components.

Do not add Axios.

Do not create another parallel API client.

---

# API Client Responsibilities

`src/api/client.js` currently handles behavior such as:

- API base URL;
- `credentials: "include"`;
- JSON serialization;
- response parsing;
- backend error normalization;
- login;
- logout;
- refresh;
- session validation;
- CSRF initialization;
- session-expiration handling;
- public menu requests;
- tenant administration requests;
- platform administration requests;
- image upload;
- unused-image cleanup;
- image URL resolution.

When a new backend operation is required:

1. inspect `src/api/client.js`;
2. search for an existing related API function;
3. extend the existing `api` object.

Do not implement backend communication directly inside presentation
components.

---

# API Base URL

The API base is resolved from:

```text
VITE_API_BASE_URL
```

Production value:

```text
https://api.lunemenu.app
```

Local development may use the existing fallback behavior.

Do not hardcode the production API URL throughout components.

The API client remains the single source of truth for API base handling.

---

# Credentials

Requests that participate in authentication use:

```javascript
credentials: "include"
```

This is required because authentication uses cookies.

Do not remove credential handling from authenticated requests.

Do not attempt to replace cookie authentication with browser-stored Bearer
tokens.

---

# Authentication API Flow

Current frontend authentication operations include:

```text
POST /auth/login
POST /auth/refresh
POST /auth/logout

GET /auth/session
GET /auth/csrf
```

Authentication flow conceptually works like:

```text
login form
    ↓
api.login()
    ↓
POST /auth/login
    ↓
backend sets HttpOnly cookies
    ↓
frontend stores only current in-memory session information
    ↓
role determines administrative route
```

When the access token expires:

```text
API request
    ↓
401
    ↓
refreshAccessToken()
    ↓
POST /auth/refresh
    ↓
new backend cookies
    ↓
original request retried
```

Preserve the existing refresh behavior.

Do not implement refresh logic separately in components.

---

# CSRF

Cookie-authenticated mutable requests require CSRF protection.

The frontend CSRF implementation is centralized in:

```text
src/api/client.js
```

The client initializes CSRF through:

```text
GET /auth/csrf
```

Mutable operations then send the expected CSRF header.

Do not:

- disable CSRF;
- bypass the central request function;
- invent a second CSRF implementation;
- manually mix CSRF token representations incorrectly;
- manually copy CSRF logic into components.

If authentication or CSRF behavior changes, inspect:

```text
src/api/client.test.js
src/auth/AuthContext.test.jsx
```

before implementation.

---

# Public Menu Architecture

Public menu route:

```text
/cardapio/{slug}
```

High-level orchestration currently starts from:

```text
PublicMenuPage
```

inside:

```text
src/App.jsx
```

The page loads:

```javascript
api.getPublicMenu(slug)
api.getPublicEstablishment(slug)
```

which call:

```text
GET /public/{slug}/cardapio
GET /public/{slug}/cardapio/info
```

No authenticated session is required for the public menu.

Do not unnecessarily trigger authentication validation for anonymous public
menu rendering.

---

# MenuShell

Main shared menu renderer:

```text
src/components/MenuShell.jsx
```

`MenuShell` is a major reusable component.

It is used for the restaurant menu experience and also supports tenant
administrative behavior through admin mode/callbacks.

Before creating another menu renderer, inspect `MenuShell`.

Responsibilities may include:

- establishment/menu presentation;
- product categories;
- product cards;
- category navigation;
- empty/loading/error states;
- public versus admin presentation;
- administrative product actions;
- category/product ordering controls;
- theme behavior;
- integration with other menu UI components.

Do not create a second full menu rendering system unless there is a strong
architectural requirement.

---

# MenuHero

Establishment/menu hero presentation:

```text
src/components/MenuHero.jsx
```

Before creating another establishment header/hero component, inspect this
component.

Changes involving establishment presentation may affect:

- cover image;
- restaurant information;
- social/contact actions;
- visual layout;
- public/admin rendering;
- responsive behavior.

Reuse the existing structure where appropriate.

---

# Scroll-to-Top

Scroll-to-top behavior lives in:

```text
src/components/ScrollToTopButton.jsx
```

Do not duplicate this functionality inside `MenuShell` or another page.

If scroll behavior changes, inspect its interaction with:

- sticky category navigation;
- menu layout;
- mobile viewport;
- scroll thresholds.

---

# Tenant Administration

Tenant administration currently uses:

```text
AdminMenuPage
```

inside:

```text
src/App.jsx
```

It coordinates tenant functionality such as:

- loading products;
- loading establishment information;
- creating products;
- editing products;
- deleting products;
- toggling product status;
- product ordering;
- category ordering;
- establishment editing;
- theme editing;
- image upload orchestration;
- logout.

Presentation is delegated largely to existing reusable components.

Do not duplicate this orchestration in a new tenant page without a concrete
reason.

---

# Product Editor

Product creation/editing UI:

```text
src/components/ProductEditorModal.jsx
```

Related tests:

```text
src/components/ProductEditorModal.test.jsx
```

Product persistence must continue through:

```text
src/api/client.js
```

Backend resource:

```text
/painel/produtos
```

The product editor must not:

- communicate directly with Supabase;
- construct its own authentication logic;
- create its own fetch wrapper;
- duplicate global upload validation.

---

# Establishment Editor

Establishment editing UI:

```text
src/components/EstablishmentEditorModal.jsx
```

Tests:

```text
src/components/EstablishmentEditorModal.test.jsx
```

Backend resource:

```text
/painel/estabelecimento
```

This component handles editable establishment data.

Use the existing API client and image workflow.

Do not create separate save logic directly against the backend.

---

# Theme Picker

Menu appearance/theme UI:

```text
src/components/ThemePickerModal.jsx
```

Tests:

```text
src/components/ThemePickerModal.test.jsx
```

Theme configuration is persisted by the backend.

The frontend is not the sole source of truth for available theme behavior.

Changes to themes may affect:

```text
src/styles.css
src/components/ThemePickerModal.jsx
backend establishment validation
database constraints
Flyway migrations
tests
```

Inspect all relevant layers before changing theme contracts.

---

# Platform Administration

Platform administration UI lives under:

```text
src/platform/
```

Current files include:

```text
CreateAccountModal.jsx
PlatformClientDetails.jsx
PlatformClients.jsx
PlatformDashboard.jsx
PlatformLayout.jsx
PlatformModal.jsx
PlatformPage.jsx
```

Tests include:

```text
PlatformModal.test.jsx
PlatformPage.test.jsx
```

---

# PlatformPage

Main platform administration orchestration:

```text
src/platform/PlatformPage.jsx
```

This component coordinates:

- platform customer list loading;
- customer details;
- customer creation;
- activation;
- deactivation;
- deletion;
- platform administrator creation;
- notifications;
- modal state;
- logout;
- navigation between platform sections.

Do not move platform behavior into tenant administration components.

---

# PlatformLayout

Platform navigation/layout:

```text
src/platform/PlatformLayout.jsx
```

Use it for platform-level layout consistency.

Do not create a second independent platform shell for a new platform screen
without checking whether `PlatformLayout` can support it.

---

# PlatformDashboard

Platform overview/dashboard:

```text
src/platform/PlatformDashboard.jsx
```

Changes to platform metrics or summary presentation should normally start by
inspecting this component and the existing platform API.

---

# PlatformClients

Customer list:

```text
src/platform/PlatformClients.jsx
```

Responsible for customer/establishment list presentation and related actions.

---

# PlatformClientDetails

Customer detail view:

```text
src/platform/PlatformClientDetails.jsx
```

Used for an individual establishment under:

```text
/plataforma/clientes/{id}
```

Do not duplicate client-detail rendering in another component unless the
responsibility is genuinely different.

---

# CreateAccountModal

Account/customer creation modal:

```text
src/platform/CreateAccountModal.jsx
```

It currently supports platform workflows such as:

- customer creation;
- platform administrator creation.

Preserve role separation.

Do not expose platform account creation behavior to tenant routes.

---

# PlatformModal

Reusable platform confirmation modal:

```text
src/platform/PlatformModal.jsx
```

Test:

```text
src/platform/PlatformModal.test.jsx
```

Before creating another platform confirmation dialog, inspect this component.

---

# Existing Reusable Components

Current important components include:

```text
src/components/EstablishmentEditorModal.jsx
src/components/MenuHero.jsx
src/components/MenuShell.jsx
src/components/ProductEditorModal.jsx
src/components/ScrollToTopButton.jsx
src/components/ThemePickerModal.jsx
src/components/icons.js
src/components/useOptimizedImage.js
```

Before creating UI, search these components for existing patterns involving:

- modals;
- close buttons;
- forms;
- error states;
- loading states;
- empty states;
- image previews;
- product cards;
- hero content;
- social/contact actions;
- categories;
- navigation;
- icons;
- theme controls;
- image processing.

Prefer reuse over parallel implementation.

---

# Modals

The frontend already has established modal behavior.

Before creating another modal, inspect:

```text
ProductEditorModal.jsx
EstablishmentEditorModal.jsx
ThemePickerModal.jsx
PlatformModal.jsx
CreateAccountModal.jsx
```

Preserve existing modal conventions such as:

- accessible title/labels;
- Escape handling where applicable;
- backdrop dismissal where applicable;
- inside-click behavior;
- focus visibility;
- loading state;
- submit-disabled behavior;
- error announcements;
- close behavior.

Do not create another modal framework for a single feature.

---

# Images

Image-related frontend behavior is split into several focused files.

Current API/image files include:

```text
src/api/imageOptimization.js
src/api/imageOptimization.worker.js
src/api/imageProcessing.js
src/api/uploadValidation.js
```

Related component utility:

```text
src/components/useOptimizedImage.js
```

Tests include:

```text
src/api/imageOptimization.test.js
src/components/UploadValidation.test.jsx
```

Do not bypass this architecture.

---

# Image Upload Flow

Expected image flow:

```text
user selects image
        ↓
frontend source validation
        ↓
image processing / optimization
        ↓
WebP output
        ↓
api.uploadImage()
        ↓
POST /painel/storage/upload
        ↓
backend validation
        ↓
backend storage lifecycle
        ↓
Supabase Storage
```

The browser does NOT upload directly to Supabase.

Keep Supabase credentials out of the frontend.

---

# Upload Validation

Centralized upload validation:

```text
src/api/uploadValidation.js
```

Reuse existing exported validation behavior.

Do not duplicate:

- MIME checks;
- source size checks;
- accepted types;
- maximum-size configuration;

inside individual components.

Frontend validation improves UX.

The backend remains authoritative.

---

# Image Optimization

Image optimization lives in:

```text
src/api/imageOptimization.js
src/api/imageOptimization.worker.js
src/api/imageProcessing.js
```

Do not reimplement image conversion separately inside modal components.

Keep expensive image processing outside normal React rendering paths.

Preserve the worker-based architecture when applicable.

---

# Optimized Image Hook

Existing image optimization integration helper:

```text
src/components/useOptimizedImage.js
```

Before writing another image-processing hook, inspect this file.

Reuse existing behavior when it matches the use case.

---

# Failed Save Compensation

The application already handles the case where:

```text
new image upload succeeds
        ↓
entity save fails
```

The newly uploaded but unused object is then eligible for cleanup through the
existing backend unused-image flow.

Preserve this behavior.

Do not leave newly uploaded images orphaned merely because the later entity
save failed.

Do not delete the previous saved image from frontend code.

The backend storage lifecycle owns safe replacement/deletion semantics.

---

# Image URL Resolution

Image URL resolution is centralized in:

```text
src/api/client.js
```

Existing helper:

```text
resolveImageUrl()
```

Managed storage URLs may be translated to controlled backend paths such as:

```text
/public/storage/{fileName}
```

Do not create independent image URL parsing/rewrite logic inside components.

Do not trust arbitrary JavaScript URLs or unsafe image schemes.

---

# API Error Handling

The API client normalizes backend failures.

React components should generally consume the resulting error rather than
reimplementing backend response parsing.

Do not repeatedly write code that independently parses:

```text
response.ok
content-type
JSON error body
401 refresh behavior
CSRF behavior
```

That belongs in the API layer.

UI components should focus on displaying appropriate user-facing state.

---

# Loading, Error and Empty States

When a feature loads remote data, consider all relevant states:

```text
loading
success
empty
error
retry
```

Follow existing application patterns.

Do not leave a screen blank on network errors.

Use existing error styling and accessibility conventions.

For relevant failures, use:

```text
role="alert"
```

where established.

---

# Optimistic UI

Some tenant product operations currently use optimistic UI behavior.

Before changing these flows, inspect the existing implementation in:

```text
src/App.jsx
```

Optimistic changes must include rollback behavior when persistence fails.

Do not:

- permanently mutate UI state before knowing how failure is handled;
- lose the previous saved state;
- leave `_pending` state stuck;
- allow conflicting reorder actions while another ordering mutation is active.

Preserve current rollback behavior.

---

# Product Ordering

Frontend product ordering reflects persisted backend ordering.

Do not treat UI order as frontend-only state.

Changes to ordering must continue through the backend.

Product order and category order are separate concepts.

Do not collapse them in frontend logic.

---

# Category Ordering

Category ordering uses backend persistence.

The UI should not merely reorder categories locally and assume the change is
saved.

Preserve:

- backend mutation;
- returned persisted ranks;
- deterministic rendering;
- failure recovery/reload behavior;
- temporary disabling during conflicting operations.

---

# Styling

The application uses custom CSS.

Primary stylesheet:

```text
src/styles.css
```

Do not introduce another styling system for isolated features.

Before writing new CSS:

1. search for an existing selector;
2. search for an existing semantic token;
3. inspect the visually closest existing component;
4. reuse or extend the established pattern.

---

# Theme Tokens

The stylesheet already contains semantic theme variables.

Prefer existing tokens such as:

```text
--theme-page-bg
--theme-surface
--theme-text
--theme-text-muted
--theme-heading
--theme-primary
--theme-accent
--theme-border
--theme-focus-ring
--theme-danger
```

Do not introduce arbitrary hardcoded colors when an appropriate semantic token
already exists.

Theme-specific visual differences should use the existing theme architecture.

---

# Visual Consistency

Consistency is more important than novelty.

New UI should look like part of the existing Lune Menu application.

Before implementing a new screen or component, inspect the closest existing
screen for:

- spacing;
- typography;
- border radius;
- card style;
- shadows;
- button hierarchy;
- form fields;
- modal style;
- loading state;
- empty state;
- error state;
- responsive behavior.

Do not create a different design language for each feature.

---

# Static vs Dynamic Styling

Prefer CSS classes for static styling.

Inline style objects are acceptable when values are genuinely dynamic and the
existing design requires them.

Do not move static CSS into large JSX style objects.

Do not use inline styles as a shortcut to avoid understanding the current
stylesheet.

---

# CSS Hacks

Avoid these as first-line layout fixes:

```text
!important
arbitrary z-index
negative margins
fixed pixel positioning
unnecessary absolute positioning
transform hacks
viewport-specific magic numbers
```

Find the structural layout cause first.

Use exceptions only when they are justified by the actual layout behavior.

---

# Responsive Design

All changes must remain usable on:

- desktop;
- tablet;
- mobile.

Check especially:

- public menu;
- menu hero;
- category navigation;
- product cards;
- administrative controls;
- platform sidebar/layout;
- modals;
- forms;
- floating buttons.

Avoid accidental horizontal scrolling.

Avoid fixed widths when responsive behavior is more appropriate.

Do not assume desktop-only usage.

Restaurant customers are likely to use mobile devices.

---

# Accessibility

Accessibility behavior is part of the frontend contract.

Use semantic HTML.

Use:

```html
<button>
```

for actions.

Use navigation/link semantics appropriately.

Preserve:

- keyboard navigation;
- visible focus;
- modal labels;
- form labels;
- `aria-*` attributes;
- `role="alert"` for relevant errors;
- loading feedback;
- Escape behavior where established;
- backdrop behavior where established.

Do not remove focus outlines without providing an accessible replacement.

Do not rely only on color to communicate important state.

---

# Icons

Use the existing icon abstraction:

```text
src/components/icons.js
```

It is based on:

```text
lucide-react
```

Do not:

- install another icon library;
- paste unrelated SVG implementations;
- mix visual icon families unnecessarily.

If a required Lucide icon is not currently exported, extend the existing icon
module.

---

# State Management

Keep state local unless it is genuinely shared.

Use:

```text
AuthContext
```

for authentication.

Do not add Redux, Zustand or another global state system merely to avoid
passing a small number of props.

Avoid duplicated sources of truth.

Derived values should generally be calculated rather than mirrored into
another state variable.

---

# React Hooks

Do not use hooks by reflex.

Especially avoid unnecessary:

```text
useEffect
useMemo
useCallback
```

Use them when their semantics are actually required.

Do not use `useEffect` merely to derive values that can be calculated during
render.

Do not memoize trivial expressions without a measured reason.

Do not create dependency-array hacks to silence lint/runtime issues.

Understand the data flow first.

---

# Effects and Async Requests

Effects that perform asynchronous work should account for component lifecycle.

Preserve established patterns that avoid updating unmounted/inactive
components.

Do not create repeated network requests because of unstable dependencies.

When React StrictMode is relevant to a test or development behavior, ensure
requests do not accidentally duplicate destructive actions.

---

# Menu Performance

`MenuShell` processes products/categories during rendering.

Avoid introducing repeated expensive transformations over the same collection
when one clear pass would work.

However, do not prematurely add complicated memoization for tiny menu arrays.

Prefer:

```text
clear data flow
```

before:

```text
complex performance optimization
```

Measure when optimization complexity is significant.

---

# External Content

Do not add external:

- scripts;
- fonts;
- analytics;
- trackers;
- widgets;
- embeds;

casually.

External resources affect:

- CSP;
- privacy;
- availability;
- performance;
- production security.

There is currently no reason to add a third-party tracking dependency unless
explicitly requested.

---

# Content Security Policy

Production CSP is generated by:

```text
frontend/csp.mjs
```

CSP tests:

```text
frontend/csp.test.js
```

The CSP is integrated into the production Vite build.

Do not weaken CSP merely to make a feature work.

Never add broad shortcuts such as:

```text
script-src *
connect-src *
'unsafe-eval'
script-src 'unsafe-inline'
```

unless there is an extraordinary, explicitly reviewed reason.

If a legitimate new external origin is required:

1. understand exactly why it is needed;
2. add only the narrow required origin;
3. update `csp.mjs`;
4. update `csp.test.js`;
5. run frontend tests;
6. run the production build.

Production host headers must enforce directives that cannot be implemented via
HTML alone.

---

# Cloudflare and CSP

The production frontend is hosted by Cloudflare Pages.

Production security headers generated by the build may be consumed through
Cloudflare Pages output.

Do not fix a CSP violation by globally allowing an external provider.

First determine:

1. whether that external script/resource is actually required;
2. whether it was intentionally added;
3. whether it can be removed;
4. whether a narrow allowlist change is appropriate.

Cloudflare-injected or optional analytics resources should not automatically
cause application CSP to be weakened.

---

# Forms

Forms should:

- use labels;
- preserve browser autocomplete semantics where appropriate;
- disable submission during an active mutation when duplicate submission is
  unsafe;
- surface validation errors clearly;
- preserve entered data after recoverable errors when appropriate.

Do not rely exclusively on placeholders as labels.

Backend validation remains authoritative.

---

# Login UI

The login page is currently orchestrated from:

```text
src/App.jsx
```

It is shared by both administrative role types.

Branding currently uses Lune Systems.

Do not create separate login systems for:

```text
ROLE_PLATFORM_ADMIN
ROLE_TENANT_ADMIN
```

unless product requirements explicitly change.

The backend determines valid credentials and role membership.

---

# Public Menu vs Admin Menu

The application intentionally reuses menu presentation behavior.

Public mode should expose only customer-facing functionality.

Admin mode may expose editing controls.

Do not accidentally expose:

- edit buttons;
- delete actions;
- reorder controls;
- management state;

on anonymous public menu routes.

Likewise, do not fork the entire menu component merely to add one admin
control when the existing mode architecture can support it safely.

---

# Social and Contact Actions

Establishment contact/social information is part of establishment data.

When modifying social/contact UI:

- reuse existing establishment data;
- preserve safe URL handling;
- preserve responsive behavior;
- do not inject HTML from establishment content;
- do not allow arbitrary script schemes;
- keep public and admin presentation consistent.

Changes may involve:

```text
MenuHero.jsx
MenuShell.jsx
EstablishmentEditorModal.jsx
styles.css
```

Inspect the existing implementation before deciding which files are required.

---

# Safe Content Rendering

Do not use:

```javascript
dangerouslySetInnerHTML
```

for restaurant/product/user-controlled content.

React text rendering should remain escaped by default.

Do not accept arbitrary:

```text
javascript:
data:
```

URLs merely because the browser can technically render them.

Use existing validation and fallback behavior.

---

# Platform/Tenant Separation

Keep platform and tenant frontend concepts separate.

Platform:

```text
src/platform/
ROLE_PLATFORM_ADMIN
/plataforma/**
```

Tenant:

```text
AdminMenuPage
ROLE_TENANT_ADMIN
/painel/**
```

Do not reuse a platform-only destructive action inside tenant UI without
understanding backend authorization.

Do not use frontend role checks as the only security boundary.

---

# Tests

Frontend tests use:

- Vitest;
- React Testing Library;
- `user-event` where appropriate.

Test user-visible behavior and important invariants.

Avoid tests that depend unnecessarily on internal implementation details.

When fixing a frontend bug, add a regression test when practical.

---

# Current Frontend Tests

Important current test files include:

```text
frontend/csp.test.js

frontend/src/App.test.jsx

frontend/src/api/client.test.js
frontend/src/api/imageOptimization.test.js

frontend/src/auth/AuthContext.test.jsx

frontend/src/components/EstablishmentEditorModal.test.jsx
frontend/src/components/MenuHero.test.jsx
frontend/src/components/MenuShell.test.jsx
frontend/src/components/ProductEditorModal.test.jsx
frontend/src/components/ThemePickerModal.test.jsx
frontend/src/components/UploadValidation.test.jsx

frontend/src/platform/PlatformModal.test.jsx
frontend/src/platform/PlatformPage.test.jsx
```

Read the relevant test before changing behavior in one of these domains.

---

# Which Tests to Read First

Authentication/session behavior:

```text
src/api/client.test.js
src/auth/AuthContext.test.jsx
src/App.test.jsx
```

Routing/role behavior:

```text
src/App.test.jsx
src/platform/PlatformPage.test.jsx
```

Public/admin menu behavior:

```text
src/components/MenuShell.test.jsx
src/components/MenuHero.test.jsx
src/App.test.jsx
```

Product modal:

```text
src/components/ProductEditorModal.test.jsx
```

Establishment editing:

```text
src/components/EstablishmentEditorModal.test.jsx
```

Theme behavior:

```text
src/components/ThemePickerModal.test.jsx
```

Upload validation:

```text
src/components/UploadValidation.test.jsx
src/api/imageOptimization.test.js
```

Platform behavior:

```text
src/platform/PlatformPage.test.jsx
src/platform/PlatformModal.test.jsx
```

CSP:

```text
csp.test.js
```

---

# Running Frontend Tests

From:

```text
frontend/
```

install exact dependencies when needed:

```bash
npm ci
```

Run tests:

```bash
npm test
```

Run production build:

```bash
npm run build
```

For a production-facing change, both tests and build should succeed.

Do not claim a test or build passed unless it was actually executed
successfully.

---

# Production Build

The Cloudflare deployment uses:

```bash
npm run build
```

Output:

```text
dist/
```

A change is not production-ready merely because development rendering works.

Verify the production build when changing:

- imports;
- CSP;
- environment handling;
- worker code;
- assets;
- image processing;
- routing fallback behavior;
- package dependencies.

---

# Dependency Changes

Before adding a frontend dependency, ask:

1. Can React already solve this?
2. Can browser APIs already solve this?
3. Is an existing dependency already available?
4. Does the dependency meaningfully reduce complexity?
5. Is it maintained?
6. Does it affect bundle size?
7. Does it affect CSP?
8. Does it introduce remote resources?
9. Does it create supply-chain/security concerns?

Do not add a package merely to save a few lines of code.

---

# Generated Files

Do not manually edit:

```text
package-lock.json
```

unless a dependency/package operation legitimately regenerates it.

Use npm tooling.

Do not hand-edit lockfile integrity values.

---

# Common Frontend Change Map

Use this map before creating a new file.

```text
Application routing
→ src/App.jsx

Home page
→ src/App.jsx

Login page
→ src/App.jsx

Tenant administration orchestration
→ src/App.jsx

Public menu orchestration
→ src/App.jsx

Authentication state
→ src/auth/AuthContext.jsx

Protected route behavior
→ src/auth/ProtectedArea.jsx

Roles / administrative home
→ src/auth/roles.js

All backend communication
→ src/api/client.js

Image source validation
→ src/api/uploadValidation.js

Image optimization
→ src/api/imageOptimization.js
→ src/api/imageOptimization.worker.js
→ src/api/imageProcessing.js

Optimized image integration
→ src/components/useOptimizedImage.js

Main menu rendering
→ src/components/MenuShell.jsx

Restaurant/menu hero
→ src/components/MenuHero.jsx

Scroll-to-top behavior
→ src/components/ScrollToTopButton.jsx

Product editing
→ src/components/ProductEditorModal.jsx

Establishment editing
→ src/components/EstablishmentEditorModal.jsx

Theme/image shape editing
→ src/components/ThemePickerModal.jsx

Icon exports
→ src/components/icons.js

Platform orchestration
→ src/platform/PlatformPage.jsx

Platform layout/navigation
→ src/platform/PlatformLayout.jsx

Platform dashboard
→ src/platform/PlatformDashboard.jsx

Platform customers
→ src/platform/PlatformClients.jsx

Platform customer details
→ src/platform/PlatformClientDetails.jsx

Platform customer/admin creation
→ src/platform/CreateAccountModal.jsx

Platform confirmation modal
→ src/platform/PlatformModal.jsx

Main application styles
→ src/styles.css

Production CSP
→ csp.mjs

CSP tests
→ csp.test.js
```

If the requested feature belongs to one of these existing paths, extend that
path instead of creating a parallel implementation.

---

# Before Creating a New Component

Before adding another component:

1. search `src/components/`;
2. search `src/platform/` if it is platform-related;
3. inspect the closest existing component;
4. check whether the behavior can be added cleanly there;
5. inspect related tests;
6. only then decide whether a new component is appropriate.

Do not create:

```text
NewMenuShell
MenuShell2
CustomModal
GenericModal2
NewProductCard
```

merely to avoid understanding existing code.

---

# Before Creating a New API Function

Before adding a function to:

```text
src/api/client.js
```

verify:

1. the backend route already exists or is being added;
2. no equivalent API function already exists;
3. authentication requirements;
4. CSRF requirements;
5. whether refresh retry applies;
6. expected response type;
7. error behavior.

Do not bypass the API client's `request()` flow for normal application
requests.

---

# Before Changing Authentication

Read:

```text
src/api/client.js
src/api/client.test.js
src/auth/AuthContext.jsx
src/auth/AuthContext.test.jsx
src/auth/ProtectedArea.jsx
src/auth/roles.js
src/App.test.jsx
```

Confirm that changes preserve:

- HttpOnly cookies;
- refresh behavior;
- session reconstruction;
- CSRF;
- role separation;
- session-expiration behavior;
- public route behavior.

---

# Before Changing Image Upload

Read:

```text
src/api/uploadValidation.js
src/api/imageOptimization.js
src/api/imageOptimization.worker.js
src/api/imageProcessing.js
src/components/useOptimizedImage.js
src/components/ProductEditorModal.jsx
src/components/EstablishmentEditorModal.jsx
src/api/client.js
```

Confirm that changes preserve:

- source validation;
- optimization;
- WebP output where expected;
- backend upload;
- failed-save cleanup;
- previous-image preservation;
- backend authority.

---

# Before Changing Menu Layout

Read:

```text
src/components/MenuShell.jsx
src/components/MenuHero.jsx
src/styles.css
src/components/MenuShell.test.jsx
src/components/MenuHero.test.jsx
```

Also inspect both:

```text
public mode
admin mode
```

A menu change that looks correct publicly may break administration controls,
and vice versa.

---

# Before Changing Platform UI

Read:

```text
src/platform/PlatformPage.jsx
src/platform/PlatformLayout.jsx
src/platform/PlatformDashboard.jsx
src/platform/PlatformClients.jsx
src/platform/PlatformClientDetails.jsx
src/platform/CreateAccountModal.jsx
src/platform/PlatformModal.jsx
src/platform/PlatformPage.test.jsx
```

Preserve:

- platform-only role behavior;
- customer activation/deactivation;
- destructive confirmation;
- customer deletion handling;
- navigation;
- error feedback;
- loading feedback.

---

# Final Frontend Review

Before finishing a frontend change, verify:

- Did I read the root `AGENTS.md`?
- Did I search for an existing implementation?
- Did I unnecessarily grow `App.jsx`?
- Did I duplicate an existing component?
- Did I duplicate API logic?
- Did I bypass `src/api/client.js`?
- Did I duplicate CSRF handling?
- Did I reintroduce browser-stored authentication?
- Did I expose a JWT?
- Did I introduce an unnecessary dependency?
- Did I duplicate image validation?
- Did I bypass image optimization?
- Did I bypass failed-upload cleanup?
- Did I introduce unsafe URL rendering?
- Did I use `dangerouslySetInnerHTML` unnecessarily?
- Did I preserve platform/tenant role separation?
- Did I preserve public/admin menu separation?
- Did I reuse existing semantic CSS tokens?
- Did I create an unnecessary visual pattern?
- Is the UI still usable on mobile?
- Is keyboard interaction preserved?
- Are loading/error/empty states handled?
- Is CSP still valid?
- Did I add an external origin unnecessarily?
- Are relevant frontend tests passing?
- Does `npm run build` pass?
- Could this frontend safely be deployed to Cloudflare Pages?

If any answer is uncertain, investigate before finishing.

---

# Frontend Principle

Prefer:

```text
existing pattern > new pattern

reuse > recreation

central API client > scattered fetch calls

backend authority > frontend assumptions

semantic tokens > arbitrary colors

simple React > unnecessary framework layering

local state > unnecessary global state

accessible behavior > visual shortcuts

structural CSS fixes > layout hacks

focused components > larger App.jsx

existing upload flow > direct Supabase access
```

Do not redesign the application while implementing an unrelated feature.

Investigate first.

Reuse existing behavior.

Preserve authentication and security.

Preserve responsive behavior.

Preserve accessibility.

Make the smallest coherent change.