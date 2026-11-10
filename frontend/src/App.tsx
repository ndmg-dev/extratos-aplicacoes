import type { ReactNode } from "react";
import { NavLink, Route, Routes } from "react-router-dom";
import { UploadPage } from "./pages/UploadPage";
import { LancamentosPage } from "./pages/LancamentosPage";
import { DocumentoDetalhePage } from "./pages/DocumentoDetalhePage";
import { IconList, IconLogo, IconUpload } from "./components/icons";

function NavItem({ to, icon, children }: { to: string; icon: ReactNode; children: ReactNode }) {
  return (
    <NavLink
      to={to}
      end
      className={({ isActive }) =>
        `inline-flex items-center gap-2 rounded-lg px-3 py-1.5 text-sm font-medium transition-colors ${
          isActive
            ? "bg-panel2 text-accent ring-1 ring-inset ring-edge2"
            : "text-ink2 hover:bg-panel2 hover:text-ink"
        }`
      }
    >
      {icon}
      {children}
    </NavLink>
  );
}

export default function App() {
  return (
    <div className="flex min-h-full flex-col">
      <header className="sticky top-0 z-20 border-b border-edge bg-panel/85 backdrop-blur">
        <div className="mx-auto flex max-w-[1400px] items-center gap-4 px-4 h-14">
          <div className="flex items-center gap-2.5 pr-2">
            <span className="grid h-8 w-8 place-items-center rounded-lg bg-accent/15 text-accent ring-1 ring-inset ring-accent/30">
              <IconLogo width={18} height={18} />
            </span>
            <div className="hidden leading-tight sm:block">
              <div className="text-sm font-semibold tracking-tight text-ink">Extratos de Aplicações</div>
              <div className="text-2xs text-ink3">conferência de saldos</div>
            </div>
          </div>
          <nav className="flex items-center gap-1">
            <NavItem to="/" icon={<IconList />}>
              Lançamentos
            </NavItem>
            <NavItem to="/upload" icon={<IconUpload />}>
              Upload
            </NavItem>
          </nav>
        </div>
      </header>

      <main className="mx-auto w-full max-w-[1400px] flex-1 px-4 py-5">
        <Routes>
          <Route path="/" element={<LancamentosPage />} />
          <Route path="/upload" element={<UploadPage />} />
          <Route path="/documentos/:id" element={<DocumentoDetalhePage />} />
        </Routes>
      </main>
    </div>
  );
}
