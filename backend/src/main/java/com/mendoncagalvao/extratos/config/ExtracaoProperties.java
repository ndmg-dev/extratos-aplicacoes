package com.mendoncagalvao.extratos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuração do fluxo de extração (§5).
 *
 * @param throttleMs intervalo entre PDFs no processamento em série (o workflow usa 1500 ms);
 *                   0 desliga o throttle.
 * @param zona       zona usada para o fallback de ano/mês na nomeação quando não há competência.
 */
@ConfigurationProperties(prefix = "extracao")
public record ExtracaoProperties(
        @DefaultValue("0") long throttleMs,
        @DefaultValue("America/Sao_Paulo") String zona
) {
}
