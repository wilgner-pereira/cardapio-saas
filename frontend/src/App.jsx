import { useCallback, useEffect, useMemo, useState } from "react";
import { api } from "./api/client.js";
import { useAuth } from "./auth/AuthContext.jsx";
import { Loader2, Lock, Plus } from "./components/icons.js";
import { MenuShell } from "./components/MenuShell.jsx";
import { ProductEditorModal } from "./components/ProductEditorModal.jsx";
import { demoProducts } from "./data/demoMenu.js";

function getPath() {
  return window.location.pathname;
}

function navigate(path) {
  window.history.pushState(null, "", path);
  window.dispatchEvent(new PopStateEvent("popstate"));
}

function parseRoute(path) {
  const publicMatch = path.match(/^\/cardapio\/([^/]+)\/?$/);
  if (publicMatch) {
    return { name: "public", username: decodeURIComponent(publicMatch[1]) };
  }
  if (path.startsWith("/admin")) {
    return { name: "admin" };
  }
  if (path.startsWith("/login")) {
    return { name: "login" };
  }
  return { name: "public", username: "demo" };
}

export function App() {
  const [path, setPath] = useState(getPath);
  const route = useMemo(() => parseRoute(path), [path]);

  useEffect(() => {
    const onPopState = () => setPath(getPath());
    window.addEventListener("popstate", onPopState);
    return () => window.removeEventListener("popstate", onPopState);
  }, []);

  if (route.name === "login") {
    return <LoginPage onSuccess={() => navigate("/admin/produtos")} />;
  }

  if (route.name === "admin") {
    return <AdminMenuPage />;
  }

  return <PublicMenuPage username={route.username} />;
}

function PublicMenuPage({ username }) {
  const [products, setProducts] = useState(username === "demo" ? demoProducts : []);
  const [loading, setLoading] = useState(username !== "demo");
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    setError("");

    if (username === "demo") {
      setProducts(demoProducts);
      setLoading(false);
      return undefined;
    }

    setLoading(true);
    api.getPublicMenu(username)
      .then(data => {
        if (active) {
          setProducts(data);
        }
      })
      .catch(err => {
        if (active) {
          setError(err.message);
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [username]);

  return (
    <MenuShell
      username={username}
      products={products}
      loading={loading}
      error={error}
    />
  );
}

function AdminMenuPage() {
  const auth = useAuth();
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [editingProduct, setEditingProduct] = useState(null);
  const [modalOpen, setModalOpen] = useState(false);

  const loadProducts = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const data = await api.getAdminProducts();
      setProducts(data);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (auth.status === "anonymous") {
      navigate("/login");
      return;
    }

    if (auth.status === "authenticated") {
      loadProducts();
    }
  }, [auth.status, loadProducts]);

  async function handleSubmit(payload) {
    if (editingProduct) {
      await api.updateProduct(editingProduct.id, payload);
    } else {
      await api.createProduct(payload);
    }
    setModalOpen(false);
    setEditingProduct(null);
    await loadProducts();
  }

  async function handleDelete(product) {
    const confirmed = window.confirm(`Remover ${product.nome}?`);
    if (!confirmed) {
      return;
    }
    await api.deleteProduct(product.id);
    await loadProducts();
  }

  async function handleToggleStatus(product) {
    await api.updateProductStatus(product.id, !product.ativo);
    await loadProducts();
  }

  async function handleLogout() {
    await auth.logout();
    navigate("/login");
  }

  if (auth.status === "loading") {
    return (
      <div className="center-screen">
        <Loader2 className="spin" size={28} />
      </div>
    );
  }

  return (
    <>
      <MenuShell
        username={auth.username || "meu-cardapio"}
        mode="admin"
        products={products}
        loading={loading}
        error={error}
        onCreate={() => {
          setEditingProduct(null);
          setModalOpen(true);
        }}
        onEdit={(product) => {
          setEditingProduct(product);
          setModalOpen(true);
        }}
        onDelete={handleDelete}
        onToggleStatus={handleToggleStatus}
        onRefresh={loadProducts}
        onLogout={handleLogout}
      />

      <button
        className="floating-action"
        type="button"
        onClick={() => {
          setEditingProduct(null);
          setModalOpen(true);
        }}
        aria-label="Adicionar produto"
      >
        <Plus size={26} />
      </button>

      {modalOpen && (
        <ProductEditorModal
          product={editingProduct}
          onClose={() => {
            setModalOpen(false);
            setEditingProduct(null);
          }}
          onSubmit={handleSubmit}
          onUpload={api.uploadImage}
        />
      )}
    </>
  );
}

function LoginPage({ onSuccess }) {
  const auth = useAuth();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  async function handleSubmit(event) {
    event.preventDefault();
    setLoading(true);
    setError("");

    try {
      await auth.login({ username, password });
      onSuccess();
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="login-page">
      <section className="login-visual">
        <button className="round-icon" type="button" aria-label="Voltar ao cardapio" onClick={() => navigate("/cardapio/demo")}>
          <Lock size={22} />
        </button>
        <div>
          <span className="brand-kicker">Painel do estabelecimento</span>
          <h1>Cardapio vivo, editado no proprio visual.</h1>
        </div>
      </section>

      <section className="login-panel">
        <form className="login-form" onSubmit={handleSubmit}>
          <h2>Entrar</h2>

          <label>
            Usuario
            <input
              value={username}
              onChange={event => setUsername(event.target.value)}
              autoComplete="username"
              required
              minLength={3}
            />
          </label>

          <label>
            Senha
            <input
              type="password"
              value={password}
              onChange={event => setPassword(event.target.value)}
              autoComplete="current-password"
              required
              minLength={8}
            />
          </label>

          {error && <p className="form-error">{error}</p>}

          <button className="primary-action" type="submit" disabled={loading}>
            {loading ? <Loader2 className="spin" size={18} /> : null}
            Entrar
          </button>
        </form>
      </section>
    </main>
  );
}
