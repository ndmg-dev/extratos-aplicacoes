package com.mendoncagalvao.extratos.extracao;

import com.mendoncagalvao.extratos.claude.ClaudeClient;
import com.mendoncagalvao.extratos.claude.ClaudeException;
import com.mendoncagalvao.extratos.claude.ClaudeResult;
import com.mendoncagalvao.extratos.config.ExtracaoProperties;
import com.mendoncagalvao.extratos.domain.Documento;
import com.mendoncagalvao.extratos.domain.Lancamento;
import com.mendoncagalvao.extratos.repository.DocumentoRepository;
import com.mendoncagalvao.extratos.storage.NomeadorArquivo;
import com.mendoncagalvao.extratos.storage.StorageService;
import com.mendoncagalvao.extratos.validacao.ValidacaoService;
import com.mendoncagalvao.extratos.validacao.model.Aplicacao;
import com.mendoncagalvao.extratos.validacao.model.ExtratoExtraido;
import com.mendoncagalvao.extratos.validacao.model.LinhaLancamento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExtracaoServiceImplTest {

    private static final Instant FIXED = Instant.parse("2026-11-04T12:00:00Z");

    private ClaudeClient claudeClient;
    private StorageService storageService;
    private DocumentoRepository documentoRepository;
    private ExtracaoPersistencia persistencia;
    private ExtracaoServiceImpl service;

    @BeforeEach
    void setUp() {
        claudeClient = mock(ClaudeClient.class);
        storageService = mock(StorageService.class);
        documentoRepository = mock(DocumentoRepository.class);
        persistencia = mock(ExtracaoPersistencia.class);

        Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
        service = new ExtracaoServiceImpl(
                claudeClient,
                new ValidacaoService(clock),
                new NomeadorArquivo(),
                storageService,
                documentoRepository,
                persistencia,
                new ExtracaoProperties(0, "America/Sao_Paulo"),
                clock);

        // persistencia devolve o documento recebido (com id) + entidades mapeadas
        when(persistencia.persistir(any(), any())).thenAnswer(inv -> {
            Documento d = inv.getArgument(0);
            List<LinhaLancamento> linhas = inv.getArgument(1);
            if (d.getId() == null) {
                d.setId(UUID.randomUUID());
            }
            List<Lancamento> ents = linhas.stream().map(l -> LancamentoMapper.toEntity(l, d.getId())).toList();
            return new ResultadoProcessamento(d, ents);
        });
    }

    private static ExtratoExtraido extratoOk() {
        ExtratoExtraido d = new ExtratoExtraido();
        d.documentoReconhecido = true;
        d.banco = "CAIXA";
        d.clienteCnpj = "12345678000195";
        d.conta = "1234";
        d.competencia = "2026-09";
        d.totalBrutoInformado = new BigDecimal("1010.00");
        Aplicacao a = new Aplicacao();
        a.identificador = "1";
        a.detalhado = true;
        a.saldoInicialBruto = new BigDecimal("1000.00");
        a.aplicacoes = BigDecimal.ZERO;
        a.resgatesBrutos = BigDecimal.ZERO;
        a.rendimentoBruto = new BigDecimal("10.00");
        a.saldoFinalBruto = new BigDecimal("1010.00");
        d.aplicacoes = List.of(a);
        return d;
    }

    private static ArquivoUpload arquivo() {
        return new ArquivoUpload("extrato.pdf", "application/pdf", "%PDF-1.4".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Happy path: arquiva, valida e persiste com status PROCESSADO")
    void happyPath() {
        when(documentoRepository.findFirstBySha256(any())).thenReturn(Optional.empty());
        when(claudeClient.extrair(any(), eq("extrato.pdf")))
                .thenReturn(new ClaudeResult(extratoOk(), "tool_use", "{\"ok\":true}"));

        ResultadoProcessamento r = service.processar(arquivo());

        assertThat(r.documento().getStatusProcessamento()).isEqualTo(Documento.STATUS_PROCESSADO);
        assertThat(r.documento().getBanco()).isEqualTo("CAIXA");
        assertThat(r.documento().getNomePadronizado()).isEqualTo("CAIXA_2026-09_1234.pdf");
        assertThat(r.documento().getSha256()).isNotBlank();
        assertThat(r.documento().getRawClaudeJson()).isEqualTo("{\"ok\":true}");
        assertThat(r.lancamentos()).hasSize(1);
        assertThat(r.lancamentos().get(0).getStatus()).isEqualTo("OK");

        // arquivou no storage com a chave padronizada
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storageService).salvar(key.capture(), any());
        assertThat(key.getValue()).isEqualTo("12345678000195/2026/09/aplicacoes/CAIXA_2026-09_1234.pdf");
    }

    @Test
    @DisplayName("Falha do Claude: documento ERRO, PDF arquivado, lançamento 'Sem resposta estruturada'")
    void falhaClaude() {
        when(documentoRepository.findFirstBySha256(any())).thenReturn(Optional.empty());
        when(claudeClient.extrair(any(), any())).thenThrow(new ClaudeException("timeout"));

        ResultadoProcessamento r = service.processar(arquivo());

        assertThat(r.documento().getStatusProcessamento()).isEqualTo(Documento.STATUS_ERRO);
        verify(storageService).salvar(any(), any()); // arquiva mesmo em erro
        assertThat(r.lancamentos()).hasSize(1);
        assertThat(r.lancamentos().get(0).getStatus()).isEqualTo("ERRO");
        assertThat(r.lancamentos().get(0).getMotivos()).isEqualTo("Sem resposta estruturada");
    }

    @Test
    @DisplayName("Idempotência: mesmo sha256 reaproveita o documento existente (não duplica)")
    void dedupPorSha256() {
        Documento existente = new Documento();
        UUID id = UUID.randomUUID();
        existente.setId(id);
        existente.setCriadoEm(Instant.parse("2026-01-01T00:00:00Z"));
        when(documentoRepository.findFirstBySha256(any())).thenReturn(Optional.of(existente));
        when(claudeClient.extrair(any(), any()))
                .thenReturn(new ClaudeResult(extratoOk(), "tool_use", "{}"));

        ResultadoProcessamento r = service.processar(arquivo());

        ArgumentCaptor<Documento> doc = ArgumentCaptor.forClass(Documento.class);
        verify(persistencia).persistir(doc.capture(), any());
        assertThat(doc.getValue()).isSameAs(existente);
        assertThat(doc.getValue().getId()).isEqualTo(id);
        assertThat(doc.getValue().getCriadoEm()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z")); // preservado
        assertThat(r.documento().getId()).isEqualTo(id);
    }

    @Test
    @DisplayName("Lote: um item com falha inesperada não derruba o lote")
    void loteNaoDerruba() {
        when(documentoRepository.findFirstBySha256(any())).thenReturn(Optional.empty());
        when(claudeClient.extrair(any(), any()))
                .thenReturn(new ClaudeResult(extratoOk(), "tool_use", "{}"));
        // primeira persistência ok, segunda lança erro inesperado
        // (doAnswer/doThrow evita invocar o stub anterior durante o re-stub)
        doAnswer(inv -> {
            Documento d = inv.getArgument(0);
            d.setId(UUID.randomUUID());
            return new ResultadoProcessamento(d, List.of());
        })
                .doThrow(new RuntimeException("falha no banco"))
                .when(persistencia).persistir(any(), any());

        List<ResultadoProcessamento> rs = service.processarLote(List.of(arquivo(), arquivo()));

        assertThat(rs).hasSize(2);
        assertThat(rs.get(1).documento().getStatusProcessamento()).isEqualTo(Documento.STATUS_ERRO);
    }

    @Test
    @DisplayName("Reprocessar: lê o PDF do storage e reprocessa o mesmo documento")
    void reprocessar() {
        Documento existente = new Documento();
        UUID id = UUID.randomUUID();
        existente.setId(id);
        existente.setStorageKey("12345678000195/2026/09/aplicacoes/CAIXA_2026-09_1234.pdf");
        existente.setNomeArquivoOriginal("extrato.pdf");
        existente.setSha256("sha-antigo");
        when(documentoRepository.findById(id)).thenReturn(Optional.of(existente));
        when(storageService.carregar(existente.getStorageKey()))
                .thenReturn("%PDF".getBytes(StandardCharsets.UTF_8));
        when(claudeClient.extrair(any(), eq("extrato.pdf")))
                .thenReturn(new ClaudeResult(extratoOk(), "tool_use", "{}"));

        ResultadoProcessamento r = service.reprocessar(id);

        assertThat(r.documento().getId()).isEqualTo(id);
        assertThat(r.documento().getStatusProcessamento()).isEqualTo(Documento.STATUS_PROCESSADO);
        verify(claudeClient).extrair(any(), eq("extrato.pdf"));
        verify(documentoRepository, never()).findFirstBySha256(any());
    }
}
