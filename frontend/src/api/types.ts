export type Status = "OK" | "REVISAR" | "SEM_DETALHE" | "ERRO";

export interface Lancamento {
  id: string;
  chave: string;
  status: Status;
  motivos: string | null;
  competencia: string | null;
  banco: string | null;
  clienteNome: string | null;
  clienteCnpj: string | null;
  agencia: string | null;
  conta: string | null;
  identificador: string | null;
  produto: string | null;
  dataAplicacao: string | null;
  dataVencimento: string | null;
  saldoInicialBruto: number | null;
  origemSaldoInicial: string | null;
  aplicacoes: number | null;
  resgatesBrutos: number | null;
  rendimentoBruto: number | null;
  irRetido: number | null;
  iofRetido: number | null;
  saldoFinalBruto: number | null;
  irProvisionado: number | null;
  iofProvisionado: number | null;
  saldoFinalLiquido: number | null;
  diferencaFechamento: number | null;
  arquivo: string | null;
  emailId: string | null;
  processadoEm: string | null;
  documentoId: string | null;
}

export interface Documento {
  id: string;
  nomeArquivoOriginal: string | null;
  nomePadronizado: string | null;
  banco: string | null;
  competencia: string | null;
  cnpj: string | null;
  conta: string | null;
  storageKey: string | null;
  tamanhoBytes: number | null;
  sha256: string | null;
  statusProcessamento: "PROCESSADO" | "ERRO" | "PENDENTE" | string;
  stopReason: string | null;
  documentoReconhecido: boolean | null;
  totalBrutoInformado: number | null;
  observacoes: string | null;
  criadoEm: string | null;
  processadoEm: string | null;
  origem?: string | null;
  emailRemetente?: string | null;
  emailAssunto?: string | null;
  emailData?: string | null;
  rawClaudeJson?: string | null;
}

export interface EmailStatus {
  habilitado: boolean;
  temCredenciais: boolean;
  autorizado: boolean;
  emailConta: string | null;
  ultimaSincronizacao: string | null;
  emExecucao: boolean;
  novosUltimaExecucao: number;
  documentosUltimaExecucao: number;
  totalMensagensProcessadas: number;
  ultimoErro: string | null;
  pollIntervalMs: number;
}

export interface DocumentoDetalhe {
  documento: Documento;
  lancamentos: Lancamento[];
}

export interface UploadResposta {
  documentos: Documento[];
  lancamentos: Lancamento[];
  contagemPorStatus: Record<string, number>;
}

export interface Resumo {
  contagemPorStatus: Record<string, number>;
  total: number;
  somaSaldoFinalPorCompetencia: Record<string, number>;
}

export interface Pagina<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

export interface Filtros {
  status?: string;
  banco?: string;
  competencia?: string;
  cnpj?: string;
  q?: string;
}
