package com.mendoncagalvao.extratos.web;

import com.mendoncagalvao.extratos.support.RecursoNaoEncontradoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.time.Instant;
import java.util.Map;

/** Tratamento central de erros da API, com mensagens claras (§7). */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<Map<String, Object>> naoEncontrado(RecursoNaoEncontradoException e) {
        return corpo(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            MissingServletRequestPartException.class,
            MissingServletRequestParameterException.class,
            MultipartException.class
    })
    public ResponseEntity<Map<String, Object>> requisicaoInvalida(Exception e) {
        return corpo(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> uploadGrande(MaxUploadSizeExceededException e) {
        return corpo(HttpStatus.BAD_REQUEST, "Arquivo excede o tamanho máximo permitido.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> erroInterno(Exception e) {
        log.error("Erro inesperado", e);
        return corpo(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno ao processar a requisição.");
    }

    private static ResponseEntity<Map<String, Object>> corpo(HttpStatus status, String mensagem) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "erro", status.getReasonPhrase(),
                "mensagem", mensagem == null ? "" : mensagem));
    }
}
