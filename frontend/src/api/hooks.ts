import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { getJson, postArquivo, postJson, queryFiltros } from "./client";
import type {
  DocumentoDetalhe,
  EmailStatus,
  Filtros,
  Pagina,
  Resumo,
  UploadResposta,
  Lancamento,
} from "./types";

export function useLancamentos(filtros: Filtros, page: number, size = 50) {
  return useQuery({
    queryKey: ["lancamentos", filtros, page, size],
    queryFn: () =>
      getJson<Pagina<Lancamento>>(
        `/api/lancamentos${queryFiltros(filtros, { page: String(page), size: String(size) })}`,
      ),
    placeholderData: (prev) => prev,
  });
}

export function useResumo(filtros: Filtros) {
  return useQuery({
    queryKey: ["resumo", filtros],
    queryFn: () => getJson<Resumo>(`/api/lancamentos/summary${queryFiltros(filtros)}`),
  });
}

export function useDocumento(id: string | undefined) {
  return useQuery({
    queryKey: ["documento", id],
    enabled: !!id,
    queryFn: () => getJson<DocumentoDetalhe>(`/api/documentos/${id}`),
  });
}

export function useUpload() {
  return useMutation({
    mutationFn: (file: File) => postArquivo<UploadResposta>("/api/extratos/upload", file),
  });
}

export function useReprocessar() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => postJson<DocumentoDetalhe>(`/api/documentos/${id}/reprocessar`),
    onSuccess: (_data, id) => {
      qc.invalidateQueries({ queryKey: ["documento", id] });
      qc.invalidateQueries({ queryKey: ["lancamentos"] });
      qc.invalidateQueries({ queryKey: ["resumo"] });
    },
  });
}

export function exportUrl(filtros: Filtros, format: "csv" | "xlsx"): string {
  return `/api/lancamentos/export${queryFiltros(filtros, { format })}`;
}

/** Status da ingestão por e-mail (§13). Poll leve para o indicador na interface. */
export function useEmailStatus() {
  return useQuery({
    queryKey: ["email-status"],
    queryFn: () => getJson<EmailStatus>("/api/email/status"),
    refetchInterval: 30_000,
    staleTime: 10_000,
  });
}

/** Busca a URL de consentimento OAuth e a abre em nova aba. */
export function useAutorizarEmail() {
  return useMutation({
    mutationFn: () => getJson<{ url: string }>("/api/email/oauth/url"),
  });
}
