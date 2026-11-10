package com.mendoncagalvao.extratos.email.model;

import java.time.Instant;
import java.util.List;

/** Uma mensagem do Gmail já com os anexos PDF baixados. */
public record GmailMensagem(
        String id,
        String remetente,
        String assunto,
        Instant data,
        List<GmailAnexo> anexosPdf
) {
}
