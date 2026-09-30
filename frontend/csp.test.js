// @vitest-environment node
import { describe, expect, it } from "vitest";
import { mkdtemp, writeFile, unlink, rmdir } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { buildCsp, cspPlugin } from "./csp.mjs";
import { createStaticServer } from "./serve-preview.mjs";

describe("CSP on the frontend document", () => {
  it("limits scripts and connections to the actual API origin", () => {
    const policy = buildCsp("https://api.example.test/backend");
    expect(policy).toContain("script-src 'self'");
    expect(policy).toContain("connect-src 'self' https://api.example.test");
    expect(policy).toContain("object-src 'none'");
    expect(policy).toContain("frame-ancestors 'none'");
    expect(policy).not.toContain("unsafe-eval");
    expect(policy).not.toMatch(/script-src[^;]*unsafe-inline/);
    expect(() => buildCsp("javascript:alert(1)")).toThrow();
  });

  it("embeds the effective document policy and emits headers for external hosting", () => {
    const plugin = cspPlugin("https://api.example.test");
    const [meta] = plugin.transformIndexHtml.handler();
    expect(meta.injectTo).toBe("head-prepend");
    expect(meta.attrs["http-equiv"]).toBe("Content-Security-Policy");
    expect(meta.attrs.content).toContain("script-src 'self'");
    expect(meta.attrs.content).not.toContain("frame-ancestors");
    const files = [];
    plugin.generateBundle.call({ emitFile: file => files.push(file) });
    expect(files.find(file => file.fileName === "_headers").source).toContain("frame-ancestors 'none'");
    expect(files.find(file => file.fileName === "csp-header.txt").source).toBe(buildCsp("https://api.example.test"));
  });

  it("delivers CSP with the actual HTML response including SPA fallback", async () => {
    const root = await mkdtemp(join(tmpdir(), "cardapio-csp-"));
    const policy = buildCsp("https://api.example.test");
    await writeFile(join(root, "index.html"), "<!doctype html><title>Cardapio</title>");
    const server = createStaticServer(root, policy);
    await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
    try {
      for (const path of ["/", "/admin/produtos"]) {
        const response = await fetch(`http://127.0.0.1:${server.address().port}${path}`);
        expect(response.headers.get("content-security-policy")).toBe(policy);
        expect(response.headers.get("content-type")).toContain("text/html");
        expect(await response.text()).toContain("Cardapio");
      }
    } finally {
      await new Promise(resolve => server.close(resolve));
      await unlink(join(root, "index.html"));
      await rmdir(root);
    }
  });
});
