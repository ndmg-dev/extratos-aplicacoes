package com.mendoncagalvao.extratos.repository;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integração de persistência com Postgres real via Testcontainers + Flyway (§12).
 *
 * <p>{@code disabledWithoutDocker = true}: quando o Testcontainers não consegue inicializar o
 * ambiente Docker, os testes são <b>pulados</b> (não falham). Em CI/ambiente Docker padrão eles
 * rodam normalmente. (No ambiente de desenvolvimento atual, o Docker Desktop muito recente é
 * incompatível com o docker-java embutido; use {@code PersistenciaExternaIT} para verificação local.)
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class PersistenciaIT extends PersistenciaTestesBase {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }
}
