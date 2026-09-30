import React from "react";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { App } from "./App.jsx";
import { AuthProvider } from "./auth/AuthContext.jsx";
import { api } from "./api/client.js";

vi.mock("./api/client.js", () => ({
  api: {
    getStoredUser: vi.fn(() => null),
    validate: vi.fn(),
    login: vi.fn(),
    logout: vi.fn(),
    resolveImageUrl: vi.fn(url => url || ""),
    getPublicMenu: vi.fn(),
    getPublicEstablishment: vi.fn(),
    getAdminProducts: vi.fn(),
    getMyEstablishment: vi.fn(),
    updateCategoryOrder: vi.fn(),
    updateMyEstablishment: vi.fn(),
    updateMyEstablishmentTheme: vi.fn(),
    uploadImage: vi.fn(),
    discardUnusedImage: vi.fn(),
    updateProduct: vi.fn(),
    createProduct: vi.fn(),
    getPlatformEstablishments: vi.fn()
  }
}));

const session = {
  userId: 42,
  username: "owner",
  roles: ["ROLE_TENANT_ADMIN"],
  estabelecimentoSlug: "pizzaria"
};

function renderApp(path, { strict = false } = {}) {
  window.history.replaceState(null, "", path);
  const tree = <AuthProvider><App /></AuthProvider>;
  return render(strict ? <React.StrictMode>{tree}</React.StrictMode> : tree);
}

describe("route-scoped session validation", () => {
  it("previews, cancels, retries and persists product image shape", async () => {
    api.validate.mockResolvedValue(session);
    api.getAdminProducts.mockResolvedValue([]);
    api.getMyEstablishment.mockResolvedValue({ nome: "Pizzaria", tema: "artesanal", imagensRedondas: false });
    const { container } = renderApp("/painel/produtos");
    await screen.findByText("Nenhum produto cadastrado");
    const menu = container.querySelector('.menu-page');
    fireEvent.click(screen.getByRole('button', { name: 'Alterar tema do cardápio' }));
    fireEvent.click(screen.getByRole('radio', { name: 'Redondas' }));
    expect(menu).toHaveAttribute('data-image-shape', 'round');
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }));
    expect(menu).toHaveAttribute('data-image-shape', 'square');
    expect(api.updateMyEstablishmentTheme).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: 'Alterar tema do cardápio' }));
    fireEvent.click(screen.getByRole('radio', { name: 'Redondas' }));
    api.updateMyEstablishmentTheme.mockRejectedValueOnce(new Error('Falha ao salvar'));
    fireEvent.click(screen.getByRole('button', { name: 'Salvar aparência' }));
    await screen.findByRole('alert');
    expect(screen.getByRole('radio', { name: 'Redondas' })).toHaveAttribute('aria-checked', 'true');
    expect(menu).toHaveAttribute('data-image-shape', 'round');
    api.updateMyEstablishmentTheme.mockResolvedValueOnce({ nome: 'Pizzaria', tema: 'artesanal', imagensRedondas: true });
    fireEvent.click(screen.getByRole('button', { name: 'Salvar aparência' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(api.updateMyEstablishmentTheme).toHaveBeenLastCalledWith('artesanal', true);
    expect(menu).toHaveAttribute('data-image-shape', 'round');
    fireEvent.click(screen.getByRole('button', { name: 'Alterar tema do cardápio' }));
    expect(screen.getByRole('radio', { name: 'Redondas' })).toHaveAttribute('aria-checked', 'true');
  });
  it.each(["product", "cover"])("discards only the new upload and restores the previous %s when saving fails", async kind => {
    URL.createObjectURL = vi.fn(() => "blob:http://localhost/new-image");
    URL.revokeObjectURL = vi.fn();
    const previousUrl = "https://example.com/previous.jpg";
    const uploadedUrl = "https://example.com/new-upload.png";
    const product = { id: 1, nome: "Pizza testes", descricao: "Pizza para testes", preco: 12.5, categoria: "Pizzas", ativo: true, imageUrl: previousUrl };
    api.validate.mockResolvedValue(session);
    api.getAdminProducts.mockResolvedValue([product]);
    api.getMyEstablishment.mockResolvedValue({ nome: "Pizzaria", heroImageUrl: previousUrl });
    api.uploadImage.mockResolvedValue(uploadedUrl);
    api.updateProduct.mockRejectedValue(new Error("save refused"));
    api.updateMyEstablishment.mockRejectedValue(new Error("save refused"));
    const { container } = renderApp("/painel/produtos");
    await screen.findByText("Pizza testes");
    fireEvent.click(screen.getByRole("button", { name: kind === "product" ? "Editar produto" : "Editar estabelecimento" }));
    fireEvent.change(container.querySelector('input[type="file"]'), {
      target: { files: [new File(["image"], "new.png", { type: "image/png" })] }
    });
    fireEvent.click(screen.getByRole("button", { name: "Salvar" }));
    await waitFor(() => expect(api.discardUnusedImage).toHaveBeenCalledWith(uploadedUrl));
    expect(api.discardUnusedImage).not.toHaveBeenCalledWith(previousUrl);
    expect(screen.getByText(/save refused/)).toBeInTheDocument();
    const selector = kind === "product" ? '.product-image-wrap img' : '.menu-page .hero-cover';
    expect(container.querySelector(selector)).toHaveAttribute("src", previousUrl);
  });

  it("restores the saved cover when editing is cancelled", async () => {
    URL.createObjectURL = vi.fn(() => "blob:http://localhost/draft");
    URL.revokeObjectURL = vi.fn();
    api.validate.mockResolvedValue(session);
    api.getAdminProducts.mockResolvedValue([]);
    api.getMyEstablishment.mockResolvedValue({ nome: "Pizzaria", heroImageUrl: "https://example.com/saved.jpg" });
    const { container } = renderApp("/painel/produtos");
    await screen.findByText("Nenhum produto cadastrado");
    fireEvent.click(screen.getByRole("button", { name: "Editar estabelecimento" }));
    fireEvent.change(screen.getByLabelText("Imagem de capa"), { target: { files: [new File(["image"], "cover.jpg", { type: "image/jpeg" })] } });
    expect(container.querySelector(".menu-page .hero-cover")).toHaveAttribute("src", "blob:http://localhost/draft");
    fireEvent.click(screen.getByRole("button", { name: "Fechar" }));
    expect(container.querySelector(".menu-page .hero-cover")).toHaveAttribute("src", "https://example.com/saved.jpg");
    expect(api.uploadImage).not.toHaveBeenCalled();
    expect(api.updateMyEstablishment).not.toHaveBeenCalled();
  });
  it("previews a cover before saving, uploads through the existing client and reloads the saved cover", async () => {
    URL.createObjectURL = vi.fn(() => "blob:http://localhost/cover");
    URL.revokeObjectURL = vi.fn();
    const establishment = { nome: "Pizzaria", slug: "pizzaria", heroImageUrl: "" };
    const persisted = { ...establishment, heroImageUrl: "https://example.com/cover.jpg" };
    api.validate.mockResolvedValue(session);
    api.getAdminProducts.mockResolvedValue([]);
    api.getMyEstablishment.mockResolvedValue(establishment);
    api.uploadImage.mockResolvedValue(persisted.heroImageUrl);
    api.updateMyEstablishment.mockResolvedValue(persisted);
    const { container, unmount } = renderApp("/painel/produtos");
    await screen.findByText("Nenhum produto cadastrado");
    fireEvent.click(screen.getByRole("button", { name: "Editar estabelecimento" }));
    const image = new File(["image"], "cover.jpg", { type: "image/jpeg" });
    fireEvent.change(screen.getByLabelText("Imagem de capa"), { target: { files: [image] } });
    expect(container.querySelector(".menu-page .hero-cover")).toHaveAttribute("src", "blob:http://localhost/cover");
    fireEvent.click(screen.getByRole("button", { name: "Salvar" }));
    await waitFor(() => expect(container.querySelector(".menu-page .hero-cover")).toHaveAttribute("src", persisted.heroImageUrl));
    expect(api.uploadImage).toHaveBeenCalledWith(image);
    expect(api.updateMyEstablishment).toHaveBeenCalledWith(expect.objectContaining({ heroImageUrl: persisted.heroImageUrl }));
    unmount();
    api.getPublicMenu.mockResolvedValue([]);
    api.getPublicEstablishment.mockResolvedValue(persisted);
    const publicView = renderApp("/cardapio/pizzaria");
    await waitFor(() => expect(publicView.container.querySelector(".hero-cover")).toHaveAttribute("src", persisted.heroImageUrl));
  });

  it("persists section movement and uses returned ranks in both sections and tabs after reload", async () => {
    const entries = [
      { id: 1, nome: "Batata", categoria: "Entradas", categoriaOrdem: 0, ordem: 0, ativo: true },
      { id: 2, nome: "Suco", categoria: "Bebidas", categoriaOrdem: 1, ordem: 0, ativo: true }
    ];
    const moved = [{ ...entries[1], categoriaOrdem: 0 }, { ...entries[0], categoriaOrdem: 1 }];
    api.validate.mockResolvedValue(session);
    api.getAdminProducts.mockResolvedValue(entries);
    api.getMyEstablishment.mockResolvedValue({ nome: "Pizzaria" });
    api.updateCategoryOrder.mockResolvedValue(moved);
    const view = renderApp("/painel/produtos");
    const up = await screen.findByRole("button", { name: "Subir seção Bebidas" });
    fireEvent.click(up);
    await waitFor(() => expect(screen.getAllByRole("heading", { level: 2 }).map(node => node.textContent)).toEqual(["Bebidas", "Entradas"]));
    expect(api.updateCategoryOrder).toHaveBeenCalledWith("Bebidas", "Entradas");
    expect([...screen.getByRole("navigation").querySelectorAll("button")].slice(0, 2).map(node => node.textContent)).toEqual(["Bebidas", "Entradas"]);
    view.unmount();
    api.getAdminProducts.mockResolvedValue(moved);
    renderApp("/painel/produtos");
    await screen.findByText("Suco");
    expect(screen.getAllByRole("heading", { level: 2 }).map(node => node.textContent)).toEqual(["Bebidas", "Entradas"]);
  });
  beforeEach(() => {
    vi.clearAllMocks();
    api.getStoredUser.mockReturnValue(null);
    api.discardUnusedImage.mockResolvedValue(undefined);
    api.getPlatformEstablishments.mockResolvedValue([]);
  });

  it("renders an anonymous public menu without validating or refreshing authentication", async () => {
    api.getPublicMenu.mockResolvedValue([{ id: 1, nome: "Pizza", categoria: "Pizzas", preco: 25, ativo: true }]);
    api.getPublicEstablishment.mockResolvedValue({ nome: "Pizzaria", slug: "pizzaria" });

    renderApp("/cardapio/pizzaria");

    expect(await screen.findByText("Pizza")).toBeInTheDocument();
    expect(api.getPublicMenu).toHaveBeenCalledTimes(1);
    expect(api.getPublicEstablishment).toHaveBeenCalledTimes(1);
    expect(api.validate).not.toHaveBeenCalled();
  });

  it("validates a protected route once and loads its administrative data", async () => {
    api.validate.mockResolvedValue(session);
    api.getAdminProducts.mockResolvedValue([{ id: 1, nome: "Pizza", categoria: "Pizzas", preco: 25, ativo: true }]);
    api.getMyEstablishment.mockResolvedValue({ nome: "Pizzaria", slug: "pizzaria" });

    renderApp("/painel/produtos", { strict: true });

    expect(await screen.findByText("Pizza")).toBeInTheDocument();
    expect(api.validate).toHaveBeenCalledTimes(1);
    expect(api.getAdminProducts).toHaveBeenCalledTimes(1);
    expect(api.getMyEstablishment).toHaveBeenCalledTimes(1);
  });

  it("redirects a protected route to login when the session is absent", async () => {
    api.validate.mockRejectedValue(Object.assign(new Error("Sessao expirada"), { sessionExpired: true }));

    renderApp("/painel/produtos");

    expect(await screen.findByRole("heading", { name: "Entrar" })).toBeInTheDocument();
    expect(window.location.pathname).toBe("/login");
    expect(api.validate).toHaveBeenCalledTimes(1);
    expect(api.getAdminProducts).not.toHaveBeenCalled();
  });

  it("keeps a protected route unavailable after a real validation failure", async () => {
    api.validate.mockRejectedValue(new Error("Network error"));

    renderApp("/painel/produtos");

    expect(await screen.findByRole("alert")).toHaveTextContent("Não foi possível verificar a sessão");
    expect(window.location.pathname).toBe("/painel/produtos");
    expect(api.validate).toHaveBeenCalledTimes(1);
    await waitFor(() => expect(api.getAdminProducts).not.toHaveBeenCalled());
  });
});

describe("administrative routing by role", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    api.getStoredUser.mockReturnValue(null);
    api.getPlatformEstablishments.mockResolvedValue([]);
    api.getAdminProducts.mockResolvedValue([]);
    api.getMyEstablishment.mockResolvedValue({ nome: "Pizzaria" });
  });

  it.each([
    ["ROLE_PLATFORM_ADMIN", "/plataforma", "Visão geral"],
    ["ROLE_TENANT_ADMIN", "/painel/produtos", "Nenhum produto cadastrado"]
  ])("routes %s after login to %s", async (role, path, content) => {
    api.login.mockResolvedValue({ ...session, roles: [role] });
    renderApp("/login");
    fireEvent.change(screen.getByLabelText("Usuario"), { target: { value: "owner" } });
    fireEvent.change(screen.getByLabelText("Senha"), { target: { value: "test-password" } });
    fireEvent.click(screen.getByRole("button", { name: "Entrar" }));
    await screen.findByText(content, { selector: role === "ROLE_PLATFORM_ADMIN" ? "h1" : undefined });
    expect(window.location.pathname).toBe(path);
    expect(api.validate).not.toHaveBeenCalled();
    if (role === "ROLE_PLATFORM_ADMIN") {
      expect(api.getAdminProducts).not.toHaveBeenCalled();
      expect(api.getMyEstablishment).not.toHaveBeenCalled();
    } else expect(api.getPlatformEstablishments).not.toHaveBeenCalled();
  });

  it("redirects a tenant away from platform routes before calling platform APIs", async () => {
    api.validate.mockResolvedValue(session);
    renderApp("/plataforma");
    await screen.findByText("Nenhum produto cadastrado");
    expect(window.location.pathname).toBe("/painel/produtos");
    expect(api.getPlatformEstablishments).not.toHaveBeenCalled();
  });

  it("redirects a platform admin away from the tenant panel before calling tenant APIs", async () => {
    api.validate.mockResolvedValue({ ...session, roles: ["ROLE_PLATFORM_ADMIN"] });
    renderApp("/painel");
    await screen.findByRole("heading", { name: "Visão geral" });
    expect(window.location.pathname).toBe("/plataforma");
    expect(api.getAdminProducts).not.toHaveBeenCalled();
    expect(api.getMyEstablishment).not.toHaveBeenCalled();
  });

  it("recovers a platform reload in StrictMode and validates only once", async () => {
    api.validate.mockResolvedValue({ ...session, roles: ["ROLE_PLATFORM_ADMIN"] });
    renderApp("/plataforma", { strict: true });
    await screen.findByRole("heading", { name: "Visão geral" });
    expect(api.validate).toHaveBeenCalledTimes(1);
    expect(api.getPlatformEstablishments).toHaveBeenCalledTimes(1);
  });

  it("redirects an anonymous platform visit to login", async () => {
    api.validate.mockRejectedValue(Object.assign(new Error("Sessão expirada"), { sessionExpired: true }));
    renderApp("/plataforma/clientes");
    await screen.findByRole("heading", { name: "Entrar" });
    expect(window.location.pathname).toBe("/login");
    expect(api.getPlatformEstablishments).not.toHaveBeenCalled();
  });

  it("denies unsupported roles without mounting either administrative page", async () => {
    api.validate.mockResolvedValue({ ...session, roles: ["ROLE_ADMIN"] });
    renderApp("/plataforma");
    expect(await screen.findByRole("alert")).toHaveTextContent("não possui acesso");
    expect(api.getPlatformEstablishments).not.toHaveBeenCalled();
    expect(api.getAdminProducts).not.toHaveBeenCalled();
  });

  it("uses the platform home when both roles are explicitly present", async () => {
    api.login.mockResolvedValue({ ...session, roles: ["ROLE_TENANT_ADMIN", "ROLE_PLATFORM_ADMIN"] });
    renderApp("/login");
    fireEvent.change(screen.getByLabelText("Usuario"), { target: { value: "owner" } });
    fireEvent.change(screen.getByLabelText("Senha"), { target: { value: "test-password" } });
    fireEvent.click(screen.getByRole("button", { name: "Entrar" }));
    await screen.findByRole("heading", { name: "Visão geral" });
    expect(window.location.pathname).toBe("/plataforma");
  });
});
