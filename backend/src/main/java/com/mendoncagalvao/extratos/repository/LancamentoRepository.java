package com.mendoncagalvao.extratos.repository;

import com.mendoncagalvao.extratos.domain.Lancamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LancamentoRepository
        extends JpaRepository<Lancamento, UUID>,
        JpaSpecificationExecutor<Lancamento>,
        LancamentoRepositoryCustom {

    Optional<Lancamento> findByChave(String chave);

    List<Lancamento> findByDocumentoIdOrderByIdentificadorAsc(UUID documentoId);
}
