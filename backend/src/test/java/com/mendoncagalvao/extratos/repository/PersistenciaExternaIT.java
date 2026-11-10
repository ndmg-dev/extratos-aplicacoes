package com.mendoncagalvao.extratos.repository;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Mesma bateria de {@link PersistenciaTestesBase}, porém contra um Postgres <b>externo</b>
 * informado por variáveis de ambiente ({@code IT_DB_URL}, {@code IT_DB_USER}, {@code IT_DB_PASSWORD}).
 *
 * <p>Útil onde o Testcontainers não inicializa (ex.: Docker Desktop muito recente). Fica
 * desabilitado quando {@code IT_DB_URL} não está definido — portanto não afeta o build padrão.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "IT_DB_URL", matches = ".+")
class PersistenciaExternaIT extends PersistenciaTestesBase {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("IT_DB_URL"));
        r.add("spring.datasource.username", () -> System.getenv().getOrDefault("IT_DB_USER", "extratos"));
        r.add("spring.datasource.password", () -> System.getenv().getOrDefault("IT_DB_PASSWORD", "extratos"));
    }
}
