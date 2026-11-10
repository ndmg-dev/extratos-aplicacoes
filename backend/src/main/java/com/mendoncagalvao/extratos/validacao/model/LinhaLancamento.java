package com.mendoncagalvao.extratos.validacao.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Uma linha de lançamento normalizada e validada (uma por aplicação/nota/produto),
 * resultado do {@code ValidacaoService}. Os campos seguem a ordem da §4.2 da spec
 * (exceto {@code documento_id}, que é atribuído na persistência).
 *
 * <p>Monetários são {@link BigDecimal} já arredondados a 2 casas ({@code r2}); valores
 * ausentes permanecem {@code null}, salvo as exceções da lógica (ir/iof retidos → 0).
 */
public record LinhaLancamento(
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
        Instant processadoEm
) {
    /** Valores de {@code status}. */
    public static final String STATUS_OK = "OK";
    public static final String STATUS_REVISAR = "REVISAR";
    public static final String STATUS_SEM_DETALHE = "SEM_DETALHE";
    public static final String STATUS_ERRO = "ERRO";

    /** Valores de {@code origem_saldo_inicial}. */
    public static final String ORIGEM_EXTRATO = "extrato";
    public static final String ORIGEM_CALCULADO = "calculado";
}
