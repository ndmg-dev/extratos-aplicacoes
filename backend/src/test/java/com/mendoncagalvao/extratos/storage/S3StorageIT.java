package com.mendoncagalvao.extratos.storage;

import com.mendoncagalvao.extratos.config.StorageProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integração do {@link S3Storage} contra um MinIO real (cria bucket, salva, lê, verifica).
 * Habilitado só quando {@code IT_S3_ENDPOINT} está definido — não afeta o build padrão.
 */
@EnabledIfEnvironmentVariable(named = "IT_S3_ENDPOINT", matches = ".+")
class S3StorageIT {

    private S3Storage storage() {
        String bucket = "it-" + UUID.randomUUID().toString().substring(0, 8);
        return new S3Storage(new StorageProperties(
                "s3", null, bucket, System.getenv("IT_S3_ENDPOINT"),
                "us-east-1",
                System.getenv().getOrDefault("IT_S3_ACCESS_KEY", "minioadmin"),
                System.getenv().getOrDefault("IT_S3_SECRET_KEY", "minioadmin"),
                true));
    }

    @Test
    @DisplayName("Cria bucket, salva, verifica e lê de volta")
    void roundtrip() {
        S3Storage s = storage();
        String key = "12345678000195/2026/09/aplicacoes/CAIXA_2026-09_1.pdf";
        byte[] conteudo = "%PDF conteúdo".getBytes(StandardCharsets.UTF_8);

        assertThat(s.existe(key)).isFalse();
        assertThat(s.salvar(key, conteudo)).isEqualTo(key);
        assertThat(s.existe(key)).isTrue();
        assertThat(s.carregar(key)).isEqualTo(conteudo);
    }
}
