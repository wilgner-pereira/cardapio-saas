import { useMemo, useState } from "react";
import {
  Beef,
  ChevronDown,
  CupSoda,
  Fish,
  IceCreamBowl,
  Menu,
  Pencil,
  Plus,
  RefreshCw,
  Save,
  Trash2,
  Utensils,
  X
} from "./icons.js";

const categoryIcons = {
  peixes: Fish,
  peixe: Fish,
  porcoes: Utensils,
  porções: Utensils,
  bebidas: CupSoda,
  sobremesas: IceCreamBowl,
  carnes: Beef
};

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
  products,
  mode = "public",
  loading = false,
  error = "",
  onCreate,
  onEdit,
  onDelete,
  onToggleStatus,
  onRefresh,
  onLogout
}) {
  const [activeCategory, setActiveCategory] = useState("");
  const isAdmin = mode === "admin";
  const storeName = formatStoreName(username);
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
        <button className="round-icon hero-menu" type="button" aria-label="Abrir menu">
          <Menu size={25} />
        </button>

        {isAdmin && (
          <div className="admin-strip">
            <span>Editor</span>
            <button className="icon-button" type="button" onClick={onRefresh} aria-label="Atualizar">
              <RefreshCw size={18} />
            </button>
            <button className="icon-button" type="button" onClick={onLogout} aria-label="Sair">
              <X size={18} />
            </button>
          </div>
        )}

        <div className="hero-brand">
          <span className="brand-kicker">Cardapio digital</span>
          <h1>{storeName}</h1>
          <p>Natureza, lazer e boa comida em uma experiencia simples para pedir melhor.</p>
        </div>
      </section>

      <section className="paper">
        <div className="intro-row">
          <span className="reed-mark" aria-hidden="true">〽</span>
          <p>
            Sabores organizados por categoria, com itens frescos, imagens e disponibilidade em tempo real.
          </p>
        </div>

        <nav className="category-tabs" aria-label="Categorias do cardapio">
          {categories.map(category => {
            const Icon = categoryIcons[normalize(category)] || Utensils;
            const active = category === selectedCategory;
            return (
              <button
                key={category}
                type="button"
                className={`category-tab ${active ? "active" : ""}`}
                onClick={() => scrollToCategory(category)}
              >
                <Icon size={20} />
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

        {loading && <StatePanel title="Carregando cardapio" />}
        {error && <StatePanel title={error} tone="danger" />}
        {!loading && !error && categories.length === 0 && (
          <StatePanel title={isAdmin ? "Nenhum produto cadastrado" : "Cardapio indisponivel"} />
        )}

        {!loading && !error && categories.map(category => (
          <section className="menu-section" id={`category-${normalize(category)}`} key={category}>
            <div className="section-title-row">
              <div className="wood-title">
                {(() => {
                  const Icon = categoryIcons[normalize(category)] || Utensils;
                  return <Icon size={24} />;
                })()}
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

function ProductCard({ product, isAdmin, onEdit, onDelete, onToggleStatus }) {
  return (
    <article className={`product-card ${!product.ativo ? "inactive" : ""}`}>
      <div className="product-copy">
        <div className="product-title-row">
          <h3>{product.nome}</h3>
          {isAdmin && !product.ativo && <span className="status-pill">Pausado</span>}
        </div>
        <p>{product.descricao}</p>
        <strong>{formatCurrency(product.preco)}</strong>
      </div>

      <div className="product-image-wrap">
        {product.imageUrl ? (
          <img src={product.imageUrl} alt={product.nome} loading="lazy" />
        ) : (
          <div className="image-placeholder">
            <Utensils size={28} />
          </div>
        )}
      </div>

      {isAdmin && (
        <div className="product-actions" aria-label={`Acoes de ${product.nome}`}>
          <button className="mini-button" type="button" onClick={() => onEdit(product)} aria-label="Editar produto">
            <Pencil size={16} />
          </button>
          <button className="mini-button" type="button" onClick={() => onToggleStatus(product)} aria-label="Alterar disponibilidade">
            <Save size={16} />
          </button>
          <button className="mini-button danger" type="button" onClick={() => onDelete(product)} aria-label="Remover produto">
            <Trash2 size={16} />
          </button>
        </div>
      )}
    </article>
  );
}

function StatePanel({ title, tone = "neutral" }) {
  return (
    <div className={`state-panel ${tone}`}>
      {title}
    </div>
  );
}
