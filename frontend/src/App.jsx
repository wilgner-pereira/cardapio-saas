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

function sortProducts(products) {
  return [...products].sort((a, b) => {
    const categoryComparison = String(a.categoria || "").localeCompare(String(b.categoria || ""));
    if (categoryComparison !== 0) {
      return categoryComparison;
    }

    const orderComparison = (a.ordem ?? 0) - (b.ordem ?? 0);
    if (orderComparison !== 0) {
      return orderComparison;
    }

    return String(a.id).localeCompare(String(b.id));
  });
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
  const [actionError, setActionError] = useState("");
  const [editingProduct, setEditingProduct] = useState(null);
  const [establishment, setEstablishment] = useState(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [establishmentModalOpen, setEstablishmentModalOpen] = useState(false);

  const applyAdminSnapshot = useCallback((nextProducts, nextEstablishment) => {
    setProducts(nextProducts);
    setEstablishment(nextEstablishment);
  }, []);

  const loadAdminData = useCallback(async ({ silent = false } = {}) => {
    if (!silent) {
      setLoading(true);
      setActionError("");
    }
    setError("");
    try {
      const [productData, establishmentData] = await Promise.all([
        api.getAdminProducts(),
        api.getMyEstablishment()
      ]);
      applyAdminSnapshot(productData, establishmentData);
    } catch (err) {
      if (silent) {
        setActionError(current => current || err.message);
      } else {
        setError(err.message);
      }
    } finally {
      if (!silent) {
        setLoading(false);
      }
    }
  }, [applyAdminSnapshot]);

  useEffect(() => {
    if (auth.status === "anonymous") {
      navigate("/login");
      return;
    }

    if (auth.status === "authenticated") {
      loadAdminData();
    }
  }, [auth.status, loadAdminData]);

  function handleSubmit(payload, imageFile) {
    setActionError("");

    const productBeingEdited = editingProduct;
    const previewUrl = imageFile ? URL.createObjectURL(imageFile) : "";
    const optimisticPayload = imageFile
      ? { ...payload, imageUrl: previewUrl }
      : payload;
    setModalOpen(false);
    setEditingProduct(null);

    if (productBeingEdited) {
      const optimisticProduct = { ...productBeingEdited, ...optimisticPayload, _pending: true };
      setProducts(current => sortProducts(current.map(product => (
        product.id === productBeingEdited.id ? optimisticProduct : product
      ))));

      void (async () => {
        const persistedPayload = imageFile
          ? { ...payload, imageUrl: await api.uploadImage(imageFile) }
          : payload;
        return api.updateProduct(productBeingEdited.id, persistedPayload);
      })()
        .then(updatedProduct => {
          setProducts(current => sortProducts(current.map(product => (
            product.id === productBeingEdited.id
              ? { ...updatedProduct, _pending: false }
              : product
          ))));
        })
        .catch(err => {
          setProducts(current => sortProducts(current.map(product => (
            product.id === productBeingEdited.id ? productBeingEdited : product
          ))));
          setActionError(`Nao foi possivel salvar ${productBeingEdited.nome}: ${err.message}`);
        })
        .finally(() => {
          if (previewUrl) {
            URL.revokeObjectURL(previewUrl);
          }
        });
      return;
    }

    const temporaryId = `optimistic-${Date.now()}-${Math.random().toString(16).slice(2)}`;
    const categoryProducts = products.filter(product => product.categoria === payload.categoria);
    const nextOrder = categoryProducts.reduce(
      (highest, product) => Math.max(highest, product.ordem ?? -1),
      -1
    ) + 1;
    const optimisticProduct = {
      ...optimisticPayload,
      id: temporaryId,
      ativo: true,
      ordem: nextOrder,
      _pending: true
    };

    setProducts(current => sortProducts([...current, optimisticProduct]));

    void (async () => {
      const persistedPayload = imageFile
        ? { ...payload, imageUrl: await api.uploadImage(imageFile) }
        : payload;
      return api.createProduct(persistedPayload);
    })()
      .then(createdProduct => {
        setProducts(current => sortProducts(current.map(product => (
          product.id === temporaryId
            ? { ...createdProduct, _pending: false }
            : product
        ))));
      })
      .catch(err => {
        setProducts(current => current.filter(product => product.id !== temporaryId));
        setActionError(`Nao foi possivel criar ${payload.nome}: ${err.message}`);
      })
      .finally(() => {
        if (previewUrl) {
          URL.revokeObjectURL(previewUrl);
        }
      });
  }

  function handleEstablishmentSubmit(payload, logoFile) {
    setActionError("");
    const { logoUrl, ...establishmentPayload } = payload;
    const previousEstablishment = establishment;
    const previewUrl = logoFile ? URL.createObjectURL(logoFile) : "";
    const optimisticLogoUrl = previewUrl || logoUrl;

    setEstablishment(current => ({
      ...current,
      ...establishmentPayload,
      logoUrl: optimisticLogoUrl
    }));
    setEstablishmentModalOpen(false);

    void (async () => {
      try {
        const [establishmentResponse, uploadedLogoUrl] = await Promise.all([
          api.updateMyEstablishment(establishmentPayload),
          logoFile ? api.uploadImage(logoFile) : Promise.resolve(logoUrl)
        ]);
        let updated = establishmentResponse;
        if (uploadedLogoUrl !== (previousEstablishment?.logoUrl || "")) {
          updated = await api.updateMyEstablishmentLogo(uploadedLogoUrl || "");
        }
        setEstablishment(updated);
      } catch (err) {
        setEstablishment(previousEstablishment);
        setActionError(`Nao foi possivel salvar o estabelecimento: ${err.message}`);
      } finally {
        if (previewUrl) {
          URL.revokeObjectURL(previewUrl);
        }
      }
    })();
  }

  function handleDelete(product) {
    const confirmed = window.confirm(`Remover ${product.nome}?`);
    if (!confirmed) {
      return;
    }

    setActionError("");
    const previousIndex = products.findIndex(item => item.id === product.id);
    setProducts(current => current.filter(item => item.id !== product.id));

    void api.deleteProduct(product.id).catch(err => {
      setProducts(current => {
        if (current.some(item => item.id === product.id)) {
          return current;
        }
        const restored = [...current];
        restored.splice(Math.max(0, Math.min(previousIndex, restored.length)), 0, product);
        return restored;
      });
      setActionError(`Nao foi possivel remover ${product.nome}: ${err.message}`);
    });
  }

  function handleToggleStatus(product) {
    if (product._pending) {
      return;
    }

    setActionError("");
    const nextStatus = !product.ativo;
    setProducts(current => current.map(item => (
      item.id === product.id
        ? { ...item, ativo: nextStatus, _pending: true }
        : item
    )));

    void api.updateProductStatus(product.id, nextStatus)
      .then(updatedProduct => {
        setProducts(current => current.map(item => (
          item.id === product.id
            ? { ...updatedProduct, _pending: false }
            : item
        )));
      })
      .catch(err => {
        setProducts(current => current.map(item => (
          item.id === product.id ? product : item
        )));
        setActionError(`Nao foi possivel alterar ${product.nome}: ${err.message}`);
      });
  }

  function handleMoveProduct(product, direction) {
    if (product._pending) {
      return;
    }

    const sameCategory = products.filter(item => item.categoria === product.categoria);
    const currentIndex = sameCategory.findIndex(item => item.id === product.id);
    const targetIndex = currentIndex + direction;

    if (currentIndex < 0 || targetIndex < 0 || targetIndex >= sameCategory.length) {
      return;
    }

    const target = sameCategory[targetIndex];
    if (target._pending) {
      return;
    }

    setActionError("");

    setProducts(current => {
      const updated = current.map(item => {
        if (item.id === product.id) {
          return { ...item, ordem: targetIndex, _pending: true };
        }
        if (item.id === target.id) {
          return { ...item, ordem: currentIndex, _pending: true };
        }
        return item;
      });
      return sortProducts(updated);
    });

    void Promise.all([
      api.updateProductOrder(product.id, targetIndex),
      api.updateProductOrder(target.id, currentIndex)
    ])
      .then(() => {
        setProducts(current => current.map(item => (
          item.id === product.id || item.id === target.id
            ? { ...item, _pending: false }
            : item
        )));
      })
      .catch(async err => {
        setProducts(current => sortProducts(current.map(item => {
          if (item.id === product.id) {
            return product;
          }
          if (item.id === target.id) {
            return target;
          }
          return item;
        })));
        setActionError(`Nao foi possivel reordenar ${product.nome}: ${err.message}`);
        await loadAdminData({ silent: true });
      });
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
        actionError={actionError}
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
        onRefresh={() => loadAdminData()}
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
        />
      )}

      {establishmentModalOpen && (
        <EstablishmentEditorModal
          establishment={establishment}
          onClose={() => setEstablishmentModalOpen(false)}
          onSubmit={handleEstablishmentSubmit}
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
