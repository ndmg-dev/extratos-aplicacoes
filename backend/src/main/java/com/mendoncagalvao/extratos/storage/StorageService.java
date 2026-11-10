package com.mendoncagalvao.extratos.storage;

/**
 * Abstração de armazenamento de arquivos (§3). Implementações: {@link FilesystemStorage}
 * (default em dev) e, futuramente, S3/MinIO em produção.
 */
public interface StorageService {

    /**
     * Armazena o conteúdo na chave informada (sobrescreve se já existir).
     *
     * @return a mesma {@code storageKey}, por conveniência
     */
    String salvar(String storageKey, byte[] conteudo);

    /** Lê o conteúdo armazenado na chave. */
    byte[] carregar(String storageKey);

    /** Indica se existe algo na chave. */
    boolean existe(String storageKey);
}
