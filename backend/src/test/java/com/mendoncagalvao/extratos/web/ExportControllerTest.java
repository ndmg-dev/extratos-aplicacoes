package com.mendoncagalvao.extratos.web;

import com.mendoncagalvao.extratos.domain.Lancamento;
import com.mendoncagalvao.extratos.repository.LancamentoRepository;
import com.mendoncagalvao.extratos.web.export.ExportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExportController.class)
@Import(ExportService.class)
class ExportControllerTest {

    @Autowired
    MockMvc mvc;
    @MockBean
    LancamentoRepository repo;

    private static Lancamento lanc() {
        Lancamento l = new Lancamento();
        l.setId(UUID.randomUUID());
        l.setChave("12345678000195|CAIXA|1234|1|2026-09");
        l.setStatus("OK");
        l.setBanco("CAIXA");
        l.setCompetencia("2026-09");
        l.setSaldoFinalBruto(new BigDecimal("1010.50"));
        return l;
    }

    @Test
    @DisplayName("Export CSV usa ; e vírgula decimal, com cabeçalho na ordem da §4.2")
    @SuppressWarnings("unchecked")
    void csv() throws Exception {
        when(repo.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(lanc()));

        mvc.perform(get("/api/lancamentos/export").param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("lancamentos.csv")))
                .andExpect(content().string(containsString("chave;status;motivos")))
                .andExpect(content().string(containsString("1010,50")));
    }

    @Test
    @DisplayName("Export XLSX retorna um arquivo .xlsx (zip PK)")
    @SuppressWarnings("unchecked")
    void xlsx() throws Exception {
        when(repo.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(lanc()));

        byte[] body = mvc.perform(get("/api/lancamentos/export").param("format", "xlsx"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("lancamentos.xlsx")))
                .andReturn().getResponse().getContentAsByteArray();
        // arquivos .xlsx são zip: começam com "PK"
        org.assertj.core.api.Assertions.assertThat(new String(body, 0, 2, java.nio.charset.StandardCharsets.ISO_8859_1))
                .isEqualTo("PK");
    }

    @Test
    @DisplayName("Formato inválido retorna 400")
    @SuppressWarnings("unchecked")
    void formatoInvalido() throws Exception {
        when(repo.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(lanc()));
        mvc.perform(get("/api/lancamentos/export").param("format", "pdf"))
                .andExpect(status().isBadRequest());
    }
}
