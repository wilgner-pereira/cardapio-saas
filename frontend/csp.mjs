export function buildCsp(apiBase = "http://localhost:8080") {
  const api = new URL(apiBase);
  if (!["http:", "https:"].includes(api.protocol) || api.username || api.password) {
    throw new Error("VITE_API_BASE_URL deve usar HTTP/HTTPS sem credenciais");
  }
  return [
    "default-src 'none'", "script-src 'self'", "style-src 'self' 'unsafe-inline'",
    `img-src 'self' blob: data: https: ${api.origin}`,
    "font-src 'self'", `connect-src 'self' ${api.origin}`,
    "object-src 'none'", "base-uri 'none'", "frame-ancestors 'none'", "form-action 'self'"
  ].join("; ");
}

export function cspPlugin(apiBase) {
  const policy = buildCsp(apiBase);
  return {
    name: "frontend-csp",
    apply: "build",
    transformIndexHtml: {
      order: "post",
      handler() {
        return [{ tag: "meta", attrs: {
          "http-equiv": "Content-Security-Policy",
          // frame-ancestors only works as an HTTP header, emitted below.
          content: policy.replace("; frame-ancestors 'none'", "")
        }, injectTo: "head-prepend" }];
      }
    },
    generateBundle() {
      this.emitFile({ type: "asset", fileName: "csp-header.txt", source: policy });
      this.emitFile({ type: "asset", fileName: "_headers", source: `/*\n  Content-Security-Policy: ${policy}\n` });
    }
  };
}
