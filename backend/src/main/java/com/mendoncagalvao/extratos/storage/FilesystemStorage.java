package com.mendoncagalvao.extratos.storage;

import com.mendoncagalvao.extratos.config.StorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Armazenamento em filesystem (default em dev). A chave é relativa ao {@code basePath};
 * caminhos que tentem escapar do diretório base são rejeitados (§11 — sanitização).
 */
@Component
@ConditionalOnProperty(prefix = "storage", name = "type", havingValue = "fs", matchIfMissing = true)
public class FilesystemStorage implements StorageService {

    private final Path base;

    public FilesystemStorage(StorageProperties props) {
        this.base = Paths.get(props.basePath()).toAbsolutePath().normalize();
    }

    @Override
    public String salvar(String storageKey, byte[] conteudo) {
        Path destino = resolver(storageKey);
        try {
            Files.createDirectories(destino.getParent());
            Files.write(destino, conteudo);
            return storageKey;
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao salvar arquivo em " + storageKey, e);
        }
    }

    @Override
    public byte[] carregar(String storageKey) {
        try {
            return Files.readAllBytes(resolver(storageKey));
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler arquivo em " + storageKey, e);
        }
    }

    @Override
    public boolean existe(String storageKey) {
        return Files.exists(resolver(storageKey));
    }

    /** Resolve a chave sob o diretório base e impede path traversal. */
    private Path resolver(String storageKey) {
        Path destino = base.resolve(storageKey).normalize();
        if (!destino.startsWith(base)) {
            throw new IllegalArgumentException("storageKey inválida (fora do diretório base): " + storageKey);
        }
        return destino;
    }
}
