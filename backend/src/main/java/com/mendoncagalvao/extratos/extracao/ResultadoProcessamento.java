package com.mendoncagalvao.extratos.extracao;

import com.mendoncagalvao.extratos.domain.Documento;
import com.mendoncagalvao.extratos.domain.Lancamento;

import java.util.List;

/**
 * Resultado do processamento de um documento: o documento persistido e seus lançamentos.
 */
public record ResultadoProcessamento(Documento documento, List<Lancamento> lancamentos) {
}
