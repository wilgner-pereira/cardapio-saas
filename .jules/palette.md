## 2024-08-27 - Button focus visible styles
**Learning:** This app's interactive buttons lacked clear keyboard focus indicators, rendering keyboard navigation difficult and failing a11y standards. When implementing UX additions, it's important to strictly use the existing CSS structure and follow boundaries instead of installing large dependencies (like ESLint). Global style updates (like adding a `:focus-visible` outline for buttons to the main CSS file) are appropriate when they solve pervasive a11y problems across all elements of that type.
**Action:** Next time, directly append standard `focus-visible` styles to global stylesheets rather than introducing unrelated tooling changes, and ensure only the targeted files are modified.

## 2025-05-18 - NotFoundState navigation controls constraint
**Learning:** `NotFoundState` in this app intentionally does not contain buttons or navigation links, as verified by explicit assertions in `App.test.jsx`. Adding navigation controls to `NotFoundState` violates existing design constraints.
**Action:** Respect `NotFoundState` minimal design; focus micro-UX accessibility improvements on icon `aria-hidden` attributes and focus management for interactive components instead.
