import { useEffect, useMemo, useState } from "react";
import { api } from "../api/client.js";
import {
  ArrowDown,
  ArrowUp,
  ChevronDown,
  Eye,
  EyeOff,
  MapPin,
  Pencil,
  Phone,
  Plus,
  RefreshCw,
  Trash2,
  Utensils,
  X
} from "./icons.js";

function normalize(value) {
  return value
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "");
}

function formatStoreName(username) {
  return username
    .split(/[._-]/)
    .filter(Boolean)
    .map(part => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ") || "Cardapio";
}

function formatCurrency(value) {
  const number = Number(value || 0);
  return number.toLocaleString("pt-BR", {
    style: "currency",
    currency: "BRL"
  });
}

function groupByCategory(products, includeUnavailable) {
  return products
    .filter(product => includeUnavailable || product.ativo)
    .reduce((groups, product) => {
      const category = product.categoria || "Cardapio";
      if (!groups.has(category)) {
        groups.set(category, []);
      }
      groups.get(category).push(product);
      return groups;
    }, new Map());
}

export function MenuShell({
  username,
  establishment,
  products,
  mode = "public",
  loading = false,
  error = "",
  onCreate,
  onEdit,
  onEditEstablishment,
  onDelete,
  onMoveProduct,
  onToggleStatus,
  onRefresh,
  onLogout
}) {
  const [activeCategory, setActiveCategory] = useState("");
  const isAdmin = mode === "admin";
  const storeName = establishment?.nome || formatStoreName(username);
  const description = establishment?.descricao || "";
  const grouped = useMemo(() => groupByCategory(products, isAdmin), [isAdmin, products]);
  const categories = Array.from(grouped.keys());
  const selectedCategory = activeCategory && grouped.has(activeCategory) ? activeCategory : categories[0];

  function scrollToCategory(category) {
    setActiveCategory(category);
    document.getElementById(`category-${normalize(category)}`)?.scrollIntoView({
      behavior: "smooth",
      block: "start"
    });
  }

  return (
    <main className="menu-page">
      <section className="hero">
        {isAdmin && (
          <div className="admin-strip">
            <span>Editor</span>
            <button className="icon-button" type="button" onClick={onEditEstablishment} aria-label="Editar estabelecimento">
              <Pencil size={18} />
            </button>
            <button className="icon-button" type="button" onClick={onRefresh} aria-label="Atualizar">
              <RefreshCw size={18} />
            </button>
            <button className="icon-button" type="button" onClick={onLogout} aria-label="Sair">
              <X size={18} />
            </button>
          </div>
        )}

        <div className="hero-content">
          <HeroLogo logoUrl={establishment?.logoUrl} storeName={storeName} />
          <div className="hero-title">
            <span>{isAdmin ? "Editor visual" : "Cardapio digital"}</span>
            <h1>{storeName}</h1>
          </div>
        </div>
      </section>

      <section className="paper">
        <nav className="category-tabs" aria-label="Categorias do cardapio">
          {categories.map(category => {
            const active = category === selectedCategory;
            return (
              <button
                key={category}
                type="button"
                className={`category-tab ${active ? "active" : ""}`}
                onClick={() => scrollToCategory(category)}
              >
                <span>{category}</span>
              </button>
            );
          })}
          {isAdmin && (
            <button type="button" className="category-tab add-tab" onClick={onCreate}>
              <Plus size={20} />
              <span>Novo</span>
            </button>
          )}
        </nav>

        <div className="intro-row">
          <span className="reed-mark" aria-hidden="true">〽</span>
          <div className="intro-copy">
            <p>
              {description || `Cardapio digital de ${storeName}.`}
            </p>
            {(establishment?.telefone || establishment?.endereco) && (
              <div className="store-meta">
                {establishment?.telefone && (
                  <span><Phone size={15} /> {establishment.telefone}</span>
                )}
                {establishment?.endereco && (
                  <span><MapPin size={15} /> {establishment.endereco}</span>
                )}
              </div>
            )}
          </div>
        </div>

        {loading && <StatePanel title="Carregando cardapio" />}
        {error && <StatePanel title={error} tone="danger" />}
        {!loading && !error && categories.length === 0 && (
          <StatePanel title={isAdmin ? "Nenhum produto cadastrado" : "Cardapio indisponivel"} />
        )}

        {!loading && !error && categories.map(category => (
          <section className="menu-section" id={`category-${normalize(category)}`} key={category}>
            <div className="section-title-row">
              <div className="wood-title">
                <h2>{category}</h2>
              </div>
              <div className="ornament" aria-hidden="true" />
            </div>

            <div className="product-list">
              {grouped.get(category).map(product => (
                <ProductCard
                  key={product.id}
                  product={product}
                  isAdmin={isAdmin}
                  onEdit={onEdit}
                  onDelete={onDelete}
                  onMoveProduct={onMoveProduct}
                  onToggleStatus={onToggleStatus}
                />
              ))}
            </div>
          </section>
        ))}

        <div className="sun-divider" aria-hidden="true">
          <span />
          <ChevronDown size={22} />
          <span />
        </div>
      </section>
    </main>
  );
}

function ProductCard({ product, isAdmin, onEdit, onDelete, onMoveProduct, onToggleStatus }) {
  const hasImage = Boolean(product.imageUrl);

  return (
    <article className={`product-card ${hasImage ? "has-image" : ""} ${isAdmin ? "admin-card" : ""} ${!product.ativo ? "inactive" : ""}`}>
      <div className="product-copy">
        <div className="product-title-row">
          <h3>{product.nome}</h3>
          {isAdmin && !product.ativo && <span className="status-pill">Pausado</span>}
        </div>
        <p>{product.descricao}</p>
        <strong>{formatCurrency(product.preco)}</strong>
      </div>

      {hasImage && (
        <ProductImage imageUrl={product.imageUrl} name={product.nome} />
      )}

      {isAdmin && (
        <div className="product-actions" aria-label={`Acoes de ${product.nome}`}>
          <button className="mini-button" type="button" onClick={() => onEdit(product)} aria-label="Editar produto">
            <Pencil size={16} />
          </button>
          <button className="mini-button" type="button" onClick={() => onMoveProduct(product, -1)} aria-label="Subir produto">
            <ArrowUp size={16} />
          </button>
          <button className="mini-button" type="button" onClick={() => onMoveProduct(product, 1)} aria-label="Descer produto">
            <ArrowDown size={16} />
          </button>
          <button className="mini-button" type="button" onClick={() => onToggleStatus(product)} aria-label="Alterar disponibilidade">
            {product.ativo ? <EyeOff size={16} /> : <Eye size={16} />}
          </button>
          <button className="mini-button danger" type="button" onClick={() => onDelete(product)} aria-label="Remover produto">
            <Trash2 size={16} />
          </button>
        </div>
      )}
    </article>
  );
}

function HeroLogo({ logoUrl, storeName }) {
  const [failed, setFailed] = useState(false);
  const resolvedLogoUrl = api.resolveImageUrl(logoUrl);

  useEffect(() => {
    setFailed(false);
  }, [resolvedLogoUrl]);

  if (!resolvedLogoUrl || failed) {
    return (
      <div className="hero-logo hero-logo-fallback" aria-hidden="true">
        <Utensils size={34} />
      </div>
    );
  }

  return (
    <img
      className="hero-logo"
      src={resolvedLogoUrl}
      alt={`Logo ${storeName}`}
      onError={() => setFailed(true)}
    />
  );
}

function ProductImage({ imageUrl, name }) {
  const [failed, setFailed] = useState(false);
  const resolvedImageUrl = api.resolveImageUrl(imageUrl);

  useEffect(() => {
    setFailed(false);
  }, [resolvedImageUrl]);

  if (!resolvedImageUrl || failed) {
    return null;
  }

  return (
    <div className="product-image-wrap">
      <img src={resolvedImageUrl} alt={name} loading="lazy" onError={() => setFailed(true)} />
    </div>
  );
}

function StatePanel({ title, tone = "neutral" }) {
  return (
    <div className={`state-panel ${tone}`}>
      {title}
    </div>
  );
}
