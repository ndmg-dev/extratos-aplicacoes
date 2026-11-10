package com.mendoncagalvao.extratos.repository;

import com.mendoncagalvao.extratos.domain.Lancamento;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementação do upsert por {@code chave}. Spring Data detecta esta classe pelo sufixo
 * {@code Impl} e a mistura ao {@code LancamentoRepository}.
 */
@Repository
public class LancamentoRepositoryImpl implements LancamentoRepositoryCustom {

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional
    public Lancamento upsertPorChave(Lancamento lancamento) {
        TypedQuery<Lancamento> q = em.createQuery(
                "select l from Lancamento l where l.chave = :chave", Lancamento.class);
        q.setParameter("chave", lancamento.getChave());
        List<Lancamento> existentes = q.setMaxResults(1).getResultList();

        if (!existentes.isEmpty()) {
            // atualiza todas as colunas da linha existente (mesma chave)
            lancamento.setId(existentes.get(0).getId());
        } else {
            lancamento.setId(null); // garante INSERT com id gerado
        }
        return em.merge(lancamento);
    }
}
