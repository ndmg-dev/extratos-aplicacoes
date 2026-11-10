package com.mendoncagalvao.extratos.storage;

/**
 * Resultado da nomeação/arquivamento de um PDF.
 *
 * @param nomePadronizado nome do arquivo ({@code BANCO_AAAA-MM_CONTA.pdf} ou o nome original)
 * @param storageKey      chave/caminho no storage ({@code cnpj|_SEM_CNPJ/ano/mes/aplicacoes/nome})
 * @param semCnpj         {@code true} quando o CNPJ não foi identificado (arquivo em {@code _SEM_CNPJ})
 */
public record ResultadoNomeacao(String nomePadronizado, String storageKey, boolean semCnpj) {
}
