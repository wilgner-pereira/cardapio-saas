import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { api } from "./api/client.js";
import { useAuth } from "./auth/AuthContext.jsx";
import { ProtectedArea } from "./auth/ProtectedArea.jsx";
import { getAdminHome, PLATFORM_ADMIN, TENANT_ADMIN } from "./auth/roles.js";
import { PlatformPage } from "./platform/PlatformPage.jsx";
import { ArrowRight, Eye, EyeOff, Loader2, Lock, Plus, Search, UserRound, Utensils } from "./components/icons.js";
import { EstablishmentEditorModal } from "./components/EstablishmentEditorModal.jsx";
import { MenuShell } from "./components/MenuShell.jsx";
import { NotFoundState } from "./components/NotFoundState.jsx";
import { ProductEditorModal } from "./components/ProductEditorModal.jsx";
import { ThemePickerModal } from "./components/ThemePickerModal.jsx";

function getPath() {
  return window.location.pathname;
}

async function saveWithImage(file, type, currentUrl, save) {
  const nextUrl = file ? await api.uploadImage(file, type) : currentUrl;
  try {
    return await save(nextUrl);
  } catch (failure) {
    // The server checks references before deleting, including after an ambiguous save failure.
    if (file) void api.discardUnusedImage(nextUrl).catch(() => {});
    throw failure;
  }
}

function navigate(path, { replace = false } = {}) {
  window.history[replace ? "replaceState" : "pushState"](null, "", path);
  window.dispatchEvent(new PopStateEvent("popstate"));
}

function parseRoute(path) {
  const publicMatch = path.match(/^\/cardapio\/([^/]+)\/?$/);
  if (publicMatch) {
    try {
      return { name: "public", username: decodeURIComponent(publicMatch[1]) };
    } catch {
      return { name: "not-found" };
    }
  }
  const platformMatch = path.match(/^\/plataforma(?:\/(clientes|administradores)(?:\/(\d+))?)?\/?$/);
  if (platformMatch && !(platformMatch[1] === "administradores" && platformMatch[2])) {
    return { name: "platform", section: platformMatch[1] || "dashboard", clientId: platformMatch[2] };
  }
  if (/^\/painel(?:\/|$)/.test(path)) {
    return { name: "panel" };
  }
  if (/^\/login\/?$/.test(path)) {
    return { name: "login" };
  }
  return { name: path === "/" ? "home" : "not-found" };
}

function sortProducts(products) {
  // ⚡ Bolt Optimization: Optimized sort comparison function.
  // Avoids unnecessary localeCompare calls when category and ID strings match exactly,
  // preserving exact type safety and lexicographical sorting behavior.
  return [...products].sort((a, b) => {
    const sectionComparison = (a.categoriaOrdem ?? 0) - (b.categoriaOrdem ?? 0);
    if (sectionComparison !== 0) return sectionComparison;

    const catA = String(a.categoria || "");
    const catB = String(b.categoria || "");
    if (catA !== catB) {
      const categoryComparison = catA.localeCompare(catB);
      if (categoryComparison !== 0) return categoryComparison;
    }

    const orderComparison = (a.ordem ?? 0) - (b.ordem ?? 0);
    if (orderComparison !== 0) {
      return orderComparison;
    }

    if (a.id === b.id) return 0;
    const idA = String(a.id);
    const idB = String(b.id);
    if (idA !== idB) {
      return idA.localeCompare(idB);
    }
    return 0;
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
    return <LoginPage onSuccess={session => navigate(getAdminHome(session.roles), { replace: true })} />;
  }

  if (route.name === "panel") {
    return <ProtectedArea role={TENANT_ADMIN} onNavigate={navigate}><AdminMenuPage /></ProtectedArea>;
  }

  if (route.name === "platform") {
    return <ProtectedArea role={PLATFORM_ADMIN} onNavigate={navigate}>
      <PlatformPage section={route.section} clientId={route.clientId} onNavigate={navigate} />
    </ProtectedArea>;
  }

  if (route.name === "home") {
    return <HomePage />;
  }

  if (route.name === "public") {
    return <PublicMenuPage key={route.username} username={route.username} />;
  }

  return <NotFoundState Icon={Search} title="Página não encontrada"
    description="Desculpe, não encontramos esta página."
    detail="Confira o endereço e tente novamente." />;
}

function PublicMenuPage({ username }) {
  const [products, setProducts] = useState([]);
  const [establishment, setEstablishment] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notFound, setNotFound] = useState(false);

  useEffect(() => {
    let active = true;
    setError("");
    setNotFound(false);
    setLoading(true);
    const productRequest = api.getPublicMenu(username).then(
      data => ({ data }),
      failure => ({ failure })
    );
    api.getPublicEstablishment(username)
      .then(async establishmentData => {
        const productResult = await productRequest;
        if (!active) return;
        if ("failure" in productResult) {
          setError(productResult.failure?.message || "Não foi possível carregar o cardápio");
        } else {
          setProducts(productResult.data);
          setEstablishment(establishmentData);
        }
      })
      .catch(failure => {
        if (!active) return;
        if (failure?.status === 404) setNotFound(true);
        else setError(failure?.message || "Não foi possível carregar o cardápio");
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [username]);

  if (loading) {
    return <main className="center-screen" role="status">
      <Loader2 className="spin" size={28} aria-hidden="true" />
      <span>Carregando cardápio…</span>
    </main>;
  }

  if (notFound) {
    return <NotFoundState Icon={Utensils} title="Cardápio não encontrado"
      description="Desculpe, não encontramos um cardápio para este endereço."
      detail="Confira o link e tente novamente." />;
  }

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
  const dataLoadStartedRef = useRef(false);
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [actionError, setActionError] = useState("");
  const [editingProduct, setEditingProduct] = useState(null);
  const [establishment, setEstablishment] = useState(null);
  const [previewTheme, setPreviewTheme] = useState(null);
  const [previewRoundImages, setPreviewRoundImages] = useState(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [establishmentModalOpen, setEstablishmentModalOpen] = useState(false);
  const [heroPreview, setHeroPreview] = useState(null);
  const [themeModalOpen, setThemeModalOpen] = useState(false);
  const [categoryMoving, setCategoryMoving] = useState(false);
  const categoryMoveRef = useRef(false);
  const themeButtonRef = useRef(null);

  const savedTheme = establishment?.tema || "artesanal";
  const effectiveTheme = previewTheme ?? savedTheme;
  const savedRoundImages = establishment?.imagensRedondas ?? false;

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
    if (auth.status === "authenticated" && !dataLoadStartedRef.current) {
      dataLoadStartedRef.current = true;
      void loadAdminData();
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

      void saveWithImage(imageFile, "product", payload.imageUrl, imageUrl =>
        api.updateProduct(productBeingEdited.id, { ...payload, imageUrl }))
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
      categoriaOrdem: categoryProducts[0]?.categoriaOrdem ??
        (products.reduce((highest, product) => Math.max(highest, product.categoriaOrdem ?? -1), -1) + 1),
      _pending: true
    };

    setProducts(current => sortProducts([...current, optimisticProduct]));

    void saveWithImage(imageFile, "product", payload.imageUrl, imageUrl =>
      api.createProduct({ ...payload, imageUrl }))
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

  function handleEstablishmentSubmit(payload, heroFile) {
    setActionError("");
    const previousEstablishment = establishment;
    const heroPreviewUrl = heroFile ? URL.createObjectURL(heroFile) : "";

    setEstablishment(current => ({
      ...current,
      ...payload,
      heroImageUrl: heroPreviewUrl || payload.heroImageUrl
    }));
    setEstablishmentModalOpen(false);
    setHeroPreview(null);

    void (async () => {
      try {
        const updated = await saveWithImage(heroFile, "hero", payload.heroImageUrl, heroImageUrl =>
          api.updateMyEstablishment({ ...payload, heroImageUrl }));
        setEstablishment(updated);
      } catch (err) {
        setEstablishment(previousEstablishment);
        setActionError(`Nao foi possivel salvar o estabelecimento: ${err.message}`);
      } finally {
        if (heroPreviewUrl) URL.revokeObjectURL(heroPreviewUrl);
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

  async function handleMoveCategory(category, direction) {
    if (categoryMoveRef.current || products.some(product => product._pending)) return;
    const categories = [...new Set(sortProducts(products).map(product => product.categoria))];
    const index = categories.indexOf(category);
    const target = categories[index + direction];
    if (index < 0 || !target) return;
    categoryMoveRef.current = true;
    setCategoryMoving(true);
    setActionError("");
    try {
      const persisted = await api.updateCategoryOrder(category, target);
      const ranks = new Map(persisted.map(product => [product.categoria, product.categoriaOrdem]));
      setProducts(current => sortProducts(current.map(product => ({
        ...product, categoriaOrdem: ranks.get(product.categoria) ?? product.categoriaOrdem
      }))));
    } catch (err) {
      setActionError(`Nao foi possivel reordenar ${category}: ${err.message}`);
      await loadAdminData({ silent: true });
    } finally {
      categoryMoveRef.current = false;
      setCategoryMoving(false);
    }
  }

  function handleMoveProduct(product, direction) {
    if (categoryMoveRef.current) return;
    if (product._pending) {
      return;
    }

    const globalIndex = products.findIndex(item => item.id === product.id);
    if (globalIndex < 0) return;

    const targetGlobalIndex = globalIndex + direction;
    const target = products[targetGlobalIndex];

    if (!target || target.categoria !== product.categoria) {
      return;
    }

    if (target._pending) {
      return;
    }

    let categoryStartIndex = globalIndex;
    while (categoryStartIndex > 0 && products[categoryStartIndex - 1].categoria === product.categoria) {
      categoryStartIndex--;
    }

    const currentIndex = globalIndex - categoryStartIndex;
    const targetIndex = targetGlobalIndex - categoryStartIndex;

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

  return (
    <>
      <MenuShell
        username={establishment?.slug || auth.estabelecimentoSlug || auth.username || "meu-cardapio"}
        establishment={heroPreview === null ? establishment : { ...establishment, heroImageUrl: heroPreview }}
        theme={effectiveTheme}
        roundImages={previewRoundImages ?? savedRoundImages}
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
        onEditTheme={() => {
          setPreviewTheme(null);
          setPreviewRoundImages(null);
          setThemeModalOpen(true);
        }}
        onEditEstablishment={() => setEstablishmentModalOpen(true)}
        onDelete={handleDelete}
        onMoveProduct={handleMoveProduct}
        onMoveCategory={handleMoveCategory}
        categoryMoving={categoryMoving}
        onToggleStatus={handleToggleStatus}
        onRefresh={() => loadAdminData()}
        onLogout={handleLogout}
        themeButtonRef={themeButtonRef}
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
          theme={effectiveTheme}
          onHeroPreview={setHeroPreview}
          onClose={() => setEstablishmentModalOpen(false)}
          onSubmit={handleEstablishmentSubmit}
        />
      )}

      {themeModalOpen && (
        <ThemePickerModal
          isOpen={themeModalOpen}
          currentTheme={savedTheme}
          currentRoundImages={savedRoundImages}
          onSelectPreview={(themeId) => setPreviewTheme(themeId)}
          onSelectImageShapePreview={setPreviewRoundImages}
          onClose={() => {
            setPreviewTheme(null);
            setPreviewRoundImages(null);
            setThemeModalOpen(false);
          }}
          onSubmit={async (selectedTheme, selectedRoundImages) => {
            const updated = await api.updateMyEstablishmentTheme(selectedTheme, selectedRoundImages);
            setEstablishment(updated);
            setPreviewTheme(null);
            setPreviewRoundImages(null);
            setThemeModalOpen(false);
          }}
          triggerRef={themeButtonRef}
        />
      )}
    </>
  );
}

function LoginPage({ onSuccess }) {
  const auth = useAuth();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [passwordVisible, setPasswordVisible] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  async function handleSubmit(event) {
    event.preventDefault();
    setLoading(true);
    setError("");

    try {
      const session = await auth.login({ username, password });
      if (!getAdminHome(session.roles)) throw new Error("Sua conta não possui acesso a uma área administrativa.");
      onSuccess(session);
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
          <Lock size={22} aria-hidden="true" />
        </button>
        <div className="login-copy">
          <div className="login-brand">
            <span className="login-brand-name">Lune Systems</span>
            <p>Tecnologia para simplificar e acelerar o seu negócio.</p>
          </div>
          <h1>
            <span>Seu cardápio e</span>{" "}
            <span>sua gestão em</span>{" "}
            <span>um só lugar.</span>
          </h1>
          <p className="login-intro">
            <span>Mais que um cardápio digital.</span>
            <span>Uma gestão completa para o</span>
            <span>seu negócio crescer.</span>
          </p>
          <p className="login-services">Sistemas comerciais, CRM, cardápios digitais, automações e integrações com WhatsApp.</p>
        </div>
      </section>

      <section className="login-panel">
        <form className="login-form" onSubmit={handleSubmit} aria-busy={loading} aria-labelledby="login-title">
          <div className="login-form-header">
            <h2 id="login-title">Entrar</h2>
            <p>Acesse sua conta para gerenciar seu cardápio e muito mais.</p>
          </div>

          <div className="login-field">
            <label htmlFor="login-username">Usuário</label>
            <div className="login-input-wrap">
              <UserRound className="login-input-icon" size={20} aria-hidden="true" />
              <input
                id="login-username"
                value={username}
                onChange={event => setUsername(event.target.value)}
                placeholder="Digite seu usuário"
                autoComplete="username"
                aria-describedby={error ? "login-error" : undefined}
                required
                minLength={3}
              />
            </div>
          </div>

          <div className="login-field">
            <label htmlFor="login-password">Senha</label>
            <div className="login-input-wrap">
              <Lock className="login-input-icon" size={20} aria-hidden="true" />
              <input
                id="login-password"
                type={passwordVisible ? "text" : "password"}
                value={password}
                onChange={event => setPassword(event.target.value)}
                placeholder="Digite sua senha"
                autoComplete="current-password"
                aria-describedby={error ? "login-error" : undefined}
                required
                minLength={8}
              />
              <button
                className="login-password-toggle"
                type="button"
                aria-label={passwordVisible ? "Ocultar senha" : "Mostrar senha"}
                aria-pressed={passwordVisible}
                aria-controls="login-password"
                onClick={() => setPasswordVisible(visible => !visible)}
              >
                {passwordVisible ? <EyeOff size={19} aria-hidden="true" /> : <Eye size={19} aria-hidden="true" />}
              </button>
            </div>
          </div>

          {error && <p id="login-error" className="form-error" role="alert">{error}</p>}

          <button className="primary-action" type="submit" disabled={loading}>
            {loading ? <Loader2 className="spin" size={18} aria-hidden="true" /> : null}
            Entrar
            {!loading && <ArrowRight size={18} aria-hidden="true" />}
          </button>
        </form>
      </section>
    </main>
  );
}
