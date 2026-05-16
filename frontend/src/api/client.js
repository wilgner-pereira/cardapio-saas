const API_BASE = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";
const ACCESS_TOKEN_KEY = "cardapio.accessToken";
const AUTH_USER_KEY = "cardapio.authUser";

let accessToken = localStorage.getItem(ACCESS_TOKEN_KEY) || "";
let refreshPromise = null;

function saveSession(session) {
  accessToken = session?.accessToken || "";

  if (accessToken) {
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
  } else {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
  }

  if (session?.username) {
    localStorage.setItem(AUTH_USER_KEY, JSON.stringify({
      userId: session.userId,
      username: session.username,
      email: session.email,
      estabelecimentoSlug: session.estabelecimentoSlug,
      roles: session.roles || []
    }));
  }
}

function clearSession() {
  accessToken = "";
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(AUTH_USER_KEY);
}

function notifySessionExpired() {
  window.dispatchEvent(new CustomEvent("cardapio:session-expired"));
}

function getStoredUser() {
  try {
    return JSON.parse(localStorage.getItem(AUTH_USER_KEY) || "null");
  } catch {
    return null;
  }
}

function hasAccessToken() {
  return Boolean(accessToken);
}

function resolveImageUrl(url) {
  if (!url) {
    return "";
  }

  if (url.startsWith("/")) {
    return `${API_BASE}${url}`;
  }

  const supabaseFile = url.match(/\/storage\/v1\/object\/public\/[^/]+\/(.+)$/);
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

  if (auth && accessToken) {
    requestHeaders.Authorization = `Bearer ${accessToken}`;
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
          clearSession();
          notifySessionExpired();
          const message = typeof data === "object" && data?.message
            ? data.message
            : "Sessao expirada";
          throw new Error(message);
        }

        saveSession(data);
        return data.accessToken;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }

  return refreshPromise;
}

async function request(path, options = {}) {
  const { auth = true, retry = true } = options;
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
    throw new Error(message);
  }

  return data;
}

export const api = {
  getStoredUser,
  hasAccessToken,
  resolveImageUrl,

  async login(username, password) {
    clearSession();
    const session = await request("/auth/login", {
      method: "POST",
      body: { username, password },
      auth: false
    });
    saveSession(session);
    return session;
  },

  async logout() {
    try {
      return await request("/auth/logout", {
        method: "POST",
        auth: false
      });
    } finally {
      clearSession();
    }
  },

  async validate() {
    if (!hasAccessToken()) {
      clearSession();
      throw new Error("Sessao ausente");
    }

    const session = await request("/auth/validate");
    saveSession({ ...session, accessToken });
    return session;
  },

  async createFirstAdmin(payload) {
    return request("/setup/admin", {
      method: "POST",
      body: payload,
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

  async deleteProduct(id) {
    return request(`/painel/produtos/${id}`, {
      method: "DELETE"
    });
  },

  async uploadImage(file) {
    const formData = new FormData();
    formData.append("file", file);

    return request("/painel/storage/upload", {
      method: "POST",
      body: formData,
      form: true
    });
  }
};
