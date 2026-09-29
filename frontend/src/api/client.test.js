import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "./client.js";

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

  beforeEach(() => {
    localStorage.clear();
    document.cookie = "XSRF-TOKEN=test-csrf; path=/";
    fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    localStorage.clear();
    document.cookie = "XSRF-TOKEN=; Max-Age=0; path=/";
    vi.unstubAllGlobals();
  });

  it("logs in, validates, refreshes a rejected request and logs out without a JSON JWT", async () => {
    fetchMock
      .mockResolvedValueOnce(response({ token: "test-csrf" }))
      .mockResolvedValueOnce(response(session))
      .mockResolvedValueOnce(response({ token: "test-csrf" }))
      .mockResolvedValueOnce(response({ ...session, accessExpiresInMs: 0 }))
      .mockResolvedValueOnce(response({ message: "Expired" }, 401))
      .mockResolvedValueOnce(response({ ...session, message: "Token renovado" }))
      .mockResolvedValueOnce(response([{ id: 1, nome: "Produto" }]))
      .mockResolvedValueOnce(response({ token: "test-csrf" }))
      .mockResolvedValueOnce(response({ message: "Sessão encerrada" }));

    expect(await api.login(session.username, "test-password")).toEqual(session);
    expect(api.getStoredUser()).toMatchObject({ username: session.username, roles: ["ROLE_TENANT_ADMIN"] });
    expect(await api.validate()).toMatchObject({ username: session.username });
    expect(await api.getAdminProducts()).toEqual([{ id: 1, nome: "Produto" }]);
    await api.logout();
    expect(api.getStoredUser()).toBeNull();

    expect(fetchMock.mock.calls.map(([url]) => new URL(url).pathname)).toEqual([
      "/auth/csrf", "/auth/login", "/auth/csrf", "/auth/validate",
      "/painel/produtos", "/auth/refresh", "/painel/produtos", "/auth/csrf", "/auth/logout"
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
    localStorage.setItem("cardapio.authUser", JSON.stringify(session));
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
      .mockResolvedValueOnce(response({ token: "api-host-token", headerName: "X-CSRF-TOKEN" }))
      .mockResolvedValueOnce(response(session))
      .mockResolvedValueOnce(response({ token: "fresh-logout-token", headerName: "X-CSRF-TOKEN" }))
      .mockResolvedValueOnce(response({ message: "Sessão encerrada" }));
    await api.login(session.username, "test-password");
    expect(fetchMock.mock.calls[1][1].headers["X-CSRF-TOKEN"]).toBe("api-host-token");
    expect(fetchMock.mock.calls[1][1].headers).not.toHaveProperty("X-XSRF-TOKEN");
    await api.logout();
    expect(fetchMock.mock.calls[3][1].headers["X-CSRF-TOKEN"]).toBe("fresh-logout-token");
    expect(api.getStoredUser()).toBeNull();
  });

  it("keeps session metadata if the server rejects logout", async () => {
    localStorage.setItem("cardapio.authUser", JSON.stringify(session));
    fetchMock
      .mockResolvedValueOnce(response({ token: "test-csrf" }))
      .mockResolvedValueOnce(response({ message: "Acesso negado" }, 403));
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
    localStorage.setItem("cardapio.authUser", JSON.stringify(session));
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
});
