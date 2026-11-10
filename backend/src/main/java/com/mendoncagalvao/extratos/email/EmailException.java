package com.mendoncagalvao.extratos.email;

/** Falha na integração com o provedor de e-mail (§13). */
public class EmailException extends RuntimeException {

    public EmailException(String message) {
        super(message);
    }

    public EmailException(String message, Throwable cause) {
        super(message, cause);
    }
}
