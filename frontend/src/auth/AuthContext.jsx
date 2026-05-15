import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { api } from "../api/client.js";

const AuthContext = createContext(null);
const USERNAME_KEY = "cardapio.currentUsername";

export function AuthProvider({ children }) {
  const [status, setStatus] = useState("loading");
  const [username, setUsername] = useState(() => localStorage.getItem(USERNAME_KEY) || "");
  const [error, setError] = useState("");

  const validate = useCallback(async () => {
    try {
      await api.validate();
      setStatus("authenticated");
      setError("");
      return true;
    } catch {
      setStatus("anonymous");
      return false;
    }
  }, []);

  useEffect(() => {
    validate();
  }, [validate]);

  async function login({ username: nextUsername, password }) {
    setError("");
    const response = await api.login(nextUsername, password);
    localStorage.setItem(USERNAME_KEY, nextUsername);
    setUsername(nextUsername);
    setStatus("authenticated");
    return response;
  }

  async function logout() {
    await api.logout();
    localStorage.removeItem(USERNAME_KEY);
    setUsername("");
    setStatus("anonymous");
  }

  const value = useMemo(() => ({
    status,
    username,
    error,
    setError,
    login,
    logout,
    validate,
    isAuthenticated: status === "authenticated"
  }), [error, status, username, validate]);

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
