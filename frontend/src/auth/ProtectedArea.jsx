import { useEffect, useRef, useState } from "react";
import { useAuth } from "./AuthContext.jsx";
import { getAdminHome } from "./roles.js";
import { Loader2 } from "../components/icons.js";

export function ProtectedArea({ role, onNavigate, children }) {
  const auth = useAuth();
  const validationStarted = useRef(false);
  const [logoutError, setLogoutError] = useState("");
  const allowed = auth.status === "authenticated" && auth.roles.includes(role);
  const home = getAdminHome(auth.roles);

  useEffect(() => {
    if (auth.status === "loading" && !validationStarted.current) {
      validationStarted.current = true;
      void auth.validate();
    } else if (auth.status === "anonymous") {
      onNavigate("/login", { replace: true });
    } else if (auth.status === "authenticated" && !allowed && home) {
      onNavigate(home, { replace: true });
    }
  }, [auth.status, auth.validate, allowed, home, onNavigate]);

  if (allowed) return children;

  if (auth.status === "unavailable") {
    return <main className="center-screen">
      <p role="alert">{auth.error}</p>
      <button className="primary-action" type="button" onClick={() => auth.validate()}>Tentar novamente</button>
    </main>;
  }

  if (auth.status === "authenticated" && !home) {
    return <main className="center-screen">
      <p role="alert">Sua conta não possui acesso a uma área administrativa.</p>
      {logoutError && <p role="alert">{logoutError}</p>}
      <button className="secondary-action" type="button" onClick={async () => {
        try { await auth.logout(); } catch (error) { setLogoutError(error.message); }
      }}>Sair</button>
    </main>;
  }

  return <main className="center-screen" role="status">
    <Loader2 className="spin" size={28} aria-hidden="true" />
    <span>Verificando sessão…</span>
  </main>;
}
