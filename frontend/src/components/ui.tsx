import type { ReactNode } from "react";

/** Cartão base do tema escuro. */
export function Card({ className = "", children }: { className?: string; children: ReactNode }) {
  return <div className={`rounded-xl border border-edge bg-panel ${className}`}>{children}</div>;
}

export type BtnVariant = "primary" | "outline" | "ghost" | "danger";

/** Classes de botão reutilizáveis (aplicar em <button>, <a> ou <Link>). */
export function btn(variant: BtnVariant = "outline", extra = ""): string {
  const base =
    "inline-flex items-center justify-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-medium transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-accent disabled:cursor-not-allowed disabled:opacity-50";
  const variants: Record<BtnVariant, string> = {
    primary: "bg-accent text-black hover:bg-accent2 disabled:bg-edge2 disabled:text-ink3",
    outline: "border border-edge2 bg-transparent text-ink hover:bg-panel2",
    ghost: "text-ink2 hover:bg-panel2 hover:text-ink",
    danger: "border border-erro/40 bg-erro/10 text-erro hover:bg-erro/20",
  };
  return `${base} ${variants[variant]} ${extra}`.trim();
}

/** Chip/etiqueta pequena (ex.: banco, competência). */
export function Tag({ children, className = "" }: { children: ReactNode; className?: string }) {
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-md border border-edge2 bg-panel2 px-2 py-0.5 text-2xs font-medium text-ink2 ${className}`}
    >
      {children}
    </span>
  );
}
