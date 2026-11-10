package com.mendoncagalvao.extratos.validacao.model;

import java.math.BigDecimal;

/**
 * Uma aplicação/nota/produto do extrato, conforme o item do array {@code aplicacoes}
 * do esquema da tool {@code registrar_extrato} (Apêndice B).
 *
 * <p>Campos monetários são {@link BigDecimal}; valores ausentes permanecem {@code null}
 * (nunca zero), exatamente como no workflow original.
 */
public class Aplicacao {

    public String identificador;
    public String produto;
    /** {@code false} quando o extrato não detalha a aplicação. {@code null} = não informado (tratado como detalhado). */
    public Boolean detalhado;
    public String dataAplicacao;
    public String dataVencimento;
    public String indexador;

    public BigDecimal saldoInicialBruto;
    public BigDecimal saldoInicialLiquido;
    public BigDecimal aplicacoes;
    public BigDecimal resgatesBrutos;
    public BigDecimal resgatesLiquidos;
    public BigDecimal rendimentoBruto;
    public BigDecimal irRetido;
    public BigDecimal iofRetido;
    public BigDecimal irProvisionado;
    public BigDecimal iofProvisionado;
    public BigDecimal saldoFinalBruto;
    public BigDecimal saldoFinalLiquido;

    public Aplicacao() {
    }
}
