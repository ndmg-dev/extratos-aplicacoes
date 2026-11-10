package com.mendoncagalvao.extratos.web;

import com.mendoncagalvao.extratos.domain.Documento;
import com.mendoncagalvao.extratos.domain.Lancamento;
import com.mendoncagalvao.extratos.web.dto.DocumentoDTO;
import com.mendoncagalvao.extratos.web.dto.LancamentoDTO;

/** Conversão entidade → DTO. */
public final class DtoMapper {

    private DtoMapper() {
    }

    public static LancamentoDTO toDto(Lancamento l) {
        return new LancamentoDTO(
                l.getId(), l.getChave(), l.getStatus(), l.getMotivos(), l.getCompetencia(),
                l.getBanco(), l.getClienteNome(), l.getClienteCnpj(), l.getAgencia(), l.getConta(),
                l.getIdentificador(), l.getProduto(), l.getDataAplicacao(), l.getDataVencimento(),
                l.getSaldoInicialBruto(), l.getOrigemSaldoInicial(), l.getAplicacoes(),
                l.getResgatesBrutos(), l.getRendimentoBruto(), l.getIrRetido(), l.getIofRetido(),
                l.getSaldoFinalBruto(), l.getIrProvisionado(), l.getIofProvisionado(),
                l.getSaldoFinalLiquido(), l.getDiferencaFechamento(), l.getArquivo(), l.getEmailId(),
                l.getProcessadoEm(), l.getDocumentoId());
    }

    public static DocumentoDTO toDto(Documento d, boolean incluirRaw) {
        return new DocumentoDTO(
                d.getId(), d.getNomeArquivoOriginal(), d.getNomePadronizado(), d.getBanco(),
                d.getCompetencia(), d.getCnpj(), d.getConta(), d.getStorageKey(), d.getTamanhoBytes(),
                d.getSha256(), d.getStatusProcessamento(), d.getStopReason(), d.getDocumentoReconhecido(),
                d.getTotalBrutoInformado(), d.getObservacoes(), d.getCriadoEm(), d.getProcessadoEm(),
                d.getOrigem(), d.getEmailRemetente(), d.getEmailAssunto(), d.getEmailData(),
                incluirRaw ? d.getRawClaudeJson() : null);
    }
}
