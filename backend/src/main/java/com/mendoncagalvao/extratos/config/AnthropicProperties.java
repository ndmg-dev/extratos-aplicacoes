package com.mendoncagalvao.extratos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuração da integração com o Claude (§6/§11 da spec). A chave da API vem de
 * variável de ambiente e <b>nunca</b> é exposta ao frontend.
 *
 * @param apiKey        {@code ANTHROPIC_API_KEY} (obrigatória)
 * @param model         {@code ANTHROPIC_MODEL} (default {@code claude-sonnet-5-5}, o mesmo do workflow)
 * @param baseUrl       base da API (default {@code https://api.anthropic.com})
 * @param maxTokens     {@code ANTHROPIC_MAX_TOKENS} (default 4096)
 * @param timeoutMs     {@code ANTHROPIC_TIMEOUT_MS} (default 180000)
 * @param maxTentativas número de tentativas (default 3, igual ao workflow)
 * @param backoffMs     espera entre tentativas (default 5000)
 * @param versao        header {@code anthropic-version} (default {@code 2023-06-01})
 */
@ConfigurationProperties(prefix = "anthropic")
public record AnthropicProperties(
        String apiKey,
        @DefaultValue("claude-sonnet-5-5") String model,
        @DefaultValue("https://api.anthropic.com") String baseUrl,
        @DefaultValue("4096") int maxTokens,
        @DefaultValue("180000") long timeoutMs,
        @DefaultValue("3") int maxTentativas,
        @DefaultValue("5000") long backoffMs,
        @DefaultValue("2023-06-01") String versao
) {
}
