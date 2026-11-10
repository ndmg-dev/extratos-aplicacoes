package com.mendoncagalvao.extratos;

import com.mendoncagalvao.extratos.extracao.ExtracaoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sobe o contexto completo da aplicação contra um Postgres externo ({@code IT_DB_URL}),
 * verificando que todo o grafo de beans (controllers, serviços, repositórios, Flyway) conecta.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "IT_DB_URL", matches = ".+")
class ContextoIT {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("IT_DB_URL"));
        r.add("spring.datasource.username", () -> System.getenv().getOrDefault("IT_DB_USER", "extratos"));
        r.add("spring.datasource.password", () -> System.getenv().getOrDefault("IT_DB_PASSWORD", "extratos"));
    }

    @Autowired
    ExtracaoService extracaoService;

    @Test
    void contextoSobe() {
        assertThat(extracaoService).isNotNull();
    }
}
