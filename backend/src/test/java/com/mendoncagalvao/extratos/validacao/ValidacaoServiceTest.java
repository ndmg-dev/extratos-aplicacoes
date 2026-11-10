package com.mendoncagalvao.extratos.validacao;

import com.mendoncagalvao.extratos.validacao.model.Aplicacao;
import com.mendoncagalvao.extratos.validacao.model.ExtratoExtraido;
import com.mendoncagalvao.extratos.validacao.model.ItemResposta;
import com.mendoncagalvao.extratos.validacao.model.LinhaLancamento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de unidade do porte verbatim do nó "Normalizar e validar" (Apêndice C).
 * Cobre Caixa, Santander, Sicoob e os casos de borda da §8.
 */
class ValidacaoServiceTest {

    private static final Instant FIXED = Instant.parse("2026-11-04T12:00:00Z");
    private final ValidacaoService service = new ValidacaoService(Clock.fixed(FIXED, ZoneOffset.UTC));

    // ---- helpers -----------------------------------------------------------------------

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static ItemResposta item(ExtratoExtraido d) {
        return new ItemResposta(d, "end_turn", "extrato.pdf", "");
    }

    private static ItemResposta item(ExtratoExtraido d, String stopReason) {
        return new ItemResposta(d, stopReason, "extrato.pdf", "");
    }

    private LinhaLancamento one(ExtratoExtraido d) {
        List<LinhaLancamento> linhas = service.normalizarEValidar(item(d));
        assertThat(linhas).hasSize(1);
        return linhas.get(0);
    }

    // ---- CAIXA --------------------------------------------------------------------------

    @Test
    @DisplayName("Caixa: aplicação detalhada que fecha o saldo -> OK")
    void caixaFechaOk() {
        ExtratoExtraido d = new ExtratoExtraido();
        d.documentoReconhecido = true;
        d.banco = "CAIXA";
        d.clienteNome = "EMPRESA LTDA";
        d.clienteCnpj = "12.345.678/0001-95";
        d.conta = "0001-1234";
        d.competencia = "2026-09";
        d.totalBrutoInformado = bd("1010.00");

        Aplicacao a = new Aplicacao();
        a.identificador = "123456";
        a.produto = "CDB";
        a.detalhado = true;
        a.saldoInicialBruto = bd("1000.00");
        a.aplicacoes = BigDecimal.ZERO;
        a.resgatesBrutos = BigDecimal.ZERO;
        a.rendimentoBruto = bd("10.00");
        a.saldoFinalBruto = bd("1010.00");
        d.aplicacoes = List.of(a);

        LinhaLancamento l = one(d);
        assertThat(l.status()).isEqualTo("OK");
        assertThat(l.motivos()).isEmpty();
        assertThat(l.origemSaldoInicial()).isEqualTo("extrato");
        assertThat(l.saldoInicialBruto()).isEqualByComparingTo("1000.00");
        assertThat(l.diferencaFechamento()).isEqualByComparingTo("0.00");
        assertThat(l.irRetido()).isEqualByComparingTo("0.00");
        assertThat(l.iofRetido()).isEqualByComparingTo("0.00");
        assertThat(l.clienteCnpj()).isEqualTo("12345678000195");
        assertThat(l.chave()).isEqualTo("12345678000195|CAIXA|00011234|123456|2026-09");
        assertThat(l.processadoEm()).isEqualTo(FIXED);
    }

    // ---- SANTANDER ----------------------------------------------------------------------

    @Test
    @DisplayName("Santander: aplicação detalhada OK + RESGATE_AUTOMATICO (detalhado=false) SEM_DETALHE")
    void santanderComResgateAutomatico() {
        ExtratoExtraido d = new ExtratoExtraido();
        d.documentoReconhecido = true;
        d.banco = "SANTANDER";
        d.clienteCnpj = "12345678000195";
        d.conta = "13000999";
        d.competencia = "2026-08";
        d.totalBrutoInformado = bd("5250.00");

        Aplicacao a1 = new Aplicacao();
        a1.identificador = "1";
        a1.detalhado = true;
        a1.saldoInicialBruto = bd("5000.00");
        a1.aplicacoes = BigDecimal.ZERO;
        a1.resgatesBrutos = BigDecimal.ZERO;
        a1.rendimentoBruto = bd("50.00");
        a1.saldoFinalBruto = bd("5050.00");
        a1.irProvisionado = bd("7.50");

        Aplicacao a2 = new Aplicacao();
        a2.identificador = "RESGATE_AUTOMATICO";
        a2.produto = "Aplicação automática (resgate automático)";
        a2.detalhado = false;
        a2.saldoFinalBruto = bd("200.00");

        d.aplicacoes = List.of(a1, a2);

        List<LinhaLancamento> linhas = service.normalizarEValidar(item(d));
        assertThat(linhas).hasSize(2);

        LinhaLancamento l1 = linhas.get(0);
        assertThat(l1.status()).isEqualTo("OK");
        assertThat(l1.irProvisionado()).isEqualByComparingTo("7.50");
        assertThat(l1.diferencaFechamento()).isEqualByComparingTo("0.00");

        LinhaLancamento l2 = linhas.get(1);
        assertThat(l2.status()).isEqualTo("SEM_DETALHE");
        assertThat(l2.identificador()).isEqualTo("RESGATE_AUTOMATICO");
        assertThat(l2.saldoFinalBruto()).isEqualByComparingTo("200.00");
        assertThat(l2.saldoInicialBruto()).isNull();
        assertThat(l2.diferencaFechamento()).isNull();
    }

    // ---- SICOOB -------------------------------------------------------------------------

    @Test
    @DisplayName("Sicoob: entrada CONSOLIDADO detalhada que fecha -> OK")
    void sicoobConsolidadoOk() {
        ExtratoExtraido d = new ExtratoExtraido();
        d.documentoReconhecido = true;
        d.banco = "SICOOB";
        d.clienteCnpj = "12345678000195";
        d.conta = "9988";
        d.competencia = "2026-07";
        d.totalBrutoInformado = bd("10000.00");

        Aplicacao a = new Aplicacao();
        a.identificador = "CONSOLIDADO";
        a.detalhado = true;
        a.saldoInicialBruto = bd("10000.00");
        a.aplicacoes = bd("500.00");
        a.resgatesBrutos = bd("600.00");
        a.rendimentoBruto = bd("100.00");
        a.irRetido = bd("15.00");
        a.iofRetido = bd("2.00");
        a.saldoFinalBruto = bd("10000.00");
        d.aplicacoes = List.of(a);

        LinhaLancamento l = one(d);
        assertThat(l.status()).isEqualTo("OK");
        assertThat(l.identificador()).isEqualTo("CONSOLIDADO");
        assertThat(l.irRetido()).isEqualByComparingTo("15.00");
        assertThat(l.iofRetido()).isEqualByComparingTo("2.00");
        assertThat(l.diferencaFechamento()).isEqualByComparingTo("0.00");
    }

    // ---- BORDA: saldo não fecha ---------------------------------------------------------

    @Test
    @DisplayName("Saldo não fecha -> REVISAR com motivo e diferenca_fechamento preenchida")
    void saldoNaoFecha() {
        ExtratoExtraido d = extratoBase();
        Aplicacao a = aplicacaoBase();
        a.saldoFinalBruto = bd("1050.00"); // deveria ser 1010
        d.aplicacoes = List.of(a);
        d.totalBrutoInformado = bd("1050.00"); // casa com a soma p/ isolar o motivo de fechamento

        LinhaLancamento l = one(d);
        assertThat(l.status()).isEqualTo("REVISAR");
        assertThat(l.motivos()).isEqualTo("Saldo não fecha (diferença 40)");
        assertThat(l.diferencaFechamento()).isEqualByComparingTo("40.00");
    }

    // ---- BORDA: sem CNPJ ----------------------------------------------------------------

    @Test
    @DisplayName("CNPJ ausente/!=14 dígitos -> motivo e REVISAR, cliente_cnpj vazio")
    void semCnpj() {
        ExtratoExtraido d = extratoBase();
        d.clienteCnpj = null;
        d.aplicacoes = List.of(aplicacaoBase());

        LinhaLancamento l = one(d);
        assertThat(l.status()).isEqualTo("REVISAR");
        assertThat(l.motivos()).isEqualTo("CNPJ não identificado no extrato (arquivo salvo em _SEM_CNPJ)");
        assertThat(l.clienteCnpj()).isEmpty();
        assertThat(l.chave()).startsWith("|BANCOX|");
    }

    // ---- BORDA: sem aplicações ----------------------------------------------------------

    @Test
    @DisplayName("Sem aplicações -> única linha REVISAR com chave ERRO|...")
    void semAplicacoes() {
        ExtratoExtraido d = extratoBase();
        d.totalBrutoInformado = null; // isola o motivo de "sem aplicações"
        d.aplicacoes = List.of();

        List<LinhaLancamento> linhas = service.normalizarEValidar(item(d));
        assertThat(linhas).hasSize(1);
        LinhaLancamento l = linhas.get(0);
        assertThat(l.status()).isEqualTo("REVISAR");
        assertThat(l.chave()).isEqualTo("ERRO||extrato.pdf");
        assertThat(l.motivos()).isEqualTo("Nenhuma aplicação encontrada");
        assertThat(l.banco()).isEqualTo("BANCOX");
        assertThat(l.competencia()).isEqualTo("2026-10");
    }

    @Test
    @DisplayName("Sem aplicações com total informado -> motivos de 'sem aplicações' e de soma, na ordem")
    void semAplicacoesComTotal() {
        ExtratoExtraido d = extratoBase(); // total = 1010.00
        d.aplicacoes = List.of();

        List<LinhaLancamento> linhas = service.normalizarEValidar(item(d));
        assertThat(linhas).hasSize(1);
        assertThat(linhas.get(0).motivos()).isEqualTo(
                "Nenhuma aplicação encontrada | Soma das aplicações (0) difere do total do extrato (1010)");
    }

    // ---- BORDA: max_tokens --------------------------------------------------------------

    @Test
    @DisplayName("stop_reason max_tokens -> motivo 'Resposta cortada (max_tokens)' e REVISAR")
    void maxTokens() {
        ExtratoExtraido d = extratoBase();
        d.aplicacoes = List.of(aplicacaoBase());

        List<LinhaLancamento> linhas = service.normalizarEValidar(item(d, "max_tokens"));
        assertThat(linhas).hasSize(1);
        LinhaLancamento l = linhas.get(0);
        assertThat(l.status()).isEqualTo("REVISAR");
        assertThat(l.motivos()).isEqualTo("Resposta cortada (max_tokens)");
    }

    // ---- BORDA: detalhado=false ---------------------------------------------------------

    @Test
    @DisplayName("detalhado=false -> SEM_DETALHE mesmo quando o saldo não fecharia")
    void detalhadoFalse() {
        ExtratoExtraido d = extratoBase();
        Aplicacao a = aplicacaoBase();
        a.detalhado = false;
        a.saldoFinalBruto = bd("99999.00"); // não fecharia, mas não há checagem
        d.aplicacoes = List.of(a);
        d.totalBrutoInformado = bd("99999.00");

        LinhaLancamento l = one(d);
        assertThat(l.status()).isEqualTo("SEM_DETALHE");
        assertThat(l.diferencaFechamento()).isNull();
    }

    // ---- BORDA: saldo inicial calculado -------------------------------------------------

    @Test
    @DisplayName("saldo_inicial_bruto null com fin e rend presentes -> calculado, sem checagem de fechamento")
    void saldoInicialCalculado() {
        ExtratoExtraido d = extratoBase();
        Aplicacao a = aplicacaoBase();
        a.saldoInicialBruto = null;
        a.aplicacoes = bd("0.00");
        a.resgatesBrutos = bd("0.00");
        a.rendimentoBruto = bd("10.00");
        a.saldoFinalBruto = bd("1010.00");
        d.aplicacoes = List.of(a);

        LinhaLancamento l = one(d);
        assertThat(l.status()).isEqualTo("OK");
        assertThat(l.origemSaldoInicial()).isEqualTo("calculado");
        assertThat(l.saldoInicialBruto()).isEqualByComparingTo("1000.00");
        assertThat(l.diferencaFechamento()).isNull(); // origem != extrato
    }

    // ---- BORDA: sem resposta estruturada ------------------------------------------------

    @Test
    @DisplayName("dados null -> ERRO 'Sem resposta estruturada'")
    void semRespostaEstruturada() {
        List<LinhaLancamento> linhas = service.normalizarEValidar(
                new ItemResposta(null, "end_turn", "quebrado.pdf", "mail-1"));
        assertThat(linhas).hasSize(1);
        LinhaLancamento l = linhas.get(0);
        assertThat(l.status()).isEqualTo("ERRO");
        assertThat(l.motivos()).isEqualTo("Sem resposta estruturada");
        assertThat(l.chave()).isEqualTo("ERRO|mail-1|quebrado.pdf");
        assertThat(l.arquivo()).isEqualTo("quebrado.pdf");
    }

    // ---- BORDA: observação do extrator não rebaixa status -------------------------------

    @Test
    @DisplayName("Obs. do extrator entra em motivos mas mantém OK")
    void observacaoNaoRebaixa() {
        ExtratoExtraido d = extratoBase();
        d.observacoes = "Layout incomum, conferir";
        d.aplicacoes = List.of(aplicacaoBase());

        LinhaLancamento l = one(d);
        assertThat(l.status()).isEqualTo("OK");
        assertThat(l.motivos()).isEqualTo("Obs. do extrator: Layout incomum, conferir");
    }

    // ---- BORDA: soma difere do total ----------------------------------------------------

    @Test
    @DisplayName("Soma das aplicações difere do total informado -> motivo e REVISAR")
    void somaDifereDoTotal() {
        ExtratoExtraido d = extratoBase();
        d.totalBrutoInformado = bd("9999.00");
        d.aplicacoes = List.of(aplicacaoBase()); // fin = 1010

        LinhaLancamento l = one(d);
        assertThat(l.status()).isEqualTo("REVISAR");
        assertThat(l.motivos()).isEqualTo("Soma das aplicações (1010) difere do total do extrato (9999)");
    }

    // ---- BORDA: campos obrigatórios ausentes --------------------------------------------

    @Test
    @DisplayName("detalhado=true com saldo_final ausente -> 'Campos obrigatórios ausentes' e REVISAR")
    void camposObrigatoriosAusentes() {
        ExtratoExtraido d = extratoBase();
        Aplicacao a = aplicacaoBase();
        a.saldoInicialBruto = bd("1000.00");
        a.rendimentoBruto = bd("10.00");
        a.saldoFinalBruto = null; // ausente; ini não é null, então não há cálculo
        d.aplicacoes = List.of(a);
        d.totalBrutoInformado = null;

        LinhaLancamento l = one(d);
        assertThat(l.status()).isEqualTo("REVISAR");
        assertThat(l.motivos()).isEqualTo("Campos obrigatórios ausentes");
    }

    // ---- bases reutilizáveis ------------------------------------------------------------

    private static ExtratoExtraido extratoBase() {
        ExtratoExtraido d = new ExtratoExtraido();
        d.documentoReconhecido = true;
        d.banco = "BANCOX";
        d.clienteNome = "EMPRESA LTDA";
        d.clienteCnpj = "12345678000195";
        d.conta = "555";
        d.competencia = "2026-10";
        d.totalBrutoInformado = bd("1010.00");
        return d;
    }

    private static Aplicacao aplicacaoBase() {
        Aplicacao a = new Aplicacao();
        a.identificador = "ID1";
        a.detalhado = true;
        a.saldoInicialBruto = bd("1000.00");
        a.aplicacoes = BigDecimal.ZERO;
        a.resgatesBrutos = BigDecimal.ZERO;
        a.rendimentoBruto = bd("10.00");
        a.saldoFinalBruto = bd("1010.00");
        return a;
    }
}
