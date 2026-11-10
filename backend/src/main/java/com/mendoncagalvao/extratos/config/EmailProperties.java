package com.mendoncagalvao.extratos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Configuração da ingestão por e-mail (§13). Provedor padrão: Gmail (Google Workspace) via
 * OAuth2 com refresh token. As credenciais ({@code clientId}/{@code clientSecret}) vêm de
 * variáveis de ambiente e <b>nunca</b> são expostas ao frontend.
 *
 * @param enabled         liga/desliga a ingestão (default {@code false})
 * @param provider        provedor (default {@code gmail})
 * @param clientId        OAuth Client ID
 * @param clientSecret    OAuth Client Secret
 * @param redirectUri     redirect URI registrado no cliente OAuth
 * @param scope           escopo OAuth (default {@code gmail.readonly})
 * @param subjectKeywords palavras-chave do assunto (filtro §13)
 * @param lookbackDays    janela de retrospecto na busca (default 30 dias)
 * @param pollIntervalMs  intervalo do polling (default 300000 = 5 min)
 * @param query           sobrescreve a query do Gmail (opcional)
 * @param maxPorCiclo     máximo de mensagens por ciclo (default 50)
 * @param timeoutMs       timeout das chamadas HTTP (default 20000)
 */
@ConfigurationProperties(prefix = "email")
public record EmailProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("gmail") String provider,
        String clientId,
        String clientSecret,
        @DefaultValue("http://localhost:8080/api/email/oauth/callback") String redirectUri,
        @DefaultValue("https://www.googleapis.com/auth/gmail.readonly") String scope,
        @DefaultValue("extrato,aplicação,aplicacao,posição,posicao,investimento,rendimento")
        List<String> subjectKeywords,
        @DefaultValue("30") int lookbackDays,
        @DefaultValue("300000") long pollIntervalMs,
        String query,
        @DefaultValue("50") int maxPorCiclo,
        @DefaultValue("20000") long timeoutMs
) {

    /** Credenciais OAuth presentes (necessárias para qualquer operação). */
    public boolean temCredenciais() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }
}
