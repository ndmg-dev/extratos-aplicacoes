package com.mendoncagalvao.extratos.validacao.model;

/**
 * Um item da chamada ao Claude, já com os dados estruturados extraídos (ou {@code null}
 * quando não houve resposta estruturada), mais o {@code stop_reason} e os metadados
 * ({@code arquivo}, {@code email_id}).
 *
 * <p>Equivale a {@code respostas[i]} + {@code metas[i]} do código original (Apêndice C).
 * O parsing de {@code tool_use}/fallback para JSON é feito a montante (ClaudeClient),
 * mantendo o {@code ValidacaoService} puro.
 *
 * @param dados      dados estruturados do extrato, ou {@code null} se ausentes
 * @param stopReason {@code stop_reason} do Claude (ex.: {@code max_tokens})
 * @param arquivo    nome do arquivo de origem
 * @param emailId    id do e-mail de origem (vazio na web; mantido por compatibilidade)
 */
public record ItemResposta(
        ExtratoExtraido dados,
        String stopReason,
        String arquivo,
        String emailId
) {
    public ItemResposta {
        if (arquivo == null) {
            arquivo = "";
        }
        if (emailId == null) {
            emailId = "";
        }
    }
}
