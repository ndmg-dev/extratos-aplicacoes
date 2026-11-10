package com.mendoncagalvao.extratos.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Registro de uma mensagem de e-mail já processada, para evitar reprocessamento a cada ciclo
 * de polling (§13). O id é o id da mensagem no provedor (Gmail).
 */
@Entity
@Table(name = "email_mensagem_processada")
public class EmailMensagemProcessada {

    @Id
    private String messageId;

    private Instant processadoEm;
    private String remetente;

    @Column(columnDefinition = "text")
    private String assunto;

    private int qtdPdf;
    private String status;

    public EmailMensagemProcessada() {
    }

    public EmailMensagemProcessada(String messageId, Instant processadoEm, String remetente,
                                   String assunto, int qtdPdf, String status) {
        this.messageId = messageId;
        this.processadoEm = processadoEm;
        this.remetente = remetente;
        this.assunto = assunto;
        this.qtdPdf = qtdPdf;
        this.status = status;
    }

    public String getMessageId() { return messageId; }
    public void setMessageId(String v) { this.messageId = v; }
    public Instant getProcessadoEm() { return processadoEm; }
    public void setProcessadoEm(Instant v) { this.processadoEm = v; }
    public String getRemetente() { return remetente; }
    public void setRemetente(String v) { this.remetente = v; }
    public String getAssunto() { return assunto; }
    public void setAssunto(String v) { this.assunto = v; }
    public int getQtdPdf() { return qtdPdf; }
    public void setQtdPdf(int v) { this.qtdPdf = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
}
