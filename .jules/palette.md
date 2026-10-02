## 2025-02-14 - Contextual ARIA labels and title tooltips for repetitive list actions
**Learning:** When lists or grids contain repeated icon-only action buttons (e.g., product card actions in administrative views), generic ARIA labels like "Editar produto" make screen reader navigation ambiguous because every button shares the same name. Including the item's name (e.g., "Editar Pizza Margherita") along with matching title tooltips resolves screen reader ambiguity and provides immediate hover feedback for desktop users.
**Action:** For repetitive list item action buttons, always include item context in `aria-label` and `title` attributes.

## 2024-08-27 - Button focus visible styles
**Learning:** This app's interactive buttons lacked clear keyboard focus indicators, rendering keyboard navigation difficult and failing a11y standards. When implementing UX additions, it's important to strictly use the existing CSS structure and follow boundaries instead of installing large dependencies (like ESLint). Global style updates (like adding a `:focus-visible` outline for buttons to the main CSS file) are appropriate when they solve pervasive a11y problems across all elements of that type.
**Action:** Next time, directly append standard `focus-visible` styles to global stylesheets rather than introducing unrelated tooling changes, and ensure only the targeted files are modified.
