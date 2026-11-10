package com.mendoncagalvao.extratos.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Token OAuth de um provedor de e-mail (§13). O {@code refresh_token} é guardado para renovar
 * o {@code access_token} automaticamente no polling. Nunca é exposto em DTOs/respostas.
 */
@Entity
@Table(name = "oauth_token")
public class OAuthToken {

    @Id
    @Column(length = 30)
    private String provedor;

    private String emailConta;

    @JsonIgnore
    @Column(columnDefinition = "text")
    private String refreshToken;

    @JsonIgnore
    @Column(columnDefinition = "text")
    private String accessToken;

    private Instant expiraEm;
    private String escopo;
    private Instant atualizadoEm;

    public OAuthToken() {
    }

    public String getProvedor() { return provedor; }
    public void setProvedor(String v) { this.provedor = v; }
    public String getEmailConta() { return emailConta; }
    public void setEmailConta(String v) { this.emailConta = v; }
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String v) { this.refreshToken = v; }
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String v) { this.accessToken = v; }
    public Instant getExpiraEm() { return expiraEm; }
    public void setExpiraEm(Instant v) { this.expiraEm = v; }
    public String getEscopo() { return escopo; }
    public void setEscopo(String v) { this.escopo = v; }
    public Instant getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(Instant v) { this.atualizadoEm = v; }
}
