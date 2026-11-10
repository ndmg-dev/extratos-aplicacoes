package com.mendoncagalvao.extratos.validacao.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Dados estruturados extraídos pelo Claude via a tool {@code registrar_extrato} (Apêndice B).
 * Corresponde ao objeto {@code d}/{@code dados} da lógica original (Apêndice C).
 */
public class ExtratoExtraido {

    public Boolean documentoReconhecido;
    public String banco;
    public String clienteNome;
    public String clienteCnpj;
    public String agencia;
    public String conta;
    public String competencia;
    public String dataPosicao;
    public BigDecimal totalBrutoInformado;
    public String observacoes;
    public List<Aplicacao> aplicacoes = new ArrayList<>();

    public ExtratoExtraido() {
    }
}
