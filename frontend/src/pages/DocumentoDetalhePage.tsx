import { Link, useParams } from "react-router-dom";
import { useDocumento, useReprocessar } from "../api/hooks";
import type { Lancamento } from "../api/types";
import { StatusBadge } from "../components/StatusBadge";
import { Card, Tag, btn } from "../components/ui";
import { IconArrowLeft, IconDownload, IconMail, IconRefresh, IconUpload } from "../components/icons";
import { formatBRL, formatCnpj, formatDateTime, texto } from "../lib/format";

export function DocumentoDetalhePage() {
  const { id } = useParams<{ id: string }>();
  const { data, isLoading, isError } = useDocumento(id);
  const reprocessar = useReprocessar();

  if (isLoading) return <p className="text-ink3">Carregando…</p>;
  if (isError || !data) return <p className="text-erro">Documento não encontrado.</p>;

  const d = data.documento;
  const viaEmail = d.origem === "EMAIL";

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Link to="/" className={btn("ghost")}>
          <IconArrowLeft /> Lançamentos
        </Link>
        <div className="flex items-center gap-2">
          <a href={`/api/documentos/${d.id}/pdf`} className={btn("outline")}>
            <IconDownload /> Baixar PDF
          </a>
          <button
            type="button"
            onClick={() => id && reprocessar.mutate(id)}
            disabled={reprocessar.isPending}
            className={btn("primary")}
          >
            <IconRefresh /> {reprocessar.isPending ? "Reprocessando…" : "Reprocessar"}
          </button>
        </div>
      </div>

      {reprocessar.isError && (
        <Card className="border-erro/40 bg-erro/10 p-3 text-sm text-erro">
          Falha ao reprocessar. Verifique se o PDF arquivado ainda está disponível no storage.
        </Card>
      )}

      {/* Metadados do documento */}
      <Card className="p-5">
        <div className="mb-4 flex flex-wrap items-center gap-3">
          <h1 className="text-base font-semibold text-ink">
            {texto(d.nomePadronizado ?? d.nomeArquivoOriginal)}
          </h1>
          <StatusBadge status={d.statusProcessamento} />
          {d.origem && (
            <Tag>
              {viaEmail ? <IconMail width={12} height={12} /> : <IconUpload width={12} height={12} />}
              {viaEmail ? "E-mail" : "Upload"}
            </Tag>
          )}
        </div>
        <dl className="grid grid-cols-2 gap-x-6 gap-y-4 text-sm sm:grid-cols-4">
          <Info rotulo="Banco" valor={texto(d.banco)} />
          <Info rotulo="Competência" valor={texto(d.competencia)} />
          <Info rotulo="CNPJ" valor={formatCnpj(d.cnpj)} />
          <Info rotulo="Conta" valor={texto(d.conta)} />
          <Info rotulo="Total informado" valor={formatBRL(d.totalBrutoInformado)} />
          <Info
            rotulo="Documento reconhecido"
            valor={d.documentoReconhecido === false ? "Não" : d.documentoReconhecido ? "Sim" : "—"}
          />
          <Info rotulo="stop_reason" valor={texto(d.stopReason)} />
          <Info rotulo="Processado em" valor={formatDateTime(d.processadoEm)} />
          {viaEmail && <Info rotulo="Remetente" valor={texto(d.emailRemetente)} />}
          {viaEmail && <Info rotulo="Recebido em" valor={formatDateTime(d.emailData)} />}
        </dl>
        {viaEmail && d.emailAssunto && (
          <p className="mt-4 rounded-lg border border-edge bg-bg p-3 text-sm text-ink2">
            <span className="font-medium text-ink">Assunto do e-mail: </span>
            {d.emailAssunto}
          </p>
        )}
        {d.observacoes && (
          <p className="mt-4 rounded-lg border border-edge bg-bg p-3 text-sm text-ink2">
            <span className="font-medium text-ink">Observações: </span>
            {d.observacoes}
          </p>
        )}
      </Card>

      {/* Lançamentos */}
      <Card className="overflow-x-auto">
        <table className="w-full min-w-[900px] text-sm">
          <thead className="border-b border-edge text-left text-2xs uppercase tracking-wide text-ink3">
            <tr>
              <th className="px-3 py-2.5 font-medium">Status</th>
              <th className="px-3 py-2.5 font-medium">Identificador / Produto</th>
              <th className="px-3 py-2.5 text-right font-medium">Saldo inicial</th>
              <th className="px-3 py-2.5 text-right font-medium">Aplicações</th>
              <th className="px-3 py-2.5 text-right font-medium">Rendimento</th>
              <th className="px-3 py-2.5 text-right font-medium">Resgates</th>
              <th className="px-3 py-2.5 text-right font-medium">Saldo final</th>
              <th className="px-3 py-2.5 text-right font-medium">Diferença</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-edge/60">
            {data.lancamentos.map((l) => (
              <LinhaDetalhe key={l.id} l={l} />
            ))}
          </tbody>
        </table>
      </Card>
    </div>
  );
}

function LinhaDetalhe({ l }: { l: Lancamento }) {
  const diffAlta = l.diferencaFechamento != null && Math.abs(l.diferencaFechamento) > 0.05;
  return (
    <>
      <tr>
        <td className="px-3 py-2.5 align-top">
          <StatusBadge status={l.status} />
        </td>
        <td className="px-3 py-2.5 align-top">
          <div className="text-ink">{texto(l.identificador)}</div>
          <div className="text-2xs text-ink3">{texto(l.produto)}</div>
          {l.origemSaldoInicial === "calculado" && (
            <div className="text-2xs text-revisar">saldo inicial calculado</div>
          )}
        </td>
        <td className="num px-3 py-2.5 text-right align-top text-ink2">{formatBRL(l.saldoInicialBruto)}</td>
        <td className="num px-3 py-2.5 text-right align-top text-ink2">{formatBRL(l.aplicacoes)}</td>
        <td className="num px-3 py-2.5 text-right align-top text-ink2">{formatBRL(l.rendimentoBruto)}</td>
        <td className="num px-3 py-2.5 text-right align-top text-ink2">{formatBRL(l.resgatesBrutos)}</td>
        <td className="num px-3 py-2.5 text-right align-top font-medium text-ink">{formatBRL(l.saldoFinalBruto)}</td>
        <td
          className={`num px-3 py-2.5 text-right align-top ${
            diffAlta ? "font-semibold text-erro" : "text-ink3"
          }`}
        >
          {formatBRL(l.diferencaFechamento)}
        </td>
      </tr>
      {l.motivos && (
        <tr>
          <td />
          <td colSpan={7} className="px-3 pb-2.5 text-2xs text-ink3">
            {l.motivos}
          </td>
        </tr>
      )}
    </>
  );
}

function Info({ rotulo, valor }: { rotulo: string; valor: string }) {
  return (
    <div>
      <dt className="text-2xs uppercase tracking-wide text-ink3">{rotulo}</dt>
      <dd className="num mt-0.5 text-ink">{valor}</dd>
    </div>
  );
}
