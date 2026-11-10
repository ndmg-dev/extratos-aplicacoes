package com.mendoncagalvao.extratos.repository;

import com.mendoncagalvao.extratos.domain.Documento;
import com.mendoncagalvao.extratos.domain.Lancamento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Casos de persistência compartilhados (upsert por {@code chave}, dedup por {@code sha256},
 * precisão monetária). Executados tanto com Testcontainers ({@code PersistenciaIT}) quanto
 * contra um Postgres externo ({@code PersistenciaExternaIT}).
 */
abstract class PersistenciaTestesBase {

    @Autowired
    LancamentoRepository lancamentoRepo;
    @Autowired
    DocumentoRepository documentoRepo;

    /** Isola cada teste independentemente do comportamento de rollback do contexto. */
    @BeforeEach
    void limpar() {
        lancamentoRepo.deleteAll();
        documentoRepo.deleteAll();
    }

    private static Lancamento lancamento(String chave, String status, BigDecimal saldoFinal) {
        Lancamento l = new Lancamento();
        l.setChave(chave);
        l.setStatus(status);
        l.setBanco("CAIXA");
        l.setCompetencia("2026-09");
        l.setClienteCnpj("12345678000195");
        l.setConta("1234");
        l.setIdentificador("1");
        l.setSaldoFinalBruto(saldoFinal);
        l.setProcessadoEm(Instant.parse("2026-11-04T12:00:00Z"));
        return l;
    }

    @Test
    @DisplayName("Upsert por chave: reenvio atualiza a linha existente e não duplica")
    void upsertNaoDuplica() {
        String chave = "12345678000195|CAIXA|1234|1|2026-09";

        Lancamento inserido = lancamentoRepo.upsertPorChave(
                lancamento(chave, "OK", new BigDecimal("1010.00")));
        UUID id = inserido.getId();
        assertThat(id).isNotNull();
        assertThat(lancamentoRepo.count()).isEqualTo(1);

        Lancamento atualizado = lancamentoRepo.upsertPorChave(
                lancamento(chave, "REVISAR", new BigDecimal("2020.00")));

        assertThat(lancamentoRepo.count()).isEqualTo(1);
        assertThat(atualizado.getId()).isEqualTo(id);
        Lancamento recarregado = lancamentoRepo.findByChave(chave).orElseThrow();
        assertThat(recarregado.getStatus()).isEqualTo("REVISAR");
        assertThat(recarregado.getSaldoFinalBruto()).isEqualByComparingTo("2020.00");
        assertThat(recarregado.getSaldoFinalBruto().scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("Chaves diferentes geram linhas distintas")
    void chavesDiferentes() {
        lancamentoRepo.upsertPorChave(lancamento("a|b|c|1|2026-09", "OK", new BigDecimal("1.00")));
        lancamentoRepo.upsertPorChave(lancamento("a|b|c|2|2026-09", "OK", new BigDecimal("2.00")));
        assertThat(lancamentoRepo.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("Documento: roundtrip, jsonb e dedup por sha256")
    void documentoRoundtrip() {
        Documento d = new Documento();
        d.setNomeArquivoOriginal("extrato.pdf");
        d.setNomePadronizado("CAIXA_2026-09_1234.pdf");
        d.setBanco("CAIXA");
        d.setCompetencia("2026-09");
        d.setCnpj("12345678000195");
        d.setStatusProcessamento(Documento.STATUS_PROCESSADO);
        d.setStopReason("tool_use");
        d.setRawClaudeJson("{\"banco\":\"CAIXA\",\"valor\":1010.00}");
        d.setDocumentoReconhecido(true);
        d.setTotalBrutoInformado(new BigDecimal("1010.00"));
        d.setSha256("abc123");
        d.setCriadoEm(Instant.parse("2026-11-04T12:00:00Z"));

        Documento salvo = documentoRepo.save(d);
        assertThat(salvo.getId()).isNotNull();

        Documento recarregado = documentoRepo.findById(salvo.getId()).orElseThrow();
        assertThat(recarregado.getRawClaudeJson()).contains("CAIXA");
        assertThat(recarregado.getTotalBrutoInformado()).isEqualByComparingTo("1010.00");
        assertThat(documentoRepo.findFirstBySha256("abc123")).isPresent();
        assertThat(documentoRepo.findFirstBySha256("inexistente")).isEmpty();
    }
}
