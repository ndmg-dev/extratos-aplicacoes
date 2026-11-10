package com.mendoncagalvao.extratos.web.dto;

import java.util.List;
import java.util.Map;

/** Resposta do upload: documentos processados, lançamentos e contagem por status. */
public record UploadRespostaDTO(
        List<DocumentoDTO> documentos,
        List<LancamentoDTO> lancamentos,
        Map<String, Long> contagemPorStatus
) {
}
