import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AuthProvider, useAuth } from "./AuthContext.jsx";
import { api } from "../api/client.js";

vi.mock("../api/client.js", () => ({ api: {
  getStoredUser: vi.fn(() => null), validate: vi.fn(), logout: vi.fn(), login: vi.fn()
} }));

const session = { username: "owner", roles: ["ROLE_TENANT_ADMIN"], estabelecimentoSlug: "store" };

function Probe() {
  const auth = useAuth();
  return <>
    <span data-testid="status">{auth.status}</span>
    <span data-testid="user">{auth.username}</span>
    <button onClick={auth.validate}>validate</button>
    <button onClick={auth.logout}>logout</button>
  </>;
}

describe("session reconstruction in AuthProvider", () => {
  beforeEach(() => vi.clearAllMocks());

  it("does not validate globally and validates only when requested", async () => {
    api.validate.mockResolvedValue(session);
    api.logout.mockResolvedValue({});
    render(<AuthProvider><Probe /></AuthProvider>);
    expect(screen.getByTestId("status")).toHaveTextContent("loading");
    expect(api.validate).not.toHaveBeenCalled();
    fireEvent.click(screen.getByText("validate"));
    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("authenticated"));
    expect(api.validate).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId("user")).toHaveTextContent("owner");
    fireEvent.click(screen.getByText("logout"));
    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("anonymous"));
    expect(screen.getByTestId("user")).toBeEmptyDOMElement();
  });

  it("does not treat a transient boot failure as anonymous and supports retry", async () => {
    api.validate.mockRejectedValueOnce(new Error("429")).mockResolvedValueOnce(session);
    render(<AuthProvider><Probe /></AuthProvider>);
    fireEvent.click(screen.getByText("validate"));
    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("unavailable"));
    fireEvent.click(screen.getByText("validate"));
    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("authenticated"));
  });

  it("preserves an authenticated user on transient validation failure", async () => {
    api.validate.mockResolvedValueOnce(session).mockRejectedValueOnce(new Error("429"));
    render(<AuthProvider><Probe /></AuthProvider>);
    fireEvent.click(screen.getByText("validate"));
    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("authenticated"));
    await act(async () => fireEvent.click(screen.getByText("validate")));
    expect(screen.getByTestId("status")).toHaveTextContent("authenticated");
    expect(screen.getByTestId("user")).toHaveTextContent("owner");
  });

  it("follows expiration when the server definitively rejects the session", async () => {
    api.validate.mockRejectedValue(Object.assign(new Error("expired"), { sessionExpired: true }));
    render(<AuthProvider><Probe /></AuthProvider>);
    fireEvent.click(screen.getByText("validate"));
    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("anonymous"));
    expect(screen.getByTestId("user")).toBeEmptyDOMElement();
  });
});
