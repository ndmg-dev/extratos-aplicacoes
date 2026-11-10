package com.mendoncagalvao.extratos.extracao;

import java.util.List;
import java.util.UUID;

/**
 * Orquestra o fluxo de processamento da §5. Interface preparada para execução assíncrona
 * (fila/@Async) no futuro — o MVP é síncrono.
 */
public interface ExtracaoService {

    /** Processa um único arquivo (ler → Claude → nomear/arquivar → validar → upsert). */
    ResultadoProcessamento processar(ArquivoUpload arquivo);

    /** Processa um lote; um item que falha vira {@code ERRO} sem derrubar o lote (§12). */
    List<ResultadoProcessamento> processarLote(List<ArquivoUpload> arquivos);

    /** Reexecuta extração/validação para um documento já arquivado. */
    ResultadoProcessamento reprocessar(UUID documentoId);
}
