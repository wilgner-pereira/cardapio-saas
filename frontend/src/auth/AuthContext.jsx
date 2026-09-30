import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { api } from "../api/client.js";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [status, setStatus] = useState("loading");
  const [user, setUser] = useState(() => api.getStoredUser());
  const [error, setError] = useState("");

  const validate = useCallback(async () => {
    try {
      const session = await api.validate();
      setUser({
        userId: session.userId,
        username: session.username,
        estabelecimentoSlug: session.estabelecimentoSlug,
        roles: session.roles || []
      });
      setStatus("authenticated");
      setError("");
      return true;
    } catch (cause) {
      if (cause.sessionExpired) {
        setUser(null);
        setStatus("anonymous");
      } else {
        setError("Não foi possível verificar a sessão. Tente novamente.");
        setStatus(previous => previous === "authenticated" ? previous : "unavailable");
      }
      return false;
    }
  }, []);

  useEffect(() => {
    const handleSessionExpired = () => {
      setUser(null);
      setStatus("anonymous");
    };

    window.addEventListener("cardapio:session-expired", handleSessionExpired);
    return () => window.removeEventListener("cardapio:session-expired", handleSessionExpired);
  }, []);

  async function login({ username: nextUsername, password }) {
    setError("");
    const response = await api.login(nextUsername, password);
    setUser({
      userId: response.userId,
      username: response.username || nextUsername,
      estabelecimentoSlug: response.estabelecimentoSlug,
      roles: response.roles || []
    });
    setStatus("authenticated");
    return response;
  }

  async function logout() {
    await api.logout();
    setUser(null);
    setStatus("anonymous");
  }

  const value = useMemo(() => ({
    status,
    user,
    username: user?.username || "",
    roles: user?.roles || [],
    estabelecimentoSlug: user?.estabelecimentoSlug || "",
    error,
    setError,
    login,
    logout,
    validate,
    isAuthenticated: status === "authenticated"
  }), [error, status, user, validate]);

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth deve ser usado dentro de AuthProvider");
  }
  return context;
}
