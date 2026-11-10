package com.mendoncagalvao.extratos.extracao;

import com.mendoncagalvao.extratos.domain.Lancamento;
import com.mendoncagalvao.extratos.validacao.model.LinhaLancamento;

import java.util.UUID;

/** Converte a linha validada ({@link LinhaLancamento}) na entidade {@link Lancamento}. */
public final class LancamentoMapper {

    private LancamentoMapper() {
    }

    public static Lancamento toEntity(LinhaLancamento l, UUID documentoId) {
        Lancamento e = new Lancamento();
        e.setChave(l.chave());
        e.setStatus(l.status());
        e.setMotivos(l.motivos());
        e.setCompetencia(l.competencia());
        e.setBanco(l.banco());
        e.setClienteNome(l.clienteNome());
        e.setClienteCnpj(l.clienteCnpj());
        e.setAgencia(l.agencia());
        e.setConta(l.conta());
        e.setIdentificador(l.identificador());
        e.setProduto(l.produto());
        e.setDataAplicacao(l.dataAplicacao());
        e.setDataVencimento(l.dataVencimento());
        e.setSaldoInicialBruto(l.saldoInicialBruto());
        e.setOrigemSaldoInicial(l.origemSaldoInicial());
        e.setAplicacoes(l.aplicacoes());
        e.setResgatesBrutos(l.resgatesBrutos());
        e.setRendimentoBruto(l.rendimentoBruto());
        e.setIrRetido(l.irRetido());
        e.setIofRetido(l.iofRetido());
        e.setSaldoFinalBruto(l.saldoFinalBruto());
        e.setIrProvisionado(l.irProvisionado());
        e.setIofProvisionado(l.iofProvisionado());
        e.setSaldoFinalLiquido(l.saldoFinalLiquido());
        e.setDiferencaFechamento(l.diferencaFechamento());
        e.setArquivo(l.arquivo());
        e.setEmailId(l.emailId());
        e.setProcessadoEm(l.processadoEm());
        e.setDocumentoId(documentoId);
        return e;
    }
}
