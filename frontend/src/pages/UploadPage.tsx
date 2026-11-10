import { useCallback, useState } from "react";
import { useDropzone } from "react-dropzone";
import { Link } from "react-router-dom";
import { useUpload } from "../api/hooks";
import { ApiError } from "../api/client";
import type { UploadResposta } from "../api/types";
import { StatusBadge } from "../components/StatusBadge";
import { Card, btn } from "../components/ui";
import { IconUpload } from "../components/icons";

type EstadoItem = "fila" | "processando" | "concluido" | "erro";

interface ItemFila {
  id: string;
  file: File;
  estado: EstadoItem;
  mensagem?: string;
  resultado?: UploadResposta;
}

function tamanho(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function UploadPage() {
  const [fila, setFila] = useState<ItemFila[]>([]);
  const [processando, setProcessando] = useState(false);
  const upload = useUpload();

  const onDrop = useCallback((aceitos: File[], rejeitados: readonly { file: File }[]) => {
    const novos: ItemFila[] = [
      ...aceitos.map((file) => ({ id: crypto.randomUUID(), file, estado: "fila" as const })),
      ...rejeitados.map(({ file }) => ({
        id: crypto.randomUUID(),
        file,
        estado: "erro" as const,
        mensagem: "Arquivo não é um PDF válido.",
      })),
    ];
    setFila((f) => [...f, ...novos]);
  }, []);

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    accept: { "application/pdf": [".pdf"] },
  });

  async function processar() {
    setProcessando(true);
    const pendentes = fila.filter((i) => i.estado === "fila");
    for (const item of pendentes) {
      setFila((f) => f.map((i) => (i.id === item.id ? { ...i, estado: "processando" } : i)));
      try {
        const resultado = await upload.mutateAsync(item.file);
        setFila((f) => f.map((i) => (i.id === item.id ? { ...i, estado: "concluido", resultado } : i)));
      } catch (e) {
        const mensagem = e instanceof ApiError ? e.message : "Falha ao enviar o arquivo.";
        setFila((f) => f.map((i) => (i.id === item.id ? { ...i, estado: "erro", mensagem } : i)));
      }
    }
    setProcessando(false);
  }

  const pendentes = fila.filter((i) => i.estado === "fila").length;
  const temItens = fila.length > 0;

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold tracking-tight text-ink">Enviar extratos</h1>
          <p className="text-sm text-ink2">Os PDFs são extraídos pelo Claude e conferidos automaticamente.</p>
        </div>
        {temItens && (
          <button type="button" onClick={() => setFila([])} className={btn("ghost")}>
            Limpar fila
          </button>
        )}
      </div>

      <div
        {...getRootProps()}
        className={`cursor-pointer rounded-xl border-2 border-dashed p-12 text-center transition-colors ${
          isDragActive ? "border-accent bg-accent/5" : "border-edge2 bg-panel hover:border-accent/60"
        }`}
      >
        <input {...getInputProps()} />
        <span className="mx-auto mb-3 grid h-12 w-12 place-items-center rounded-full bg-accent/15 text-accent ring-1 ring-inset ring-accent/30">
          <IconUpload width={22} height={22} />
        </span>
        <p className="text-sm font-medium text-ink">Arraste PDFs de extratos aqui, ou clique para selecionar</p>
        <p className="mt-1 text-xs text-ink3">
          CDB, RDB, LCI, LCA, fundos e poupança — Caixa, Santander, Sicoob e outros
        </p>
      </div>

      {temItens && (
        <Card className="overflow-hidden">
          <table className="w-full text-sm">
            <thead className="border-b border-edge text-left text-2xs uppercase tracking-wide text-ink3">
              <tr>
                <th className="px-3 py-2.5 font-medium">Arquivo</th>
                <th className="px-3 py-2.5 font-medium">Tamanho</th>
                <th className="px-3 py-2.5 font-medium">Situação</th>
                <th className="px-3 py-2.5 font-medium">Resultado</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-edge/60">
              {fila.map((item) => (
                <tr key={item.id}>
                  <td className="px-3 py-2.5 font-medium text-ink">{item.file.name}</td>
                  <td className="num px-3 py-2.5 text-ink2">{tamanho(item.file.size)}</td>
                  <td className="px-3 py-2.5">
                    <SituacaoBadge estado={item.estado} />
                  </td>
                  <td className="px-3 py-2.5 text-ink2">
                    {item.estado === "concluido" && item.resultado ? (
                      <ResultadoResumo resultado={item.resultado} />
                    ) : item.estado === "erro" ? (
                      <span className="text-erro">{item.mensagem}</span>
                    ) : (
                      "—"
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}

      <div className="flex items-center gap-3">
        <button
          type="button"
          onClick={processar}
          disabled={pendentes === 0 || processando}
          className={btn("primary")}
        >
          {processando ? "Processando…" : `Processar ${pendentes > 0 ? `(${pendentes})` : ""}`}
        </button>
        <Link to="/" className="text-sm font-medium text-accent hover:underline">
          Ver lançamentos →
        </Link>
      </div>
    </div>
  );
}

function SituacaoBadge({ estado }: { estado: EstadoItem }) {
  const mapa: Record<EstadoItem, { rotulo: string; classe: string }> = {
    fila: { rotulo: "Na fila", classe: "text-ink2 bg-panel2 border-edge2" },
    processando: { rotulo: "Processando", classe: "text-info bg-info/10 border-info/30" },
    concluido: { rotulo: "Concluído", classe: "text-ok bg-ok/10 border-ok/30" },
    erro: { rotulo: "Erro", classe: "text-erro bg-erro/10 border-erro/30" },
  };
  const m = mapa[estado];
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full border px-2 py-0.5 text-2xs font-semibold uppercase tracking-wide ${m.classe}`}
    >
      <span
        className={`h-1.5 w-1.5 rounded-full bg-current ${estado === "processando" ? "animate-pulse" : ""}`}
      />
      {m.rotulo}
    </span>
  );
}

function ResultadoResumo({ resultado }: { resultado: UploadResposta }) {
  const doc = resultado.documentos[0];
  const n = resultado.lancamentos.length;
  return (
    <div className="flex items-center gap-2">
      {doc && <StatusBadge status={doc.statusProcessamento} />}
      <span className="text-ink2">
        {n} {n === 1 ? "lançamento" : "lançamentos"}
      </span>
      {doc && (
        <Link to={`/documentos/${doc.id}`} className="text-accent hover:underline">
          detalhes
        </Link>
      )}
    </div>
  );
}
