const API_BASE = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";

let csrfToken = null;

async function readResponse(response) {
  const contentType = response.headers.get("content-type") || "";

  if (contentType.includes("application/json")) {
    return response.json();
  }

  return response.text();
}

async function request(path, options = {}) {
  const {
    method = "GET",
    body,
    headers = {},
    csrf = false,
    form = false
  } = options;

  const requestHeaders = { ...headers };

  if (!form && body !== undefined) {
    requestHeaders["Content-Type"] = "application/json";
  }

  if (csrf) {
    requestHeaders["X-XSRF-TOKEN"] = await getCsrfToken();
  }

  const response = await fetch(`${API_BASE}${path}`, {
    method,
    credentials: "include",
    headers: requestHeaders,
    body: form ? body : body === undefined ? undefined : JSON.stringify(body)
  });

  const data = await readResponse(response);

  if (!response.ok) {
    const message = typeof data === "object" && data?.message
      ? data.message
      : "Nao foi possivel concluir a operacao";
    throw new Error(message);
  }

  return data;
}

export async function getCsrfToken() {
  if (csrfToken) {
    return csrfToken;
  }

  const data = await request("/auth/admin/csrf");
  csrfToken = data.token;
  return csrfToken;
}

export const api = {
  async login(username, password) {
    csrfToken = null;
    return request("/auth/admin/login", {
      method: "POST",
      body: { username, password }
    });
  },

  async logout() {
    csrfToken = null;
    return request("/auth/admin/logout", {
      method: "POST"
    });
  },

  async validate() {
    return request("/auth/admin/validate");
  },

  async getPublicMenu(username) {
    return request(`/public/${encodeURIComponent(username)}/cardapio`);
  },

  async getAdminProducts() {
    return request("/admin/produto");
  },

  async createProduct(payload) {
    return request("/admin/produto", {
      method: "POST",
      body: payload,
      csrf: true
    });
  },

  async updateProduct(id, payload) {
    return request(`/admin/produto/${id}`, {
      method: "PUT",
      body: payload,
      csrf: true
    });
  },

  async updateProductStatus(id, ativo) {
    return request(`/admin/produto/${id}/status`, {
      method: "PATCH",
      body: { ativo },
      csrf: true
    });
  },

  async deleteProduct(id) {
    return request(`/admin/produto/${id}`, {
      method: "DELETE",
      csrf: true
    });
  },

  async uploadImage(file) {
    const formData = new FormData();
    formData.append("file", file);

    return request("/storage/upload", {
      method: "POST",
      body: formData,
      form: true,
      csrf: true
    });
  }
};
