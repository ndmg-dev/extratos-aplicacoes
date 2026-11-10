package com.mendoncagalvao.extratos.email.model;

/** Um anexo PDF de uma mensagem de e-mail. */
public record GmailAnexo(String nomeArquivo, byte[] conteudo) {
}
