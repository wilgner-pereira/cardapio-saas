import { useCallback, useEffect, useMemo, useState } from "react";
import { api } from "./api/client.js";
import { useAuth } from "./auth/AuthContext.jsx";
import { Loader2, Lock, Plus } from "./components/icons.js";
import { EstablishmentEditorModal } from "./components/EstablishmentEditorModal.jsx";
import { MenuShell } from "./components/MenuShell.jsx";
import { ProductEditorModal } from "./components/ProductEditorModal.jsx";

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
  if (path.startsWith("/painel")) {
    return { name: "panel" };
  }
  if (path.startsWith("/login")) {
    return { name: "login" };
  }
  return { name: "home" };
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
    return <LoginPage onSuccess={() => navigate("/painel/produtos")} />;
  }

  if (route.name === "panel") {
    return <AdminMenuPage />;
  }

  if (route.name === "home") {
    return <HomePage />;
  }

  return <PublicMenuPage username={route.username} />;
}

function PublicMenuPage({ username }) {
  const [products, setProducts] = useState([]);
  const [establishment, setEstablishment] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    setError("");

    setLoading(true);
    Promise.all([
      api.getPublicMenu(username),
      api.getPublicEstablishment(username)
    ])
      .then(([productData, establishmentData]) => {
        if (active) {
          setProducts(productData);
          setEstablishment(establishmentData);
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
      establishment={establishment}
      products={products}
      loading={loading}
      error={error}
    />
  );
}

function HomePage() {
  return (
    <main className="home-page">
      <section className="home-panel">
        <span className="brand-kicker">Cardapio digital</span>
        <h1>Abra um cardapio pelo endereco do estabelecimento.</h1>
        <p>Use uma URL como /cardapio/restaurante, /cardapio/pizzaria ou entre no painel para editar seu proprio cardapio.</p>
        <div className="home-actions">
          <button className="primary-action" type="button" onClick={() => navigate("/login")}>
            Entrar no painel
          </button>
        </div>
      </section>
    </main>
  );
}

function AdminMenuPage() {
  const auth = useAuth();
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [editingProduct, setEditingProduct] = useState(null);
  const [establishment, setEstablishment] = useState(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [establishmentModalOpen, setEstablishmentModalOpen] = useState(false);

  const loadAdminData = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [productData, establishmentData] = await Promise.all([
        api.getAdminProducts(),
        api.getMyEstablishment()
      ]);
      setProducts(productData);
      setEstablishment(establishmentData);
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
      loadAdminData();
    }
  }, [auth.status, loadAdminData]);

  async function handleSubmit(payload) {
    if (editingProduct) {
      await api.updateProduct(editingProduct.id, payload);
    } else {
      await api.createProduct(payload);
    }
    setModalOpen(false);
    setEditingProduct(null);
    await loadAdminData();
  }

  async function handleEstablishmentSubmit(payload) {
    const { logoUrl, ...establishmentPayload } = payload;
    let updated = await api.updateMyEstablishment(establishmentPayload);

    if (logoUrl !== (establishment?.logoUrl || "")) {
      updated = await api.updateMyEstablishmentLogo(logoUrl || "");
    }

    setEstablishment(updated);
    setEstablishmentModalOpen(false);
    await loadAdminData();
  }

  async function handleDelete(product) {
    const confirmed = window.confirm(`Remover ${product.nome}?`);
    if (!confirmed) {
      return;
    }
    await api.deleteProduct(product.id);
    await loadAdminData();
  }

  async function handleToggleStatus(product) {
    await api.updateProductStatus(product.id, !product.ativo);
    await loadAdminData();
  }

  async function handleMoveProduct(product, direction) {
    const sameCategory = products.filter(item => item.categoria === product.categoria);
    const currentIndex = sameCategory.findIndex(item => item.id === product.id);
    const targetIndex = currentIndex + direction;

    if (currentIndex < 0 || targetIndex < 0 || targetIndex >= sameCategory.length) {
      return;
    }

    const target = sameCategory[targetIndex];

    await Promise.all([
      api.updateProductOrder(product.id, targetIndex),
      api.updateProductOrder(target.id, currentIndex)
    ]);
    await loadAdminData();
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
        username={establishment?.slug || auth.estabelecimentoSlug || auth.username || "meu-cardapio"}
        establishment={establishment}
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
        onEditEstablishment={() => setEstablishmentModalOpen(true)}
        onDelete={handleDelete}
        onMoveProduct={handleMoveProduct}
        onToggleStatus={handleToggleStatus}
        onRefresh={loadAdminData}
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

      {establishmentModalOpen && (
        <EstablishmentEditorModal
          establishment={establishment}
          onClose={() => setEstablishmentModalOpen(false)}
          onSubmit={handleEstablishmentSubmit}
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
        <button className="round-icon" type="button" aria-label="Voltar" onClick={() => navigate("/")}>
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
