## 2024-08-27 - [Array processing in React Components]
**Learning:** Chained array methods like `.filter().reduce()` or `.filter().map()` can cause unnecessary memory allocations and multiple iterations over large arrays, which is noticeable when rendering components that process large lists on every render (e.g., categorizing a large menu).
**Action:** Replace chained array methods with a single imperative loop (like `for` or `for...of`) when processing large datasets in React components that run synchronously during the render cycle.
