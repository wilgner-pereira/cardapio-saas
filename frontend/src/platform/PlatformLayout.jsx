import { LayoutDashboard, LogOut, ShieldCheck, Store, Utensils } from "../components/icons.js";

export function PlatformLink({ to, onNavigate, children, ...props }) {
  return <a href={to} {...props} onClick={event => {
    if (event.button === 0 && !event.metaKey && !event.ctrlKey && !event.shiftKey && !event.altKey) {
      event.preventDefault();
      onNavigate(to);
    }
  }}>{children}</a>;
}

const navigation = [
  { section: "dashboard", to: "/plataforma", label: "Visão geral", Icon: LayoutDashboard },
  { section: "clientes", to: "/plataforma/clientes", label: "Clientes", Icon: Store },
  { section: "administradores", to: "/plataforma/administradores", label: "Administradores", Icon: ShieldCheck }
];

export function PlatformLayout({ section, username, onNavigate, onLogout, loggingOut, children }) {
  return <div className="platform-layout">
    <aside className="platform-sidebar">
      <PlatformLink to="/plataforma" onNavigate={onNavigate} className="platform-brand">
        <span className="platform-brand-icon"><Utensils size={22} aria-hidden="true" /></span>
        <span>Cardápio SaaS<small>Administração da plataforma</small></span>
      </PlatformLink>
      <nav className="platform-nav" aria-label="Navegação da plataforma">
        {navigation.map(({ section: key, to, label, Icon }) => <PlatformLink key={key} to={to}
          onNavigate={onNavigate} aria-current={section === key ? "page" : undefined}>
          <Icon size={20} aria-hidden="true" />{label}
        </PlatformLink>)}
      </nav>
      <div className="platform-sidebar-footer">
        <span><ShieldCheck size={18} aria-hidden="true" /> Super Admin</span>
        <button type="button" onClick={onLogout} disabled={loggingOut}>
          <LogOut size={18} aria-hidden="true" />{loggingOut ? "Saindo…" : "Sair"}
        </button>
      </div>
    </aside>
    <div className="platform-workspace">
      <header className="platform-topbar">
        <span>Gestão da plataforma</span>
        <span className="platform-user"><ShieldCheck size={18} aria-hidden="true" />{username}</span>
      </header>
      <main id="platform-content" className="platform-content" tabIndex={-1}>{children}</main>
    </div>
  </div>;
}
