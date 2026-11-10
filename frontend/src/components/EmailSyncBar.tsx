import { useAutorizarEmail, useEmailStatus } from "../api/hooks";
import { formatDateTime } from "../lib/format";
import { IconMail } from "./icons";
import { btn } from "./ui";

/**
 * Indicador da ingestão por e-mail (§13): mostra o estado da sincronização automática e,
 * quando ainda não autorizado, um atalho para autorizar a conta.
 */
export function EmailSyncBar() {
  const { data } = useEmailStatus();
  const autorizar = useAutorizarEmail();

  if (!data || !data.habilitado) return null; // recurso desligado: não polui a interface

  const abrirAutorizacao = () => {
    autorizar.mutate(undefined, {
      onSuccess: (r) => window.open(r.url, "_blank", "noopener"),
    });
  };

  // Sem credenciais configuradas no servidor.
  if (!data.temCredenciais) {
    return (
      <Barra tom="neutro">
        <IconMail width={15} height={15} className="text-ink3" />
        <span className="text-ink2">Ingestão por e-mail habilitada, mas sem credenciais configuradas.</span>
      </Barra>
    );
  }

  // Habilitado, mas ainda não autorizado.
  if (!data.autorizado) {
    return (
      <Barra tom="aviso">
        <IconMail width={15} height={15} className="text-revisar" />
        <span className="text-ink">Ingestão por e-mail aguardando autorização da conta.</span>
        <button
          type="button"
          onClick={abrirAutorizacao}
          disabled={autorizar.isPending}
          className={btn("primary", "ml-auto !py-1")}
        >
          {autorizar.isPending ? "Abrindo…" : "Autorizar conta"}
        </button>
      </Barra>
    );
  }

  // Autorizado: mostra estado da sincronização.
  const temErro = !!data.ultimoErro;
  return (
    <Barra tom={temErro ? "erro" : "ok"}>
      <IconMail width={15} height={15} className={temErro ? "text-erro" : "text-ok"} />
      <span className="text-ink">
        E-mail{data.emailConta ? ` (${data.emailConta})` : ""}
      </span>
      <span className="text-ink3">·</span>
      {data.emExecucao ? (
        <span className="inline-flex items-center gap-1.5 text-info">
          <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-current" />
          sincronizando…
        </span>
      ) : (
        <span className="text-ink2">
          última sincronização {formatDateTime(data.ultimaSincronizacao)}
          {data.novosUltimaExecucao > 0 && (
            <span className="text-ok"> · {data.novosUltimaExecucao} novos</span>
          )}
        </span>
      )}
      {temErro && (
        <span className="ml-auto truncate text-2xs text-erro" title={data.ultimoErro ?? ""}>
          erro: {data.ultimoErro}
        </span>
      )}
    </Barra>
  );
}

function Barra({ tom, children }: { tom: "ok" | "aviso" | "erro" | "neutro"; children: React.ReactNode }) {
  const borda = {
    ok: "border-edge",
    aviso: "border-revisar/40 bg-revisar/5",
    erro: "border-erro/40 bg-erro/5",
    neutro: "border-edge",
  }[tom];
  return (
    <div
      className={`flex flex-wrap items-center gap-2 rounded-xl border bg-panel px-3 py-2 text-sm ${borda}`}
    >
      {children}
    </div>
  );
}
