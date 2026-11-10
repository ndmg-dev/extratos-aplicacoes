package com.mendoncagalvao.extratos.extracao;

import java.time.Instant;

/**
 * Origem de um arquivo processado. Permite que o mesmo {@link ExtracaoService} seja reusado
 * tanto pelo upload web quanto pela ingestão por e-mail (§13), registrando a procedência no
 * documento.
 *
 * @param origem          {@code UPLOAD} ou {@code EMAIL}
 * @param emailMessageId  id da mensagem no provedor (Gmail), ou {@code null} no upload
 * @param emailRemetente  remetente do e-mail, ou {@code null}
 * @param emailAssunto    assunto do e-mail, ou {@code null}
 * @param emailData       data/hora da mensagem, ou {@code null}
 */
public record Proveniencia(
        String origem,
        String emailMessageId,
        String emailRemetente,
        String emailAssunto,
        Instant emailData
) {
    public static final String ORIGEM_UPLOAD = "UPLOAD";
    public static final String ORIGEM_EMAIL = "EMAIL";

    /** Proveniência de upload manual pela interface web. */
    public static Proveniencia upload() {
        return new Proveniencia(ORIGEM_UPLOAD, null, null, null, null);
    }

    /** Proveniência de ingestão por e-mail. */
    public static Proveniencia email(String messageId, String remetente, String assunto, Instant data) {
        return new Proveniencia(ORIGEM_EMAIL, messageId, remetente, assunto, data);
    }

    /** Id de e-mail para o campo {@code email_id} do lançamento (vazio quando não houver). */
    public String emailIdOuVazio() {
        return emailMessageId == null ? "" : emailMessageId;
    }
}
