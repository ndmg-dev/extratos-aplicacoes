package com.mendoncagalvao.extratos.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Um PDF processado (§4.1). O {@code raw_claude_json} é guardado para auditoria e não é
 * exposto por padrão nos DTOs.
 */
@Entity
@Table(name = "documento")
public class Documento {

    /** Valores de {@code status_processamento}. */
    public static final String STATUS_PROCESSADO = "PROCESSADO";
    public static final String STATUS_ERRO = "ERRO";
    public static final String STATUS_PENDENTE = "PENDENTE";

    /** Valores de {@code origem} (§13). */
    public static final String ORIGEM_UPLOAD = "UPLOAD";
    public static final String ORIGEM_EMAIL = "EMAIL";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String nomeArquivoOriginal;
    private String nomePadronizado;
    private String banco;

    @Column(length = 7)
    private String competencia;

    @Column(length = 14)
    private String cnpj;

    private String conta;
    private String storageKey;
    private Long tamanhoBytes;

    @Column(length = 64)
    private String sha256;

    private String statusProcessamento;
    private String stopReason;

    @JsonIgnore
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String rawClaudeJson;

    private Boolean documentoReconhecido;

    @Column(precision = 18, scale = 2)
    private BigDecimal totalBrutoInformado;

    @Column(columnDefinition = "text")
    private String observacoes;

    private Instant criadoEm;
    private Instant processadoEm;

    // Proveniência (§13): de onde veio o documento.
    @Column(length = 10)
    private String origem;
    private String emailMessageId;
    private String emailRemetente;
    @Column(columnDefinition = "text")
    private String emailAssunto;
    private Instant emailData;

    public Documento() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getNomeArquivoOriginal() { return nomeArquivoOriginal; }
    public void setNomeArquivoOriginal(String v) { this.nomeArquivoOriginal = v; }
    public String getNomePadronizado() { return nomePadronizado; }
    public void setNomePadronizado(String v) { this.nomePadronizado = v; }
    public String getBanco() { return banco; }
    public void setBanco(String v) { this.banco = v; }
    public String getCompetencia() { return competencia; }
    public void setCompetencia(String v) { this.competencia = v; }
    public String getCnpj() { return cnpj; }
    public void setCnpj(String v) { this.cnpj = v; }
    public String getConta() { return conta; }
    public void setConta(String v) { this.conta = v; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String v) { this.storageKey = v; }
    public Long getTamanhoBytes() { return tamanhoBytes; }
    public void setTamanhoBytes(Long v) { this.tamanhoBytes = v; }
    public String getSha256() { return sha256; }
    public void setSha256(String v) { this.sha256 = v; }
    public String getStatusProcessamento() { return statusProcessamento; }
    public void setStatusProcessamento(String v) { this.statusProcessamento = v; }
    public String getStopReason() { return stopReason; }
    public void setStopReason(String v) { this.stopReason = v; }
    public String getRawClaudeJson() { return rawClaudeJson; }
    public void setRawClaudeJson(String v) { this.rawClaudeJson = v; }
    public Boolean getDocumentoReconhecido() { return documentoReconhecido; }
    public void setDocumentoReconhecido(Boolean v) { this.documentoReconhecido = v; }
    public BigDecimal getTotalBrutoInformado() { return totalBrutoInformado; }
    public void setTotalBrutoInformado(BigDecimal v) { this.totalBrutoInformado = v; }
    public String getObservacoes() { return observacoes; }
    public void setObservacoes(String v) { this.observacoes = v; }
    public Instant getCriadoEm() { return criadoEm; }
    public void setCriadoEm(Instant v) { this.criadoEm = v; }
    public Instant getProcessadoEm() { return processadoEm; }
    public void setProcessadoEm(Instant v) { this.processadoEm = v; }
    public String getOrigem() { return origem; }
    public void setOrigem(String v) { this.origem = v; }
    public String getEmailMessageId() { return emailMessageId; }
    public void setEmailMessageId(String v) { this.emailMessageId = v; }
    public String getEmailRemetente() { return emailRemetente; }
    public void setEmailRemetente(String v) { this.emailRemetente = v; }
    public String getEmailAssunto() { return emailAssunto; }
    public void setEmailAssunto(String v) { this.emailAssunto = v; }
    public Instant getEmailData() { return emailData; }
    public void setEmailData(Instant v) { this.emailData = v; }
}
