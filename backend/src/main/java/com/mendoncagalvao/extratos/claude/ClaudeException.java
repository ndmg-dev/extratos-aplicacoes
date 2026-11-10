package com.mendoncagalvao.extratos.claude;

/**
 * Falha na comunicação com a API do Claude após esgotar as tentativas. O item correspondente
 * deve ser marcado como {@code ERRO} sem derrubar o lote (§12).
 */
public class ClaudeException extends RuntimeException {

    public ClaudeException(String message, Throwable cause) {
        super(message, cause);
    }

    public ClaudeException(String message) {
        super(message);
    }
}
