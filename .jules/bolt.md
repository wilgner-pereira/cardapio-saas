## 2024-08-27 - [Array processing in React Components]
**Learning:** Chained array methods like `.filter().reduce()` or `.filter().map()` can cause unnecessary memory allocations and multiple iterations over large arrays, which is noticeable when rendering components that process large lists on every render (e.g., categorizing a large menu).
**Action:** Replace chained array methods with a single imperative loop (like `for` or `for...of`) when processing large datasets in React components that run synchronously during the render cycle.

## 2026-10-04 - [Schwartzian transform for Array sorting in React]
**Learning:** When sorting collections in JavaScript using multiple criteria including string values, calling `String(val)` and `.localeCompare()` directly inside `.sort((a, b) => ...)` causes `O(N log N)` repeated type coercions and function calls. Using a Schwartzian transform (map to pre-computed keys, sort, map back) reduces `String()` conversions from `O(N log N)` to `O(N)` while preserving exact `localeCompare()` linguistic sort order.
**Action:** Pre-compute string fields into mapped tuple/object keys before sorting when sorting arrays with multi-property comparator logic in hot state-update paths.
