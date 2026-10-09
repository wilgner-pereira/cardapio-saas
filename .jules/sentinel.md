## 2026-10-09 - Storage Content-Type Sanitization Against Stored XSS
**Vulnerability:** Downloading public files from storage proxy passed through the remote `Content-Type` directly. If the remote bucket contained non-image files (like `text/html` or `image/svg+xml`), browsers could render and execute scripts in the application origin.
**Learning:** Public file proxy endpoints must sanitize response `Content-Type` headers to prevent stored XSS or MIME-sniffing exploits.
**Prevention:** Restrict allowed download `Content-Type` headers to safe image formats (`image/webp`, `image/jpeg`, `image/png`, `image/gif`) and fall back to `application/octet-stream` for all unknown or non-image content types.
