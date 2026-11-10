package com.mendoncagalvao.extratos.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Espelha a §4.2 (ordem das colunas preservada). */
public record LancamentoDTO(
        UUID id,
        String chave,
        String status,
        String motivos,
        String competencia,
        String banco,
        String clienteNome,
        String clienteCnpj,
        String agencia,
        String conta,
        String identificador,
        String produto,
        String dataAplicacao,
        String dataVencimento,
        BigDecimal saldoInicialBruto,
        String origemSaldoInicial,
        BigDecimal aplicacoes,
        BigDecimal resgatesBrutos,
        BigDecimal rendimentoBruto,
        BigDecimal irRetido,
        BigDecimal iofRetido,
        BigDecimal saldoFinalBruto,
        BigDecimal irProvisionado,
        BigDecimal iofProvisionado,
        BigDecimal saldoFinalLiquido,
        BigDecimal diferencaFechamento,
        String arquivo,
        String emailId,
        Instant processadoEm,
        UUID documentoId
) {
}
