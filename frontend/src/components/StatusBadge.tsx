import type { Status } from "../api/types";

const MAPA: Record<string, { rotulo: string; classe: string }> = {
  OK: { rotulo: "OK", classe: "text-ok bg-ok/10 border-ok/30" },
  REVISAR: { rotulo: "Revisar", classe: "text-revisar bg-revisar/10 border-revisar/30" },
  SEM_DETALHE: { rotulo: "Sem detalhe", classe: "text-semdetalhe bg-semdetalhe/10 border-semdetalhe/25" },
  ERRO: { rotulo: "Erro", classe: "text-erro bg-erro/10 border-erro/30" },
  // status de processamento do documento
  PROCESSADO: { rotulo: "Processado", classe: "text-ok bg-ok/10 border-ok/30" },
  PENDENTE: { rotulo: "Pendente", classe: "text-revisar bg-revisar/10 border-revisar/30" },
};

export function StatusBadge({ status }: { status: Status | string }) {
  const m = MAPA[status] ?? { rotulo: status, classe: "text-ink2 bg-panel2 border-edge2" };
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full border px-2 py-0.5 text-2xs font-semibold uppercase tracking-wide ${m.classe}`}
    >
      <span className="h-1.5 w-1.5 rounded-full bg-current" />
      {m.rotulo}
    </span>
  );
}
