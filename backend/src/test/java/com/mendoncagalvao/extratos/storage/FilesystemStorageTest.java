package com.mendoncagalvao.extratos.storage;

import com.mendoncagalvao.extratos.config.StorageProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FilesystemStorageTest {

    private FilesystemStorage storage(Path base) {
        return new FilesystemStorage(
                new StorageProperties("fs", base.toString(), null, null, "us-east-1", null, null, true));
    }

    @Test
    @DisplayName("Salvar cria diretórios aninhados e carregar/existe funcionam")
    void roundtrip(@TempDir Path base) {
        FilesystemStorage s = storage(base);
        String key = "12345678000195/2026/09/aplicacoes/CAIXA_2026-09_1.pdf";
        byte[] conteudo = "%PDF conteúdo".getBytes(StandardCharsets.UTF_8);

        assertThat(s.existe(key)).isFalse();
        assertThat(s.salvar(key, conteudo)).isEqualTo(key);
        assertThat(s.existe(key)).isTrue();
        assertThat(s.carregar(key)).isEqualTo(conteudo);
        assertThat(Files.exists(base.resolve(key))).isTrue();
    }

    @Test
    @DisplayName("Salvar sobrescreve conteúdo existente")
    void sobrescreve(@TempDir Path base) {
        FilesystemStorage s = storage(base);
        String key = "a/b/c.pdf";
        s.salvar(key, "v1".getBytes(StandardCharsets.UTF_8));
        s.salvar(key, "v2".getBytes(StandardCharsets.UTF_8));
        assertThat(new String(s.carregar(key), StandardCharsets.UTF_8)).isEqualTo("v2");
    }

    @Test
    @DisplayName("Chave com path traversal é rejeitada")
    void traversalRejeitado(@TempDir Path base) {
        FilesystemStorage s = storage(base);
        assertThatThrownBy(() -> s.salvar("../fora.pdf", new byte[]{1}))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
