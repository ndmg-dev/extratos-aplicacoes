package com.mendoncagalvao.extratos.claude;

import com.mendoncagalvao.extratos.validacao.model.ExtratoExtraido;

/**
 * Resultado do parsing da resposta do Claude.
 *
 * @param dados      dados estruturados do extrato, ou {@code null} se não houve resposta estruturada
 * @param stopReason {@code stop_reason} do Claude (ex.: {@code max_tokens})
 * @param rawJson    corpo JSON bruto da resposta (para auditoria — {@code raw_claude_json})
 */
public record ClaudeResult(ExtratoExtraido dados, String stopReason, String rawJson) {
}
