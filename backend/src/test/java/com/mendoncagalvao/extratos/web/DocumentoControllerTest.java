package com.mendoncagalvao.extratos.web;

import com.mendoncagalvao.extratos.domain.Documento;
import com.mendoncagalvao.extratos.domain.Lancamento;
import com.mendoncagalvao.extratos.extracao.ExtracaoService;
import com.mendoncagalvao.extratos.repository.DocumentoRepository;
import com.mendoncagalvao.extratos.repository.LancamentoRepository;
import com.mendoncagalvao.extratos.storage.StorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentoController.class)
class DocumentoControllerTest {

    @Autowired
    MockMvc mvc;
    @MockBean
    DocumentoRepository documentoRepo;
    @MockBean
    LancamentoRepository lancamentoRepo;
    @MockBean
    StorageService storageService;
    @MockBean
    ExtracaoService extracaoService;

    private static Documento doc(UUID id) {
        Documento d = new Documento();
        d.setId(id);
        d.setBanco("CAIXA");
        d.setNomePadronizado("CAIXA_2026-09_1234.pdf");
        d.setStorageKey("12345678000195/2026/09/aplicacoes/CAIXA_2026-09_1234.pdf");
        d.setStatusProcessamento(Documento.STATUS_PROCESSADO);
        d.setRawClaudeJson("{\"segredo\":true}");
        return d;
    }

    @Test
    @DisplayName("GET /{id} retorna documento + lançamentos, sem raw_claude_json por padrão")
    void detalhe() throws Exception {
        UUID id = UUID.randomUUID();
        when(documentoRepo.findById(id)).thenReturn(Optional.of(doc(id)));
        Lancamento l = new Lancamento();
        l.setId(UUID.randomUUID());
        l.setChave("k1");
        l.setStatus("OK");
        when(lancamentoRepo.findByDocumentoIdOrderByIdentificadorAsc(id)).thenReturn(List.of(l));

        mvc.perform(get("/api/documentos/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documento.banco").value("CAIXA"))
                .andExpect(jsonPath("$.documento.rawClaudeJson").doesNotExist())
                .andExpect(jsonPath("$.lancamentos[0].status").value("OK"));
    }

    @Test
    @DisplayName("GET /{id}?incluirRaw=true inclui o raw_claude_json")
    void detalheComRaw() throws Exception {
        UUID id = UUID.randomUUID();
        when(documentoRepo.findById(id)).thenReturn(Optional.of(doc(id)));
        when(lancamentoRepo.findByDocumentoIdOrderByIdentificadorAsc(id)).thenReturn(List.of());

        mvc.perform(get("/api/documentos/{id}", id).param("incluirRaw", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documento.rawClaudeJson").value("{\"segredo\":true}"));
    }

    @Test
    @DisplayName("GET /{id} inexistente retorna 404")
    void naoEncontrado() throws Exception {
        UUID id = UUID.randomUUID();
        when(documentoRepo.findById(id)).thenReturn(Optional.empty());
        mvc.perform(get("/api/documentos/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /{id}/pdf baixa o PDF arquivado")
    void baixarPdf() throws Exception {
        UUID id = UUID.randomUUID();
        Documento d = doc(id);
        when(documentoRepo.findById(id)).thenReturn(Optional.of(d));
        when(storageService.carregar(d.getStorageKey())).thenReturn("%PDF-1.4".getBytes(StandardCharsets.UTF_8));

        mvc.perform(get("/api/documentos/{id}/pdf", id))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"));
    }

    @Test
    @DisplayName("POST /{id}/reprocessar aciona o serviço")
    void reprocessar() throws Exception {
        UUID id = UUID.randomUUID();
        when(extracaoService.reprocessar(any()))
                .thenReturn(new com.mendoncagalvao.extratos.extracao.ResultadoProcessamento(doc(id), List.of()));

        mvc.perform(post("/api/documentos/{id}/reprocessar", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documento.statusProcessamento").value("PROCESSADO"));
    }
}
