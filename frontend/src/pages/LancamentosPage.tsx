import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { exportUrl, useLancamentos, useResumo } from "../api/hooks";
import type { Filtros, Status } from "../api/types";
import { StatusBadge } from "../components/StatusBadge";
import { EmailSyncBar } from "../components/EmailSyncBar";
import { Card, Tag, btn } from "../components/ui";
import { IconDownload, IconUpload } from "../components/icons";
import { formatBRL, formatCnpj, texto } from "../lib/format";

type SegItem = { status?: Status; rotulo: string; dot: string };

const SEGMENTOS: SegItem[] = [
  { status: undefined, rotulo: "Todas", dot: "bg-ink3" },
  { status: "OK", rotulo: "OK", dot: "bg-ok" },
  { status: "REVISAR", rotulo: "Revisar", dot: "bg-revisar" },
  { status: "SEM_DETALHE", rotulo: "Sem detalhe", dot: "bg-semdetalhe" },
  { status: "ERRO", rotulo: "Erro", dot: "bg-erro" },
];

export function LancamentosPage() {
  const [filtros, setFiltros] = useState<Filtros>({});
  const [page, setPage] = useState(0);
  const navigate = useNavigate();

  const atualizarFiltro = (patch: Partial<Filtros>) => {
    setFiltros((f) => ({ ...f, ...patch }));
    setPage(0);
  };

  const resumo = useResumo(filtros);
  const lista = useLancamentos(filtros, page);

  const contagem = resumo.data?.contagemPorStatus ?? {};
  const total = resumo.data?.total ?? 0;
  const saldoTotal = Object.values(resumo.data?.somaSaldoFinalPorCompetencia ?? {}).reduce(
    (a, b) => a + b,
    0,
  );
  const okCount = contagem.OK ?? 0;
  const revisarCount = contagem.REVISAR ?? 0;
  const erroCount = contagem.ERRO ?? 0;
  const pendencias = (contagem.REVISAR ?? 0) + (contagem.SEM_DETALHE ?? 0) + (contagem.ERRO ?? 0);
  const progresso = total > 0 ? Math.round((okCount / total) * 100) : 0;
  const atualizado = resumo.dataUpdatedAt
    ? new Date(resumo.dataUpdatedAt).toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" })
    : "—";

  const pagina = lista.data;

  return (
    <div className="space-y-5">
      <EmailSyncBar />

      {/* Cabeçalho da página */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold tracking-tight text-ink">Lançamentos</h1>
          <p className="text-sm text-ink2">Conferência de fechamento de saldos das aplicações.</p>
        </div>
        <div className="flex items-center gap-2">
          <a href={exportUrl(filtros, "csv")} className={btn("outline")}>
            <IconDownload /> CSV
          </a>
          <a href={exportUrl(filtros, "xlsx")} className={btn("outline")}>
            <IconDownload /> XLSX
          </a>
          <Link to="/upload" className={btn("primary")}>
            <IconUpload /> Enviar extratos
          </Link>
        </div>
      </div>

      {/* Banner de KPIs */}
      <Card className="p-5">
        <div className="grid gap-5 lg:grid-cols-[1.4fr_1fr_1fr_1fr] lg:items-center lg:divide-x lg:divide-edge">
          <div className="lg:pr-6">
            <div className="text-sm font-medium text-ink">
              {okCount} de {total} conferidos sem pendência
            </div>
            <div className="mt-3 h-2 w-full overflow-hidden rounded-full bg-panel2">
              <div
                className="h-full rounded-full bg-accent transition-[width]"
                style={{ width: `${progresso}%` }}
              />
            </div>
            <div className="mt-2 text-2xs text-ink3">
              {pendencias} com pendência · {progresso}% conferido
            </div>
          </div>
          <Kpi rotulo="Saldo final (total)" valor={formatBRL(saldoTotal)} cor="text-accent" prefixo="R$" />
          <Kpi rotulo="A revisar" valor={String(revisarCount)} cor="text-revisar" />
          <Kpi rotulo="Com erro" valor={String(erroCount)} cor="text-erro" />
        </div>
      </Card>

      {/* Filtro segmentado por status */}
      <div className="flex flex-wrap items-center gap-2">
        <div className="flex flex-wrap items-center gap-1 rounded-xl border border-edge bg-panel p-1">
          {SEGMENTOS.map((s) => {
            const ativo = (filtros.status ?? undefined) === s.status;
            const count = s.status ? (contagem[s.status] ?? 0) : total;
            return (
              <button
                key={s.rotulo}
                type="button"
                onClick={() => atualizarFiltro({ status: s.status })}
                className={`inline-flex items-center gap-2 rounded-lg px-3 py-1.5 text-sm font-medium transition-colors ${
                  ativo ? "bg-panel2 text-ink ring-1 ring-inset ring-edge2" : "text-ink2 hover:text-ink"
                }`}
              >
                <span className={`h-2 w-2 rounded-full ${s.dot}`} />
                {s.rotulo}
                <span
                  className={`num min-w-[1.25rem] rounded-md px-1 text-center text-2xs tabular-nums ${
                    ativo ? "bg-bg text-ink" : "bg-panel2 text-ink2"
                  }`}
                >
                  {count}
                </span>
              </button>
            );
          })}
        </div>
        <span className="ml-auto text-2xs text-ink3">Atualizado às {atualizado}</span>
      </div>

      {/* Filtros textuais */}
      <Card className="flex flex-wrap items-end gap-3 p-3">
        <Campo rotulo="Busca (produto, identificador, cliente)">
          <input
            type="search"
            value={filtros.q ?? ""}
            onChange={(e) => atualizarFiltro({ q: e.target.value })}
            placeholder="Buscar…"
            className={inputCls + " w-56"}
          />
        </Campo>
        <Campo rotulo="Banco">
          <input
            type="text"
            value={filtros.banco ?? ""}
            onChange={(e) => atualizarFiltro({ banco: e.target.value })}
            placeholder="CAIXA…"
            className={inputCls + " w-32"}
          />
        </Campo>
        <Campo rotulo="Competência">
          <input
            type="text"
            value={filtros.competencia ?? ""}
            onChange={(e) => atualizarFiltro({ competencia: e.target.value })}
            placeholder="AAAA-MM"
            className={inputCls + " num w-28"}
          />
        </Campo>
        <Campo rotulo="CNPJ">
          <input
            type="text"
            value={filtros.cnpj ?? ""}
            onChange={(e) => atualizarFiltro({ cnpj: e.target.value })}
            placeholder="só dígitos"
            className={inputCls + " num w-40"}
          />
        </Campo>
      </Card>

      {/* Tabela */}
      <Card className="overflow-x-auto">
        <table className="w-full min-w-[1000px] text-sm">
          <thead className="border-b border-edge text-left text-2xs uppercase tracking-wide text-ink3">
            <tr>
              <Th>Status</Th>
              <Th>Banco / Competência</Th>
              <Th>Cliente</Th>
              <Th>Identificador / Produto</Th>
              <ThNum>Saldo final</ThNum>
              <ThNum>Rendimento</ThNum>
              <ThNum>Diferença</ThNum>
              <Th>Motivos</Th>
            </tr>
          </thead>
          <tbody className="divide-y divide-edge/60">
            {lista.isError && (
              <tr>
                <td colSpan={8} className="px-3 py-10 text-center text-erro">
                  Erro ao carregar lançamentos.
                </td>
              </tr>
            )}
            {lista.isLoading && (
              <tr>
                <td colSpan={8} className="px-3 py-10 text-center text-ink3">
                  Carregando…
                </td>
              </tr>
            )}
            {pagina && pagina.content.length === 0 && (
              <tr>
                <td colSpan={8} className="px-3 py-12 text-center text-ink3">
                  Nenhum lançamento encontrado.{" "}
                  <Link to="/upload" className="text-accent hover:underline">
                    Envie extratos
                  </Link>{" "}
                  para começar.
                </td>
              </tr>
            )}
            {pagina?.content.map((l) => {
              const diffAlta = l.diferencaFechamento != null && Math.abs(l.diferencaFechamento) > 0.05;
              return (
                <tr
                  key={l.id}
                  onClick={() => l.documentoId && navigate(`/documentos/${l.documentoId}`)}
                  className={`${l.documentoId ? "cursor-pointer" : ""} transition-colors hover:bg-panel2`}
                >
                  <td className="px-3 py-2.5">
                    <StatusBadge status={l.status} />
                  </td>
                  <td className="px-3 py-2.5">
                    <div className="font-medium text-ink">{texto(l.banco)}</div>
                    <div className="num text-2xs text-ink3">{texto(l.competencia)}</div>
                  </td>
                  <td className="px-3 py-2.5">
                    <div className="font-medium text-ink">{texto(l.clienteNome)}</div>
                    <div className="num text-2xs text-ink3">{formatCnpj(l.clienteCnpj)}</div>
                  </td>
                  <td className="px-3 py-2.5">
                    <div className="text-ink">{texto(l.identificador)}</div>
                    {l.produto && <div className="mt-1"><Tag>{l.produto}</Tag></div>}
                  </td>
                  <td className="num px-3 py-2.5 text-right font-medium tabular-nums text-ink">
                    {formatBRL(l.saldoFinalBruto)}
                  </td>
                  <td className="num px-3 py-2.5 text-right tabular-nums text-ink2">
                    {formatBRL(l.rendimentoBruto)}
                  </td>
                  <td
                    className={`num px-3 py-2.5 text-right tabular-nums ${
                      diffAlta ? "font-semibold text-erro" : "text-ink3"
                    }`}
                  >
                    {formatBRL(l.diferencaFechamento)}
                  </td>
                  <td className="max-w-xs px-3 py-2.5 text-2xs text-ink3">
                    <span className="line-clamp-2" title={l.motivos ?? ""}>
                      {texto(l.motivos)}
                    </span>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </Card>

      {/* Paginação */}
      {pagina && pagina.totalPages > 1 && (
        <div className="flex items-center justify-between text-sm text-ink2">
          <span>
            {pagina.totalElements} lançamentos · página {pagina.number + 1} de {pagina.totalPages}
          </span>
          <div className="flex gap-2">
            <button
              type="button"
              disabled={pagina.first}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              className={btn("outline")}
            >
              Anterior
            </button>
            <button
              type="button"
              disabled={pagina.last}
              onClick={() => setPage((p) => p + 1)}
              className={btn("outline")}
            >
              Próxima
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

const inputCls =
  "rounded-lg border border-edge2 bg-bg px-2.5 py-1.5 text-sm text-ink placeholder:text-ink3 focus:border-accent focus:outline-none";

function Kpi({
  rotulo,
  valor,
  cor,
  prefixo,
}: {
  rotulo: string;
  valor: string;
  cor: string;
  prefixo?: string;
}) {
  return (
    <div className="lg:px-6">
      <div className="text-2xs uppercase tracking-wide text-ink3">{rotulo}</div>
      <div className={`num mt-1 text-2xl font-semibold tabular-nums ${cor}`}>
        {prefixo && <span className="mr-1 text-base font-normal text-ink3">{prefixo}</span>}
        {valor}
      </div>
    </div>
  );
}

function Campo({ rotulo, children }: { rotulo: string; children: React.ReactNode }) {
  return (
    <label className="flex flex-col gap-1">
      <span className="text-2xs uppercase tracking-wide text-ink3">{rotulo}</span>
      {children}
    </label>
  );
}

function Th({ children }: { children: React.ReactNode }) {
  return <th className="px-3 py-2.5 font-medium">{children}</th>;
}
function ThNum({ children }: { children: React.ReactNode }) {
  return <th className="px-3 py-2.5 text-right font-medium">{children}</th>;
}
