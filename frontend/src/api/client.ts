import type { Filtros } from "./types";

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

async function parseErro(res: Response): Promise<never> {
  let mensagem = `Erro ${res.status}`;
  try {
    const body = await res.json();
    if (body && typeof body.mensagem === "string") mensagem = body.mensagem;
  } catch {
    /* corpo não-JSON */
  }
  throw new ApiError(res.status, mensagem);
}

export async function getJson<T>(url: string): Promise<T> {
  const res = await fetch(url, { headers: { Accept: "application/json" } });
  if (!res.ok) await parseErro(res);
  return res.json() as Promise<T>;
}

export async function postJson<T>(url: string): Promise<T> {
  const res = await fetch(url, { method: "POST", headers: { Accept: "application/json" } });
  if (!res.ok) await parseErro(res);
  return res.json() as Promise<T>;
}

export async function postArquivo<T>(url: string, file: File): Promise<T> {
  const form = new FormData();
  form.append("files", file, file.name);
  const res = await fetch(url, { method: "POST", body: form, headers: { Accept: "application/json" } });
  if (!res.ok) await parseErro(res);
  return res.json() as Promise<T>;
}

/** Monta a query string dos filtros (ignorando vazios). */
export function queryFiltros(filtros: Filtros, extra?: Record<string, string>): string {
  const params = new URLSearchParams();
  for (const [k, v] of Object.entries(filtros)) {
    if (v !== undefined && v !== null && String(v).trim() !== "") params.set(k, String(v));
  }
  if (extra) for (const [k, v] of Object.entries(extra)) params.set(k, v);
  const s = params.toString();
  return s ? `?${s}` : "";
}
