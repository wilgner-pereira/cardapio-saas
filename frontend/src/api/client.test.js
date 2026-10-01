import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
let api;

const session = {
  message: "Autenticado",
  tokenType: "Bearer",
  userId: 42,
  username: "restaurante-teste",
  email: "owner@example.test",
  estabelecimentoSlug: "restaurante-teste",
  roles: ["ROLE_TENANT_ADMIN"],
  accessExpiresInMs: 900000
};

function response(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" }
  });
}

describe("authentication with HttpOnly cookies and metadata-only responses", () => {
  let fetchMock;

  beforeEach(async () => {
    vi.resetModules();
    localStorage.clear();
    sessionStorage.clear();
    ({ api } = await import("./client.js"));
    document.cookie = "XSRF-TOKEN=test-csrf; path=/";
    fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
  });

  async function loginInMemory() {
    fetchMock.mockResolvedValueOnce(response(session))
      .mockResolvedValueOnce(response({ token: "test-csrf" }));
    await api.login(session.username, "test-password");
    fetchMock.mockClear();
  }

  afterEach(() => {
    localStorage.clear();
    document.cookie = "XSRF-TOKEN=; Max-Age=0; path=/";
    vi.unstubAllGlobals();
  });

  it('saves theme and image shape through the existing cookie and CSRF flow', async () => {
    await loginInMemory();
    fetchMock.mockImplementation(() => Promise.resolve(response({ tema: 'grafite', imagensRedondas: true })));
    await api.updateMyEstablishmentTheme('grafite', true);
    const [url, options] = fetchMock.mock.calls[0];
    expect(new URL(url).searchParams.get('imagensRedondas')).toBe('true');
    expect(options.method).toBe('PUT');
    expect(options.credentials).toBe('include');
    expect(options.headers['X-XSRF-TOKEN']).toBe('test-csrf');
    fetchMock.mockClear();
    await api.updateMyEstablishmentTheme('artesanal');
    expect(new URL(fetchMock.mock.calls[0][0]).searchParams.has('imagensRedondas')).toBe(false);
  });

  it("logs in, validates, refreshes a rejected request and logs out without a JSON JWT", async () => {
    fetchMock
      .mockResolvedValueOnce(response(session))
      .mockResolvedValueOnce(response({ token: "test-csrf" }))
      .mockResolvedValueOnce(response({ authenticated: true, session: { ...session, accessExpiresInMs: 0 } }))
      .mockResolvedValueOnce(response({ message: "Expired" }, 401))
      .mockResolvedValueOnce(response({ ...session, message: "Token renovado" }))
      .mockResolvedValueOnce(response({ token: "test-csrf" }))
      .mockResolvedValueOnce(response([{ id: 1, nome: "Produto" }]))
      .mockResolvedValueOnce(response({ message: "Sessão encerrada" }));

    expect(await api.login(session.username, "test-password")).toEqual(session);
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
    expect(api.getStoredUser()).not.toHaveProperty("email");
    expect(api.getStoredUser()).toMatchObject({ username: session.username, roles: ["ROLE_TENANT_ADMIN"] });
    expect(await api.validate()).toMatchObject({ username: session.username });
    expect(await api.getAdminProducts()).toEqual([{ id: 1, nome: "Produto" }]);
    await api.logout();
    expect(api.getStoredUser()).toBeNull();

    expect(fetchMock.mock.calls.map(([url]) => new URL(url).pathname)).toEqual([
      "/auth/login", "/auth/csrf", "/auth/session", "/painel/produtos",
      "/auth/refresh", "/auth/csrf", "/painel/produtos", "/auth/logout"
    ]);
    for (const [, options] of fetchMock.mock.calls) {
      expect(options.credentials).toBe("include");
      expect(options.headers).not.toHaveProperty("Authorization");
      if (options.method === "POST") {
        expect(options.headers["X-XSRF-TOKEN"]).toBe("test-csrf");
      }
    }
  });

  it("clears metadata and signals expiration when refresh fails", async () => {
    await loginInMemory();
    const expired = vi.fn();
    window.addEventListener("cardapio:session-expired", expired);
    fetchMock
      .mockResolvedValueOnce(response({}, 401))
      .mockResolvedValueOnce(response({ message: "Sessão expirada" }, 401));
    try {
      await expect(api.getAdminProducts()).rejects.toThrow("Sessão expirada");
      expect(api.getStoredUser()).toBeNull();
      expect(expired).toHaveBeenCalledTimes(1);
      expect(fetchMock).toHaveBeenCalledTimes(2);
    } finally {
      window.removeEventListener("cardapio:session-expired", expired);
    }
  });

  it("uses the CSRF response when the API cookie is on another host", async () => {
    // A frontend-host cookie must not override the token obtained from the API host.
    document.cookie = "XSRF-TOKEN=unrelated-frontend-token; path=/";
    fetchMock
      .mockResolvedValueOnce(response(session))
      .mockResolvedValueOnce(response({ token: "api-host-token", headerName: "X-CSRF-TOKEN" }))
      .mockResolvedValueOnce(response({ message: "Sessão encerrada" }));
    await api.login(session.username, "test-password");
    await api.logout();
    expect(fetchMock.mock.calls[2][1].headers["X-CSRF-TOKEN"]).toBe("api-host-token");
    expect(fetchMock.mock.calls[2][1].headers).not.toHaveProperty("X-XSRF-TOKEN");
    expect(api.getStoredUser()).toBeNull();
  });

  it("keeps session metadata if the server rejects logout", async () => {
    await loginInMemory();
    fetchMock.mockResolvedValueOnce(response({ message: "Acesso negado" }, 403));
    await expect(api.logout()).rejects.toThrow("Acesso negado");
    expect(api.getStoredUser()).toMatchObject({ username: session.username });
  });

  it("sends the operator setup secret only in its header alongside CSRF", async () => {
    const secret = "test-only-setup-secret-with-32-characters";
    fetchMock
      .mockResolvedValueOnce(response({ token: "test-csrf" }))
      .mockResolvedValueOnce(response({ username: "operator" }, 201));
    const payload = { username: "operator", email: "operator@example.test", password: "test-password" };
    await api.createFirstAdmin(payload, secret);
    expect(fetchMock.mock.calls[1][1].headers).toMatchObject({
      "X-Setup-Token": secret,
      "X-XSRF-TOKEN": "test-csrf"
    });
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual(payload);
    expect(localStorage.length).toBe(0);
  });

  it("does not expire a session when refresh is temporarily rate limited", async () => {
    await loginInMemory();
    const expired = vi.fn();
    window.addEventListener("cardapio:session-expired", expired);
    fetchMock
      .mockResolvedValueOnce(response({}, 401))
      .mockResolvedValueOnce(response({ message: "Muitas requisicoes" }, 429));
    try {
      await expect(api.getAdminProducts()).rejects.toThrow("Muitas requisicoes");
      expect(api.getStoredUser()).toMatchObject({ username: session.username });
      expect(expired).not.toHaveBeenCalled();
      expect(fetchMock).toHaveBeenCalledTimes(2);
    } finally {
      window.removeEventListener("cardapio:session-expired", expired);
    }
  });

  it("reconstructs a reload from validate and removes legacy storage without trusting it", async () => {
    localStorage.setItem("cardapio.authUser", JSON.stringify({ ...session, username: "stale" }));
    sessionStorage.setItem("cardapio.authUser", JSON.stringify(session));
    vi.resetModules();
    const reloaded = (await import("./client.js")).api;
    expect(reloaded.getStoredUser()).toBeNull();
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
    fetchMock.mockResolvedValueOnce(response({ token: "csrf" }))
      .mockResolvedValueOnce(response({ authenticated: true, session }));
    await Promise.all([reloaded.validate(), reloaded.validate()]);
    expect(reloaded.getStoredUser()).toMatchObject({ username: session.username });
    expect(fetchMock.mock.calls.map(([url]) => new URL(url).pathname)).toEqual(["/auth/csrf", "/auth/session"]);
  });

  it("loads public menu data without validate or refresh requests", async () => {
    fetchMock.mockResolvedValueOnce(response([{ id: 1, nome: "Pizza" }]))
      .mockResolvedValueOnce(response({ nome: "Pizzaria" }));

    await Promise.all([
      api.getPublicMenu("pizzaria"),
      api.getPublicEstablishment("pizzaria")
    ]);

    expect(fetchMock.mock.calls.map(([url]) => new URL(url).pathname)).toEqual([
      "/public/pizzaria/cardapio",
      "/public/pizzaria/cardapio/info"
    ]);
  });

  it("never persists tokens even if an old server includes them in JSON", async () => {
    const writes = vi.spyOn(Storage.prototype, "setItem");
    fetchMock.mockResolvedValueOnce(response({ ...session, accessToken: "jwt-access", refreshToken: "jwt-refresh" }))
      .mockResolvedValueOnce(response({ token: "csrf" }));
    await api.login(session.username, "test-password");
    expect(writes).not.toHaveBeenCalled();
    expect(api.getStoredUser()).not.toHaveProperty("accessToken");
    expect(api.getStoredUser()).not.toHaveProperty("refreshToken");
    fetchMock.mockResolvedValueOnce(response({}));
    await api.logout();
    expect(api.hasStoredUser()).toBe(false);
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
    writes.mockRestore();
  });

  it("can recover a reload with expired access through refresh", async () => {
    fetchMock.mockResolvedValueOnce(response({ token: "csrf" }))
      .mockResolvedValueOnce(response({ authenticated: false, refreshAvailable: true, session: null }))
      .mockResolvedValueOnce(response(session))
      .mockResolvedValueOnce(response({ token: "csrf-after-refresh" }))
      .mockResolvedValueOnce(response({ authenticated: true, session }))
      .mockResolvedValueOnce(response([{ id: 1, nome: "Produto restaurado" }]));
    await api.validate();
    expect(api.getStoredUser()).toMatchObject({ username: session.username });
    expect(await api.getAdminProducts()).toEqual([{ id: 1, nome: "Produto restaurado" }]);
    expect(localStorage.length).toBe(0);
    expect(fetchMock.mock.calls.map(([url]) => new URL(url).pathname)).toEqual([
      "/auth/csrf", "/auth/session", "/auth/refresh", "/auth/csrf", "/auth/session",
      "/painel/produtos"
    ]);
  });

  it("recognizes an anonymous reload without probing protected routes or attempting refresh", async () => {
    fetchMock.mockResolvedValueOnce(response({ token: "csrf" }))
      .mockResolvedValueOnce(response({ authenticated: false, refreshAvailable: false, session: null }));
    await expect(api.validate()).rejects.toMatchObject({ sessionExpired: true });
    expect(fetchMock.mock.calls.map(([url]) => new URL(url).pathname)).toEqual(["/auth/csrf", "/auth/session"]);
    expect(api.getStoredUser()).toBeNull();
  });

  it("shares one CSRF initialization across parallel mutations", async () => {
    fetchMock
      .mockResolvedValueOnce(response({ token: "parallel-csrf", headerName: "X-CSRF-TOKEN" }))
      .mockResolvedValueOnce(response({ id: 10, ativo: false }))
      .mockResolvedValueOnce(response({ id: 10, ordem: 2 }));

    await Promise.all([
      api.updateProductStatus(10, false),
      api.updateProductOrder(10, 2)
    ]);

    expect(fetchMock.mock.calls.map(([url]) => new URL(url).pathname)).toEqual([
      "/auth/csrf", "/painel/produtos/10/status", "/painel/produtos/10/ordem"
    ]);
    for (const [, options] of fetchMock.mock.calls.slice(1)) {
      expect(options.headers["X-CSRF-TOKEN"]).toBe("parallel-csrf");
    }
  });

  it("requests unused image cleanup with authentication and CSRF", async () => {
    await loginInMemory();
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));
    await api.discardUnusedImage("https://example.com/new-upload.png");
    const [url, options] = fetchMock.mock.calls[0];
    expect(new URL(url).pathname).toBe("/painel/storage/unused-image");
    expect(options.method).toBe("DELETE");
    expect(options.credentials).toBe("include");
    expect(options.headers["X-XSRF-TOKEN"]).toBe("test-csrf");
    expect(JSON.parse(options.body)).toEqual({ url: "https://example.com/new-upload.png" });
  });

  it.each(["product", "hero"])("uploads the optimized WebP and %s profile with cookies and CSRF", async type => {
    await loginInMemory();
    fetchMock.mockResolvedValueOnce(new Response("https://example.com/uuid", { status: 200 }));
    const optimized = new File(["webp"], "optimized.webp", { type: "image/webp" });
    await api.uploadImage(optimized, type);
    const [url, options] = fetchMock.mock.calls[0];
    expect(new URL(url).pathname).toBe("/painel/storage/upload");
    expect(options.method).toBe("POST");
    expect(options.credentials).toBe("include");
    expect(options.headers["X-XSRF-TOKEN"]).toBe("test-csrf");
    expect(options.headers).not.toHaveProperty("Content-Type");
    expect(options.body.get("file")).toBe(optimized);
    expect(options.body.get("file").name).toBe("optimized.webp");
    expect(options.body.get("type")).toBe(type);
  });

  it.each([
    ["getPlatformEstablishments", [], "/plataforma/estabelecimentos", "GET"],
    ["getPlatformEstablishment", [12], "/plataforma/estabelecimentos/12", "GET"],
    ["createPlatformEstablishment", [{ nome: "Novo cliente" }], "/plataforma/estabelecimentos", "POST"],
    ["activatePlatformEstablishment", [12], "/plataforma/estabelecimentos/12/ativar", "PATCH"],
    ["deactivatePlatformEstablishment", [12], "/plataforma/estabelecimentos/12/desativar", "PATCH"],
    ["deletePlatformEstablishment", [12], "/plataforma/estabelecimentos/12", "DELETE"],
    ["createPlatformAdmin", [{ username: "operator" }], "/plataforma/admins", "POST"]
  ])("%s uses the shared cookie and CSRF request flow", async (method, args, path, verb) => {
    await loginInMemory();
    fetchMock.mockResolvedValueOnce(verb === "DELETE" ? new Response(null, { status: 204 }) : response({ id: 12 }));
    await api[method](...args);
    const [url, options] = fetchMock.mock.calls[0];
    expect(new URL(url).pathname).toBe(path);
    expect(options.method).toBe(verb);
    expect(options.credentials).toBe("include");
    expect(options.headers).not.toHaveProperty("Authorization");
    if (verb !== "GET") expect(options.headers["X-XSRF-TOKEN"]).toBe("test-csrf");
    if (verb === "POST") expect(JSON.parse(options.body)).toEqual(args[0]);
  });

  it("returns platform validation messages and field errors to the form", async () => {
    await loginInMemory();
    fetchMock.mockResolvedValueOnce(response({ message: "Dados inválidos", fieldErrors: { username: "Username inválido" } }, 400));
    await expect(api.createPlatformEstablishment({})).rejects.toMatchObject({
      message: "Dados inválidos", status: 400, fieldErrors: { username: "Username inválido" }, sessionExpired: false
    });
  });

  it("refreshes a platform mutation after 401 and retries with the renewed CSRF token", async () => {
    await loginInMemory();
    fetchMock.mockResolvedValueOnce(response({}, 401))
      .mockResolvedValueOnce(response({ ...session, roles: ["ROLE_PLATFORM_ADMIN"] }))
      .mockResolvedValueOnce(response({ token: "renewed", headerName: "X-CSRF-TOKEN" }))
      .mockResolvedValueOnce(response({ id: 12, ativo: false }));
    await expect(api.deactivatePlatformEstablishment(12)).resolves.toMatchObject({ ativo: false });
    expect(fetchMock.mock.calls.map(([url]) => new URL(url).pathname)).toEqual([
      "/plataforma/estabelecimentos/12/desativar", "/auth/refresh", "/auth/csrf", "/plataforma/estabelecimentos/12/desativar"
    ]);
    expect(fetchMock.mock.calls[3][1].headers["X-CSRF-TOKEN"]).toBe("renewed");
  });
});
