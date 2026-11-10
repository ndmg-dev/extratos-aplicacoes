package com.mendoncagalvao.extratos.support;

/** Recurso inexistente — mapeado para HTTP 404 pelo tratador de exceções. */
public class RecursoNaoEncontradoException extends RuntimeException {
    public RecursoNaoEncontradoException(String message) {
        super(message);
    }
}
