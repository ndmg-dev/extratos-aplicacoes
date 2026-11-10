const moeda = new Intl.NumberFormat("pt-BR", {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const dataHora = new Intl.DateTimeFormat("pt-BR", {
  dateStyle: "short",
  timeStyle: "short",
});

/** Formata um valor monetário em pt-BR (1234.56 -> "1.234,56"); nulo vira travessão. */
export function formatBRL(v: number | string | null | undefined): string {
  if (v === null || v === undefined || v === "") return "—";
  const n = typeof v === "string" ? Number(v) : v;
  if (Number.isNaN(n)) return "—";
  return moeda.format(n);
}

/** Formata data/hora ISO em pt-BR. */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "—";
  return dataHora.format(d);
}

/** Formata CNPJ de 14 dígitos (00.000.000/0000-00). */
export function formatCnpj(cnpj: string | null | undefined): string {
  if (!cnpj) return "—";
  const d = cnpj.replace(/\D/g, "");
  if (d.length !== 14) return cnpj;
  return `${d.slice(0, 2)}.${d.slice(2, 5)}.${d.slice(5, 8)}/${d.slice(8, 12)}-${d.slice(12)}`;
}

export function texto(v: string | null | undefined): string {
  return v && v.trim() !== "" ? v : "—";
}
