package com.mendoncagalvao.extratos.web;

import com.mendoncagalvao.extratos.config.UploadProperties;
import com.mendoncagalvao.extratos.domain.Documento;
import com.mendoncagalvao.extratos.domain.Lancamento;
import com.mendoncagalvao.extratos.extracao.ExtracaoService;
import com.mendoncagalvao.extratos.extracao.ResultadoProcessamento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UploadController.class)
@Import(UploadControllerTest.Config.class)
class UploadControllerTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class Config {
        @Bean
        UploadProperties uploadProperties() {
            return new UploadProperties(20);
        }
    }

    @Autowired
    MockMvc mvc;
    @MockBean
    ExtracaoService extracaoService;

    private static MockMultipartFile pdf(String nome) {
        return new MockMultipartFile("files", nome, "application/pdf", "%PDF-1.4".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Upload válido processa e retorna documentos + lançamentos + contagem")
    void uploadValido() throws Exception {
        Documento d = new Documento();
        d.setId(UUID.randomUUID());
        d.setStatusProcessamento(Documento.STATUS_PROCESSADO);
        d.setBanco("CAIXA");
        Lancamento l = new Lancamento();
        l.setId(UUID.randomUUID());
        l.setChave("k1");
        l.setStatus("OK");
        when(extracaoService.processarLote(any()))
                .thenReturn(List.of(new ResultadoProcessamento(d, List.of(l))));

        mvc.perform(multipart("/api/extratos/upload").file(pdf("extrato.pdf")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentos[0].banco").value("CAIXA"))
                .andExpect(jsonPath("$.lancamentos[0].status").value("OK"))
                .andExpect(jsonPath("$.contagemPorStatus.OK").value(1));
    }

    @Test
    @DisplayName("Upload de não-PDF é rejeitado com 400")
    void uploadNaoPdf() throws Exception {
        MockMultipartFile txt = new MockMultipartFile("files", "nota.txt", "text/plain", "oi".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/extratos/upload").file(txt))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").exists());
    }

    @Test
    @DisplayName("Upload sem o campo 'files' é rejeitado com 400")
    void uploadSemArquivo() throws Exception {
        mvc.perform(multipart("/api/extratos/upload"))
                .andExpect(status().isBadRequest());
    }
}
