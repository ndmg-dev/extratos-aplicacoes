package com.mendoncagalvao.extratos.web;

import com.mendoncagalvao.extratos.domain.Lancamento;
import com.mendoncagalvao.extratos.repository.LancamentoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LancamentoController.class)
class LancamentoControllerTest {

    @Autowired
    MockMvc mvc;
    @MockBean
    LancamentoRepository repo;

    private static Lancamento lanc(String status, String competencia, BigDecimal saldo) {
        Lancamento l = new Lancamento();
        l.setId(UUID.randomUUID());
        l.setChave("k-" + UUID.randomUUID());
        l.setStatus(status);
        l.setBanco("CAIXA");
        l.setCompetencia(competencia);
        l.setSaldoFinalBruto(saldo);
        return l;
    }

    @Test
    @DisplayName("GET /api/lancamentos retorna página filtrada")
    @SuppressWarnings("unchecked")
    void listar() throws Exception {
        when(repo.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(lanc("OK", "2026-09", new BigDecimal("1010.00")))));

        mvc.perform(get("/api/lancamentos").param("status", "OK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("OK"))
                .andExpect(jsonPath("$.content[0].banco").value("CAIXA"));
    }

    @Test
    @DisplayName("GET /api/lancamentos/summary agrega contagem por status e soma por competência")
    @SuppressWarnings("unchecked")
    void summary() throws Exception {
        when(repo.findAll(any(Specification.class)))
                .thenReturn(List.of(
                        lanc("OK", "2026-09", new BigDecimal("1000.00")),
                        lanc("OK", "2026-09", new BigDecimal("10.50")),
                        lanc("REVISAR", "2026-08", new BigDecimal("500.00"))));

        mvc.perform(get("/api/lancamentos/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.contagemPorStatus.OK").value(2))
                .andExpect(jsonPath("$.contagemPorStatus.REVISAR").value(1))
                .andExpect(jsonPath("$.contagemPorStatus.ERRO").value(0))
                .andExpect(jsonPath("$.somaSaldoFinalPorCompetencia['2026-09']").value(1010.50));
    }
}
