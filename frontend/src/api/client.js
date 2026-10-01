import { validateOptimizedImage } from "./uploadValidation.js";

const API_BASE = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";
const AUTH_USER_KEY = "cardapio.authUser";

let refreshPromise = null;
let csrfPromise = null;
let cachedCsrfToken = null;
let cachedCsrfHeader = "X-XSRF-TOKEN";
let currentUser = null;
let validationPromise = null;

function removeLegacySession() {
  // Upgrade old clients without trusting or migrating persisted personal data.
  for (const name of ["localStorage", "sessionStorage"]) {
    try {
      window[name].removeItem(AUTH_USER_KEY);
    } catch {
      // Cookie authentication also works when browser storage is unavailable.
    }
  }
}

removeLegacySession();

function saveSession(session) {
  if (session?.username) {
    currentUser = {
      userId: session.userId,
      username: session.username,
      estabelecimentoSlug: session.estabelecimentoSlug,
      roles: session.roles || []
    };
  }
}

function clearSession() {
  currentUser = null;
  removeLegacySession();
  cachedCsrfToken = null;
  cachedCsrfHeader = "X-XSRF-TOKEN";
}

function notifySessionExpired() {
  window.dispatchEvent(new CustomEvent("cardapio:session-expired"));
}

function getStoredUser() {
  return currentUser;
}

function hasStoredUser() {
  return getStoredUser() !== null;
}

function resolveImageUrl(url) {
  if (!url) {
    return "";
  }

  if (url.startsWith("/")) {
    return `${API_BASE}${url}`;
  }

  const supabaseFile = url.match(/\/storage\/v1\/object\/public\/[^\/]+\/(.+)$/);
  if (supabaseFile?.[1]) {
    return `${API_BASE}/public/storage/${encodeURIComponent(decodeURIComponent(supabaseFile[1]))}`;
  }

  return url;
}

async function readResponse(response) {
  const contentType = response.headers.get("content-type") || "";

  if (contentType.includes("application/json")) {
    return response.json();
  }

  return response.text();
}

async function doFetch(path, options = {}) {
  const {
    method = "GET",
    body,
    headers = {},
    form = false,
    auth = true
  } = options;

  const requestHeaders = { ...headers };

  if (!form && body !== undefined) {
    requestHeaders["Content-Type"] = "application/json";
  }

  const match = document.cookie.match(new RegExp('(^| )XSRF-TOKEN=([^;]+)'));
  const csrfToken = cachedCsrfToken || (match ? decodeURIComponent(match[2]) : null);

  if (csrfToken && ["POST", "PUT", "PATCH", "DELETE"].includes(method.toUpperCase())) {
    requestHeaders[cachedCsrfToken ? cachedCsrfHeader : "X-XSRF-TOKEN"] = csrfToken;
  }

  return fetch(`${API_BASE}${path}`, {
    method,
    credentials: "include",
    headers: requestHeaders,
    body: form ? body : body === undefined ? undefined : JSON.stringify(body)
  });
}

async function refreshAccessToken() {
  if (!refreshPromise) {
    refreshPromise = doFetch("/auth/refresh", {
      method: "POST",
      auth: false
    })
      .then(async response => {
        const data = await readResponse(response);

        if (!response.ok) {
          if (response.status === 401 || response.status === 404) {
            clearSession();
            notifySessionExpired();
          }
          const message = typeof data === "object" && data?.message
            ? data.message
            : "Nao foi possivel renovar a sessao";
          throw Object.assign(new Error(message), {
            sessionExpired: response.status === 401 || response.status === 404
          });
        }

        saveSession(data);
        await initializeCsrfToken({ force: true });
        return data;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }

  return refreshPromise;
}

async function request(path, options = {}) {
  const { auth = true, retry = true } = options;
  const method = (options.method || "GET").toUpperCase();
  const csrfExempt = path === "/auth/login" || path === "/auth/refresh";

  if (["POST", "PUT", "PATCH", "DELETE"].includes(method) && !csrfExempt) {
    await initializeCsrfToken();
  }

  let response = await doFetch(path, options);

  if (auth && retry && response.status === 401) {
    await refreshAccessToken();
    response = await doFetch(path, { ...options, retry: false });
  }

  const data = await readResponse(response);

  if (!response.ok) {
    const message = typeof data === "object" && data?.message
      ? data.message
      : "Nao foi possivel concluir a operacao";
    if (auth && response.status === 401) {
      clearSession();
      notifySessionExpired();
    }
    throw Object.assign(new Error(message), {
      sessionExpired: auth && response.status === 401,
      status: response.status,
      fieldErrors: typeof data === "object" ? data?.fieldErrors || {} : {}
    });
  }

  return data;
}

async function initializeCsrfToken({ force = false } = {}) {
  if (!force && cachedCsrfToken) {
    return cachedCsrfToken;
  }

  if (!csrfPromise) {
    csrfPromise = (async () => {
      const response = await doFetch("/auth/csrf", { auth: false });
      const csrf = await readResponse(response);

      if (!response.ok || !csrf?.token) {
        const message = typeof csrf === "object" && csrf?.message
          ? csrf.message
          : "Nao foi possivel inicializar a protecao CSRF";
        throw new Error(message);
      }

      cachedCsrfToken = csrf.token;
      cachedCsrfHeader = csrf.headerName === "X-CSRF-TOKEN" ? "X-CSRF-TOKEN" : "X-XSRF-TOKEN";
      return cachedCsrfToken;
    })().finally(() => {
      csrfPromise = null;
    });
  }

  return csrfPromise;
}

export const api = {
  getStoredUser,
  hasStoredUser,
  resolveImageUrl,

  async login(username, password) {
    clearSession();
    const session = await request("/auth/login", {
      method: "POST",
      body: { username, password },
      auth: false
    });
    saveSession(session);
    await initializeCsrfToken({ force: true });
    return session;
  },

  async logout() {
    const result = await request("/auth/logout", {
      method: "POST",
      auth: false
    });
    clearSession();
    return result;
  },

  validate() {
    if (!validationPromise) {
      validationPromise = (async () => {
        await initializeCsrfToken();
        let state = await request("/auth/session", { auth: false });
        if (!state.authenticated && state.refreshAvailable) {
          await refreshAccessToken();
          state = await request("/auth/session", { auth: false });
        }
        if (!state.authenticated || !state.session) {
          clearSession();
          notifySessionExpired();
          throw Object.assign(new Error("Sessão expirada"), { sessionExpired: true });
        }
        const session = state.session;
        saveSession(session);
        return session;
      })().finally(() => { validationPromise = null; });
    }
    return validationPromise;
  },

  async createFirstAdmin(payload, setupToken) {
    return request("/setup/admin", {
      method: "POST",
      body: payload,
      headers: { "X-Setup-Token": setupToken },
      auth: false
    });
  },

  async getPublicMenu(slug) {
    return request(`/public/${encodeURIComponent(slug)}/cardapio`, {
      auth: false
    });
  },

  async getPublicEstablishment(slug) {
    return request(`/public/${encodeURIComponent(slug)}/cardapio/info`, {
      auth: false
    });
  },

  async getAdminProducts() {
    return request("/painel/produtos");
  },

  getPlatformEstablishments() {
    return request("/plataforma/estabelecimentos");
  },

  getPlatformEstablishment(id) {
    return request(`/plataforma/estabelecimentos/${encodeURIComponent(id)}`);
  },

  createPlatformEstablishment(payload) {
    return request("/plataforma/estabelecimentos", { method: "POST", body: payload });
  },

  updatePlatformEstablishment(id, payload) {
    return request(`/plataforma/estabelecimentos/${encodeURIComponent(id)}`, { method: "PUT", body: payload });
  },

  updatePlatformClientPassword(id, password) {
    return request(`/plataforma/estabelecimentos/${encodeURIComponent(id)}/senha`, { method: "PATCH", body: { password } });
  },

  activatePlatformEstablishment(id) {
    return request(`/plataforma/estabelecimentos/${encodeURIComponent(id)}/ativar`, { method: "PATCH" });
  },

  deactivatePlatformEstablishment(id) {
    return request(`/plataforma/estabelecimentos/${encodeURIComponent(id)}/desativar`, { method: "PATCH" });
  },

  deletePlatformEstablishment(id) {
    return request(`/plataforma/estabelecimentos/${encodeURIComponent(id)}`, { method: "DELETE" });
  },

  createPlatformAdmin(payload) {
    return request("/plataforma/admins", { method: "POST", body: payload });
  },

  async getMyEstablishment() {
    return request("/painel/estabelecimento");
  },

  async updateMyEstablishment(payload) {
    return request("/painel/estabelecimento", {
      method: "PUT",
      body: payload
    });
  },

  async updateMyEstablishmentLogo(logoUrl) {
    return request(`/painel/estabelecimento/logo?logoUrl=${encodeURIComponent(logoUrl)}`, {
      method: "PUT"
    });
  },

  async updateMyEstablishmentTheme(tema, imagensRedondas) {
    const shapeParam = imagensRedondas === undefined ? "" : `&imagensRedondas=${imagensRedondas}`;
    return request(`/painel/estabelecimento/tema?tema=${encodeURIComponent(tema)}${shapeParam}`, {
      method: "PUT"
    });
  },

  async createProduct(payload) {
    return request("/painel/produtos", {
      method: "POST",
      body: payload
    });
  },

  async updateProduct(id, payload) {
    return request(`/painel/produtos/${id}`, {
      method: "PUT",
      body: payload
    });
  },

  async updateProductStatus(id, ativo) {
    return request(`/painel/produtos/${id}/status`, {
      method: "PATCH",
      body: { ativo }
    });
  },

  async updateProductOrder(id, ordem) {
    return request(`/painel/produtos/${id}/ordem`, {
      method: "PATCH",
      body: { ordem }
    });
  },

  async updateCategoryOrder(categoria, categoriaAlvo) {
    return request("/painel/categorias/ordem", {
      method: "PATCH",
      body: { categoria, categoriaAlvo }
    });
  },

  async deleteProduct(id) {
    return request(`/painel/produtos/${id}`, {
      method: "DELETE"
    });
  },

  async discardUnusedImage(url) {
    return request("/painel/storage/unused-image", { method: "DELETE", body: { url } });
  },

  async uploadImage(file, type) {
    validateOptimizedImage(file, type);
    const formData = new FormData();
    formData.append("file", file);
    formData.append("type", type);

    return request("/painel/storage/upload", {
      method: "POST",
      body: formData,
      form: true
    });
  }
};
