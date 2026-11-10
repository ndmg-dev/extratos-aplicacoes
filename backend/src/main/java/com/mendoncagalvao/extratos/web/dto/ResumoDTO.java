package com.mendoncagalvao.extratos.web.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Resumo dos lançamentos (respeita os mesmos filtros da listagem).
 *
 * @param contagemPorStatus              nº de lançamentos por status (OK/REVISAR/SEM_DETALHE/ERRO)
 * @param total                          total de lançamentos
 * @param somaSaldoFinalPorCompetencia   soma de {@code saldo_final_bruto} por competência
 */
public record ResumoDTO(
        Map<String, Long> contagemPorStatus,
        long total,
        Map<String, BigDecimal> somaSaldoFinalPorCompetencia
) {
}
