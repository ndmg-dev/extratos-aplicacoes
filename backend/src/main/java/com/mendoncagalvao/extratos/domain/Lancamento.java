package com.mendoncagalvao.extratos.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Um lançamento (uma aplicação/nota/produto) — §4.2. As colunas seguem exatamente a ordem da
 * spec (ver também a migração {@code V1__init.sql}). O {@code upsert} é feito pela coluna
 * {@code chave} (única).
 */
@Entity
@Table(name = "lancamento", indexes = {
        @Index(name = "ix_lancamento_status", columnList = "status"),
        @Index(name = "ix_lancamento_banco", columnList = "banco"),
        @Index(name = "ix_lancamento_competencia", columnList = "competencia"),
        @Index(name = "ix_lancamento_cliente_cnpj", columnList = "cliente_cnpj")
})
public class Lancamento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String chave;

    @Column(nullable = false)
    private String status;

    @Column(columnDefinition = "text")
    private String motivos;

    @Column(length = 7)
    private String competencia;

    private String banco;
    private String clienteNome;

    @Column(length = 14)
    private String clienteCnpj;

    private String agencia;
    private String conta;
    private String identificador;
    private String produto;
    private String dataAplicacao;
    private String dataVencimento;

    @Column(precision = 18, scale = 2)
    private BigDecimal saldoInicialBruto;

    private String origemSaldoInicial;

    @Column(precision = 18, scale = 2)
    private BigDecimal aplicacoes;
    @Column(precision = 18, scale = 2)
    private BigDecimal resgatesBrutos;
    @Column(precision = 18, scale = 2)
    private BigDecimal rendimentoBruto;
    @Column(precision = 18, scale = 2)
    private BigDecimal irRetido;
    @Column(precision = 18, scale = 2)
    private BigDecimal iofRetido;
    @Column(precision = 18, scale = 2)
    private BigDecimal saldoFinalBruto;
    @Column(precision = 18, scale = 2)
    private BigDecimal irProvisionado;
    @Column(precision = 18, scale = 2)
    private BigDecimal iofProvisionado;
    @Column(precision = 18, scale = 2)
    private BigDecimal saldoFinalLiquido;
    @Column(precision = 18, scale = 2)
    private BigDecimal diferencaFechamento;

    private String arquivo;
    private String emailId;
    private Instant processadoEm;

    @Column(name = "documento_id")
    private UUID documentoId;

    public Lancamento() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getChave() { return chave; }
    public void setChave(String v) { this.chave = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getMotivos() { return motivos; }
    public void setMotivos(String v) { this.motivos = v; }
    public String getCompetencia() { return competencia; }
    public void setCompetencia(String v) { this.competencia = v; }
    public String getBanco() { return banco; }
    public void setBanco(String v) { this.banco = v; }
    public String getClienteNome() { return clienteNome; }
    public void setClienteNome(String v) { this.clienteNome = v; }
    public String getClienteCnpj() { return clienteCnpj; }
    public void setClienteCnpj(String v) { this.clienteCnpj = v; }
    public String getAgencia() { return agencia; }
    public void setAgencia(String v) { this.agencia = v; }
    public String getConta() { return conta; }
    public void setConta(String v) { this.conta = v; }
    public String getIdentificador() { return identificador; }
    public void setIdentificador(String v) { this.identificador = v; }
    public String getProduto() { return produto; }
    public void setProduto(String v) { this.produto = v; }
    public String getDataAplicacao() { return dataAplicacao; }
    public void setDataAplicacao(String v) { this.dataAplicacao = v; }
    public String getDataVencimento() { return dataVencimento; }
    public void setDataVencimento(String v) { this.dataVencimento = v; }
    public BigDecimal getSaldoInicialBruto() { return saldoInicialBruto; }
    public void setSaldoInicialBruto(BigDecimal v) { this.saldoInicialBruto = v; }
    public String getOrigemSaldoInicial() { return origemSaldoInicial; }
    public void setOrigemSaldoInicial(String v) { this.origemSaldoInicial = v; }
    public BigDecimal getAplicacoes() { return aplicacoes; }
    public void setAplicacoes(BigDecimal v) { this.aplicacoes = v; }
    public BigDecimal getResgatesBrutos() { return resgatesBrutos; }
    public void setResgatesBrutos(BigDecimal v) { this.resgatesBrutos = v; }
    public BigDecimal getRendimentoBruto() { return rendimentoBruto; }
    public void setRendimentoBruto(BigDecimal v) { this.rendimentoBruto = v; }
    public BigDecimal getIrRetido() { return irRetido; }
    public void setIrRetido(BigDecimal v) { this.irRetido = v; }
    public BigDecimal getIofRetido() { return iofRetido; }
    public void setIofRetido(BigDecimal v) { this.iofRetido = v; }
    public BigDecimal getSaldoFinalBruto() { return saldoFinalBruto; }
    public void setSaldoFinalBruto(BigDecimal v) { this.saldoFinalBruto = v; }
    public BigDecimal getIrProvisionado() { return irProvisionado; }
    public void setIrProvisionado(BigDecimal v) { this.irProvisionado = v; }
    public BigDecimal getIofProvisionado() { return iofProvisionado; }
    public void setIofProvisionado(BigDecimal v) { this.iofProvisionado = v; }
    public BigDecimal getSaldoFinalLiquido() { return saldoFinalLiquido; }
    public void setSaldoFinalLiquido(BigDecimal v) { this.saldoFinalLiquido = v; }
    public BigDecimal getDiferencaFechamento() { return diferencaFechamento; }
    public void setDiferencaFechamento(BigDecimal v) { this.diferencaFechamento = v; }
    public String getArquivo() { return arquivo; }
    public void setArquivo(String v) { this.arquivo = v; }
    public String getEmailId() { return emailId; }
    public void setEmailId(String v) { this.emailId = v; }
    public Instant getProcessadoEm() { return processadoEm; }
    public void setProcessadoEm(Instant v) { this.processadoEm = v; }
    public UUID getDocumentoId() { return documentoId; }
    public void setDocumentoId(UUID v) { this.documentoId = v; }
}
