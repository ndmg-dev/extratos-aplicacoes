package com.mendoncagalvao.extratos.web.dto;

import java.util.List;

/** Metadados do documento + seus lançamentos (§7, GET /api/documentos/{id}). */
public record DocumentoDetalheDTO(DocumentoDTO documento, List<LancamentoDTO> lancamentos) {
}
