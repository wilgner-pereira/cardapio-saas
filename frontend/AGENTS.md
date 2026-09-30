# Frontend AGENTS.md

This file extends the repository root `AGENTS.md`.

All root rules remain mandatory.

---

## Current frontend stack

The frontend intentionally uses:

- React 18
- JavaScript / JSX
- Vite
- Vitest
- React Testing Library
- lucide-react
- native `fetch`
- custom CSS

It currently does NOT use:

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

A stack change requires an explicit requirement and concrete justification.

---

## Current architecture

Important existing files:

```text
src/App.jsx
src/api/client.js
src/api/uploadValidation.js
src/auth/AuthContext.jsx
src/components/
src/styles.css
csp.mjs
```

`App.jsx` currently owns route parsing and high-level pages.

Routing is intentionally lightweight and currently uses:

- `window.location.pathname`;
- `history.pushState`;
- `parseRoute()`.

Do not add React Router just to implement another simple route.

Follow the existing routing approach unless routing complexity has genuinely outgrown it or the user explicitly asks for a migration.

---

## Do not grow App.jsx unnecessarily

`App.jsx` is already a large orchestration file.

When adding substantial new UI:

- do not dump another large component into `App.jsx`;
- prefer extracting a meaningful page/component when it represents a clear UI responsibility;
- keep routing/orchestration in `App.jsx`.

However:

do not extract tiny fragments merely to create more files.

Extract by responsibility, not by line count alone.

---

## Existing reusable components

Before creating UI, inspect:

```text
src/components/MenuShell.jsx
src/components/MenuHero.jsx
src/components/ProductEditorModal.jsx
src/components/EstablishmentEditorModal.jsx
src/components/ThemePickerModal.jsx
src/components/icons.js
```

Search these components before creating new:

- modal patterns;
- close buttons;
- loading states;
- image previews;
- product cards;
- empty/error state UI;
- icons;
- menu layout;
- hero layout;
- theme UI.

Do not create a second implementation when an existing component can be extended cleanly.

---

## API access

All browser/backend communication should follow:

```text
src/api/client.js
```

Do not scatter `fetch()` calls through React components.

Do not add Axios.

Do not duplicate:

- base URL handling;
- credentials handling;
- error parsing;
- refresh behavior;
- CSRF initialization;
- session-expiration handling;
- image URL resolution.

Extend the existing `api` client when a new backend operation is required.

---

## Authentication

Authentication cookies are HttpOnly and controlled by the backend.

Never attempt to read JWT cookies from JavaScript.

Never put JWTs in:

- localStorage;
- sessionStorage;
- IndexedDB;
- React-persisted state.

The project intentionally reconstructs authentication from server state.

Legacy `cardapio.authUser` storage is removed, not trusted.

Do not reintroduce persistence of authentication metadata in browser storage merely to make login state easier.

`AuthContext` is the frontend source of authentication state.

---

## CSRF

Mutable requests must continue through the existing CSRF flow in `src/api/client.js`.

Do not:

- disable CSRF;
- manually invent a second CSRF implementation;
- mix the raw cookie token and XOR token representations incorrectly;
- bypass the API client's request path.

If authentication or request behavior changes, inspect:

```text
src/api/client.test.js
src/auth/AuthContext.test.jsx
```

before implementation.

---

## Uploads

Image validation is centralized in:

```text
src/api/uploadValidation.js
```

Reuse:

- `validateImage`
- `IMAGE_ACCEPT`
- `MAX_IMAGE_BYTES`

Do not duplicate image type/size validation inside individual components.

Frontend validation is UX only.

The backend remains authoritative.

The frontend upload limit must remain aligned with backend multipart/file limits.

When an image was uploaded successfully but the subsequent entity save fails, preserve the existing unused-image cleanup/compensation flow.

Do not leave uploaded objects orphaned simply because the UI request failed later.

---

## Styling

The project currently uses one main stylesheet:

```text
src/styles.css
```

It already contains a semantic theme/token system.

Prefer existing CSS variables such as:

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

Do not introduce arbitrary colors when an appropriate semantic token already exists.

Before writing CSS:

1. search for an existing selector;
2. search for an existing semantic token;
3. inspect a visually similar component;
4. extend the current pattern.

Do not introduce another styling system for one component.

---

## Visual consistency

Consistency is more important than creativity.

New UI must look like it belongs to the current application.

Before implementing a screen or component, inspect the closest existing screen for:

- spacing;
- typography;
- border radius;
- controls;
- buttons;
- cards;
- shadows;
- responsive behavior;
- loading state;
- error state;
- empty state.

Do not create a new design language for each feature.

---

## Theme system

The theme system is intentional and backed by database/API behavior.

Do not invent, rename or remove themes solely in frontend code.

Do not duplicate theme colors directly inside components.

Use the existing semantic CSS variables and `data-theme` behavior.

Changes to themes may affect:

- `styles.css`;
- `ThemePickerModal.jsx`;
- establishment API behavior;
- backend enum/validation;
- Flyway constraints;
- tests.

Inspect all affected layers before changing theme contracts.

---

## Inline styles

Prefer CSS classes and existing semantic tokens.

Inline style objects are acceptable only where dynamic values are genuinely required by existing design behavior.

Do not turn static styling into JSX style objects.

Do not use inline styles as a quick fix for layout problems.

---

## CSS hacks

Avoid using these as first-line fixes:

- `!important`
- arbitrary `z-index`
- negative margins
- fixed pixel positioning
- unnecessary `position: absolute`
- transforms used only to compensate for bad layout
- viewport-specific magic numbers

Find the structural cause first.

---

## Accessibility

Accessibility behavior already has tests and must be preserved.

Use semantic HTML.

Use:

```text
button
```

for actions.

Use links for navigation semantics where appropriate.

Preserve:

- keyboard focus visibility;
- modal labels;
- `aria-*` attributes;
- `role="alert"` for relevant errors;
- Escape-to-close behavior where established;
- backdrop dismissal behavior where established;
- keyboard usability.

Do not remove focus outlines without an accessible replacement.

---

## Modals

Before creating another modal, inspect existing modal components.

Existing modal behavior includes accessibility and dismissal conventions.

Do not create an independent modal system unless there is a clear architectural reason.

When modifying modal behavior, add/update tests for:

- Escape;
- backdrop click;
- inside click;
- labels;
- error announcements.

---

## Icons

Use the existing icon abstraction in:

```text
src/components/icons.js
```

which is based on `lucide-react`.

Do not:

- install another icon library;
- paste random SVG implementations;
- mix icon families unnecessarily.

---

## CSP

Production CSP is generated by:

```text
csp.mjs
```

and integrated into the Vite production build.

Do not weaken it to make a feature work.

Never add:

```text
script-src *
'unsafe-eval'
script-src 'unsafe-inline'
connect-src *
```

as a shortcut.

If a legitimate new external origin is required:

1. understand why;
2. update CSP intentionally;
3. keep the allowlist narrow;
4. update `csp.test.js`;
5. verify the production build.

Remember that `frame-ancestors` must be enforced by the real production host header.

---

## External content

Do not add remote scripts, fonts, trackers or third-party widgets casually.

They have implications for:

- CSP;
- privacy;
- availability;
- performance.

There is currently no analytics/tracking dependency in the frontend.

Do not introduce one unless explicitly requested.

---

## Images

Use existing image URL resolution and validation logic.

Do not use `dangerouslySetInnerHTML` to render content.

Do not accept JavaScript/data URLs merely because the browser can display them.

Preserve safe fallback behavior for invalid image URLs.

---

## State

Keep state local unless it is genuinely shared.

Use `AuthContext` for authentication.

Do not introduce a global state library to solve local component state.

Avoid duplicated sources of truth.

Derived values should normally be calculated rather than copied into another state variable.

---

## React hooks

Do not use hooks by reflex.

Especially avoid unnecessary:

- `useEffect`
- `useMemo`
- `useCallback`

Use them when their semantics are actually required.

Do not use `useEffect` just to derive state that can be calculated during render.

Do not memoize trivial expressions without a measured reason.

---

## Lists and menu performance

`MenuShell` processes product/category collections during rendering.

Avoid repeated chained transformations over large collections when one clear pass is sufficient.

Do not prematurely optimize tiny arrays, but do not introduce avoidable repeated processing into hot render paths.

Measure before adding complex memoization.

---

## Responsive UI

Changes must remain usable on:

- desktop;
- tablet;
- mobile.

Avoid fixed widths when responsive layout is appropriate.

Do not introduce accidental horizontal scrolling.

Check modals, menus, product cards and admin actions on narrow screens.

---

## Tests

Use existing tools:

- Vitest
- React Testing Library
- user-event where appropriate

Test behavior rather than internal implementation.

When touching frontend behavior, run:

```bash
npm test
npm run build
```

When touching CSP also verify:

```text
csp.test.js
```

Relevant component behavior should have regression tests.

---

## Final frontend review

Before finishing:

- Did I reuse an existing component?
- Did I duplicate API logic?
- Did I duplicate upload validation?
- Did I create an unnecessary library dependency?
- Did I unnecessarily grow `App.jsx`?
- Did I introduce a new visual pattern?
- Did I reuse semantic CSS variables?
- Is mobile still usable?
- Is keyboard interaction preserved?
- Is CSP still valid?
- Did I reintroduce browser-stored auth data?
- Are loading/error/empty states handled where relevant?
- Do frontend tests pass?
- Does `npm run build` pass?

---

## Frontend principle

Prefer:

existing pattern > new pattern

reuse > recreation

semantic tokens > arbitrary styling

simple React > framework layering

accessible behavior > visual shortcut

structural CSS fix > layout hack

focused component > larger `App.jsx`

Do not redesign the application while implementing a feature.