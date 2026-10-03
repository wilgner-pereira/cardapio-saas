## 2024-08-27 - [Array processing in React Components]
**Learning:** Chained array methods like `.filter().reduce()` or `.filter().map()` can cause unnecessary memory allocations and multiple iterations over large arrays, which is noticeable when rendering components that process large lists on every render (e.g., categorizing a large menu).
**Action:** Replace chained array methods with a single imperative loop (like `for` or `for...of`) when processing large datasets in React components that run synchronously during the render cycle.

## 2026-10-03 - [Spring @Transactional(readOnly = true) vs jakarta.transaction.Transactional]
**Learning:** In Spring Boot with Spring Data JPA/Hibernate, importing `jakarta.transaction.Transactional` lacks the `readOnly` attribute. Using Spring's `org.springframework.transaction.annotation.Transactional(readOnly = true)` sets Hibernate's flush mode to `MANUAL` (disabling dirty checking overhead across loaded entities) and marks the JDBC connection as read-only.
**Action:** Always import Spring's `@Transactional` annotation in Spring Boot service classes and annotate read-only query methods with `@Transactional(readOnly = true)`.
