import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "../api/client.js";
import { PlatformPage } from "./PlatformPage.jsx";

const auth = vi.hoisted(() => ({ username: "operator", logout: vi.fn() }));
vi.mock("../auth/AuthContext.jsx", () => ({ useAuth: () => auth }));
vi.mock("../api/client.js", () => ({ api: {
  getPlatformEstablishments: vi.fn(), getPlatformEstablishment: vi.fn(), createPlatformEstablishment: vi.fn(),
  activatePlatformEstablishment: vi.fn(), deactivatePlatformEstablishment: vi.fn(), deletePlatformEstablishment: vi.fn(), createPlatformAdmin: vi.fn()
} }));

const clients = [
  { id: 1, nome: "Pizzaria do João", slug: "pizza-joao", ativo: true },
  { id: 2, nome: "Hamburgueria Central", slug: "burger-central", ativo: false }
];
const clientDetails = { ...clients[0], descricao: "Pizzas artesanais", telefone: "(31) 99999-1111", endereco: "Rua das Flores, 12", emailContato: "contato@example.test", horarioFuncionamento: "18h às 23h", tema: "grafite", criadoEm: "2026-09-20T12:30:00", atualizadoEm: "2026-09-29T10:00:00" };

function renderPage(props = {}) {
  const onNavigate = vi.fn();
  const view = render(<PlatformPage section="clientes" onNavigate={onNavigate} {...props} />);
  return { ...view, onNavigate };
}

function deferred() {
  let resolve;
  const promise = new Promise(done => { resolve = done; });
  return { promise, resolve };
}

async function fillAccess(user) {
  await user.type(screen.getByLabelText("Usuário"), "novo-cliente");
  await user.type(screen.getByLabelText("Email de acesso"), "novo@example.test");
  await user.type(screen.getByLabelText("Senha"), "senha-forte-teste");
}

describe("platform management", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    api.getPlatformEstablishments.mockResolvedValue(clients);
    api.getPlatformEstablishment.mockResolvedValue(clientDetails);
    auth.logout.mockResolvedValue(undefined);
  });

  it("loads establishments and derives all three metrics from the returned list", async () => {
    renderPage({ section: "dashboard" });
    await screen.findByText("Pizzaria do João");
    expect(screen.getByText("Total de clientes").parentElement).toHaveTextContent("2");
    expect(screen.getByText("Clientes ativos").parentElement).toHaveTextContent("1");
    expect(screen.getByText("Clientes inativos").parentElement).toHaveTextContent("1");
    expect(api.getPlatformEstablishments).toHaveBeenCalledTimes(1);
  });

  it("shows loading without presenting artificial zero metrics", async () => {
    const pending = deferred();
    api.getPlatformEstablishments.mockReturnValue(pending.promise);
    renderPage({ section: "dashboard" });
    expect(screen.getByRole("status")).toHaveTextContent("Carregando estabelecimentos");
    expect(screen.queryByText("Total de clientes")).not.toBeInTheDocument();
    await act(async () => pending.resolve(clients));
    await screen.findByText("Total de clientes");
  });

  it("shows load errors and successfully retries", async () => {
    api.getPlatformEstablishments.mockRejectedValueOnce(new Error("Serviço indisponível"));
    renderPage({ section: "dashboard" });
    expect(await screen.findByRole("alert")).toHaveTextContent("Serviço indisponível");
    fireEvent.click(screen.getByRole("button", { name: "Tentar novamente" }));
    await screen.findByText("Pizzaria do João");
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it.each(["dashboard", "clientes"])("shows the empty state on %s", async section => {
    api.getPlatformEstablishments.mockResolvedValue([]);
    renderPage({ section });
    await screen.findByText("Nenhum cliente cadastrado");
    fireEvent.click(screen.getByRole("button", { name: "Cadastrar primeiro cliente" }));
    expect(screen.getByRole("dialog", { name: "Novo cliente" })).toBeInTheDocument();
  });

  it("searches locally by name and slug and shows no matching results", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText("Pizzaria do João");
    const search = screen.getByRole("searchbox");
    await user.type(search, "PIZZARIA");
    expect(screen.getByText("Pizzaria do João")).toBeInTheDocument();
    expect(screen.queryByText("Hamburgueria Central")).not.toBeInTheDocument();
    await user.clear(search);
    await user.type(search, "burger-central");
    expect(screen.getByText("Hamburgueria Central")).toBeInTheDocument();
    expect(screen.queryByText("Pizzaria do João")).not.toBeInTheDocument();
    await user.clear(search);
    await user.type(search, "inexistente");
    expect(screen.getByText("Nenhum cliente encontrado")).toBeInTheDocument();
    expect(api.getPlatformEstablishments).toHaveBeenCalledTimes(1);
  });

  it("creates a client with the DTO fields, closes the form and reloads the list", async () => {
    const user = userEvent.setup();
    const created = { id: 3, nome: "Nova loja", slug: "novo-cliente", ativo: true };
    api.createPlatformEstablishment.mockResolvedValue(created);
    api.getPlatformEstablishments.mockResolvedValueOnce(clients).mockResolvedValueOnce([...clients, created]);
    renderPage();
    await screen.findByText("Pizzaria do João");
    await user.click(screen.getByRole("button", { name: "Novo cliente" }));
    await fillAccess(user);
    await user.type(screen.getByLabelText("Nome", { exact: true }), "Nova loja");
    await user.type(screen.getByLabelText("Descrição"), "Comida artesanal");
    await user.type(screen.getByLabelText("Horário de funcionamento"), "10h às 20h");
    await user.type(screen.getByLabelText("Telefone"), "(31) 99999-1111");
    await user.type(screen.getByLabelText("Endereço"), "Rua Central, 10");
    await user.type(screen.getByLabelText("Email de contato"), "contato@example.test");
    expect(screen.queryByLabelText("Slug")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Criar cliente" }));
    await screen.findByText("Nova loja");
    expect(api.createPlatformEstablishment).toHaveBeenCalledWith({
      username: "novo-cliente", email: "novo@example.test", password: "senha-forte-teste", nome: "Nova loja",
      descricao: "Comida artesanal", horarioFuncionamento: "10h às 20h", telefone: "(31) 99999-1111", endereco: "Rua Central, 10", emailContato: "contato@example.test"
    });
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(screen.getByText("Nova loja foi cadastrado com sucesso.")).toBeInTheDocument();
  });

  it("preserves the client form on API validation errors and permits correction", async () => {
    const user = userEvent.setup();
    api.createPlatformEstablishment.mockRejectedValueOnce(Object.assign(new Error("Dados inválidos"), { fieldErrors: { username: "Username já está em uso" } }));
    renderPage();
    await user.click(screen.getByRole("button", { name: "Novo cliente" }));
    await fillAccess(user);
    await user.type(screen.getByLabelText("Nome", { exact: true }), "Nova loja");
    await user.click(screen.getByRole("button", { name: "Criar cliente" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Dados inválidos");
    expect(screen.getByText("Username já está em uso")).toBeInTheDocument();
    expect(screen.getByLabelText("Nome", { exact: true })).toHaveValue("Nova loja");
    expect(screen.getByLabelText("Usuário")).toHaveAttribute("aria-invalid", "true");
    expect(screen.getByRole("button", { name: "Criar cliente" })).toBeEnabled();
  });

  it("activates a client and updates its status only after a successful API response", async () => {
    const pending = deferred();
    api.activatePlatformEstablishment.mockReturnValue(pending.promise);
    renderPage();
    await screen.findByText("Hamburgueria Central");
    fireEvent.click(screen.getByRole("button", { name: "Ativar Hamburgueria Central" }));
    expect(screen.getByRole("button", { name: "Ativar Hamburgueria Central" })).toBeDisabled();
    expect(screen.getByText("Inativo")).toBeInTheDocument();
    await act(async () => pending.resolve({ ...clients[1], ativo: true }));
    await screen.findByRole("button", { name: "Desativar Hamburgueria Central" });
    expect(api.activatePlatformEstablishment).toHaveBeenCalledWith(2);
    expect(screen.getByText("Hamburgueria Central foi ativado.")).toBeInTheDocument();
  });

  it("keeps the previous status and displays activation failures", async () => {
    api.activatePlatformEstablishment.mockRejectedValue(new Error("Falha ao ativar"));
    renderPage();
    await screen.findByText("Hamburgueria Central");
    fireEvent.click(screen.getByRole("button", { name: "Ativar Hamburgueria Central" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Falha ao ativar");
    expect(screen.getByText("Inativo")).toBeInTheDocument();
  });

  it("requires confirmation before deactivation and updates the list", async () => {
    api.deactivatePlatformEstablishment.mockResolvedValue({ ...clients[0], ativo: false });
    renderPage();
    await screen.findByText("Pizzaria do João");
    fireEvent.click(screen.getByRole("button", { name: "Desativar Pizzaria do João" }));
    const modal = screen.getByRole("dialog", { name: "Desativar Pizzaria do João?" });
    expect(modal).toHaveTextContent("painel e o cardápio público");
    expect(api.deactivatePlatformEstablishment).not.toHaveBeenCalled();
    fireEvent.click(within(modal).getByRole("button", { name: "Desativar cliente" }));
    await screen.findByRole("button", { name: "Ativar Pizzaria do João" });
    expect(api.deactivatePlatformEstablishment).toHaveBeenCalledWith(1);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it.each(["Excluir", "Desativar"])("cancels %s without changing any data", async action => {
    renderPage();
    await screen.findByText("Pizzaria do João");
    fireEvent.click(screen.getByRole("button", { name: `${action} Pizzaria do João` }));
    fireEvent.click(screen.getByRole("button", { name: "Cancelar" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(api.deletePlatformEstablishment).not.toHaveBeenCalled();
    expect(api.deactivatePlatformEstablishment).not.toHaveBeenCalled();
    expect(screen.getByText("Pizzaria do João")).toBeInTheDocument();
  });

  it("deletes only after confirmation and removes the client without a page reload", async () => {
    api.deletePlatformEstablishment.mockResolvedValue(undefined);
    renderPage();
    await screen.findByText("Pizzaria do João");
    fireEvent.click(screen.getByRole("button", { name: "Excluir Pizzaria do João" }));
    const modal = screen.getByRole("dialog");
    expect(modal).toHaveTextContent("usuários vinculados e suas sessões");
    expect(api.deletePlatformEstablishment).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Excluir permanentemente" }));
    await waitFor(() => expect(screen.queryByText("Pizzaria do João")).not.toBeInTheDocument());
    expect(api.deletePlatformEstablishment).toHaveBeenCalledWith(1);
    expect(screen.getByText("Pizzaria do João foi excluído.")).toBeInTheDocument();
  });

  it("keeps failed deletion confirmations open and guards against duplicate requests", async () => {
    const pending = deferred();
    api.deletePlatformEstablishment.mockReturnValue(pending.promise);
    renderPage();
    await screen.findByText("Pizzaria do João");
    fireEvent.click(screen.getByRole("button", { name: "Excluir Pizzaria do João" }));
    fireEvent.click(screen.getByRole("button", { name: "Excluir permanentemente" }));
    expect(screen.getByRole("button", { name: "Cancelar" })).toBeDisabled();
    fireEvent.keyDown(screen.getByRole("dialog"), { key: "Escape" });
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(api.deletePlatformEstablishment).toHaveBeenCalledTimes(1);
    await act(async () => pending.resolve(undefined));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("retains the client on deletion errors and supports retry", async () => {
    api.deletePlatformEstablishment.mockRejectedValueOnce(new Error("Exclusão recusada")).mockResolvedValueOnce(undefined);
    renderPage();
    await screen.findByText("Pizzaria do João");
    fireEvent.click(screen.getByRole("button", { name: "Excluir Pizzaria do João" }));
    fireEvent.click(screen.getByRole("button", { name: "Excluir permanentemente" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Exclusão recusada");
    expect(screen.getByText("Pizzaria do João")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Excluir permanentemente" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  });

  it("loads client details through their endpoint and updates details after deactivation", async () => {
    api.deactivatePlatformEstablishment.mockResolvedValue({ ...clientDetails, ativo: false });
    const { onNavigate } = renderPage({ clientId: "1" });
    await screen.findByRole("heading", { name: "Pizzaria do João" });
    for (const value of [clientDetails.descricao, clientDetails.telefone, clientDetails.endereco, clientDetails.emailContato, clientDetails.horarioFuncionamento, clientDetails.tema]) {
      expect(screen.getByText(value)).toBeInTheDocument();
    }
    expect(screen.getByText("Criado em")).toBeInTheDocument();
    expect(api.getPlatformEstablishment).toHaveBeenCalledWith("1");
    fireEvent.click(screen.getByRole("button", { name: "Desativar Pizzaria do João" }));
    fireEvent.click(screen.getByRole("button", { name: "Desativar cliente" }));
    await screen.findByText("Inativo");
    expect(screen.getByText(/estão indisponíveis/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("link", { name: /Voltar para clientes/ }));
    expect(onNavigate).toHaveBeenCalledWith("/plataforma/clientes");
  });

  it("shows detail errors and retries without displaying the previous client", async () => {
    api.getPlatformEstablishment.mockRejectedValueOnce(new Error("Estabelecimento não encontrado"));
    renderPage({ clientId: "999" });
    expect(await screen.findByRole("alert")).toHaveTextContent("não encontrado");
    expect(screen.queryByText(clientDetails.descricao)).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Tentar novamente" }));
    await screen.findByText(clientDetails.descricao);
  });

  it("creates another platform admin without inventing an admin listing", async () => {
    const user = userEvent.setup();
    api.createPlatformAdmin.mockResolvedValue({ username: "novo-cliente" });
    renderPage({ section: "administradores" });
    await user.click(screen.getByRole("button", { name: "Novo administrador" }));
    await fillAccess(user);
    expect(screen.queryByLabelText("Nome", { exact: true })).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Criar administrador" }));
    await screen.findByText("Administrador novo-cliente criado com sucesso.");
    expect(api.createPlatformAdmin).toHaveBeenCalledWith({ username: "novo-cliente", email: "novo@example.test", password: "senha-forte-teste" });
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("logs out on the server before navigating and preserves the page on failure", async () => {
    auth.logout.mockRejectedValueOnce(new Error("Falha ao sair")).mockResolvedValueOnce(undefined);
    const { onNavigate } = renderPage();
    fireEvent.click(screen.getByRole("button", { name: "Sair" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Falha ao sair");
    expect(onNavigate).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Sair" }));
    await waitFor(() => expect(onNavigate).toHaveBeenCalledWith("/login", { replace: true }));
  });
});
