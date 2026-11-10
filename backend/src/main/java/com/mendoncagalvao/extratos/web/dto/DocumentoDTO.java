package com.mendoncagalvao.extratos.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Espelha a §4.1. {@code rawClaudeJson} só é incluído sob flag de auditoria. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DocumentoDTO(
        UUID id,
        String nomeArquivoOriginal,
        String nomePadronizado,
        String banco,
        String competencia,
        String cnpj,
        String conta,
        String storageKey,
        Long tamanhoBytes,
        String sha256,
        String statusProcessamento,
        String stopReason,
        Boolean documentoReconhecido,
        BigDecimal totalBrutoInformado,
        String observacoes,
        Instant criadoEm,
        Instant processadoEm,
        String origem,
        String emailRemetente,
        String emailAssunto,
        Instant emailData,
        String rawClaudeJson
) {
}
