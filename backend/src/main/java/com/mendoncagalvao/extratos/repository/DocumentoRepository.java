package com.mendoncagalvao.extratos.repository;

import com.mendoncagalvao.extratos.domain.Documento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DocumentoRepository extends JpaRepository<Documento, UUID> {

    /** Dedup/idempotência por conteúdo (§5): detecta reenvio do mesmo PDF. */
    Optional<Documento> findFirstBySha256(String sha256);
}
