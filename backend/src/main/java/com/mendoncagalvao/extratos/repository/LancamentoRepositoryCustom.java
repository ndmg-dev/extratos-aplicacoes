package com.mendoncagalvao.extratos.repository;

import com.mendoncagalvao.extratos.domain.Lancamento;

public interface LancamentoRepositoryCustom {

    /**
     * Upsert pela coluna {@code chave} (equivalente ao {@code appendOrUpdate} do Google Sheets,
     * §4.3): insere se a chave não existe; atualiza todas as colunas se existe. Não duplica
     * reenvios.
     */
    Lancamento upsertPorChave(Lancamento lancamento);
}
