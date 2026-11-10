package com.mendoncagalvao.extratos.validacao;

import com.mendoncagalvao.extratos.validacao.model.Aplicacao;
import com.mendoncagalvao.extratos.validacao.model.ExtratoExtraido;
import com.mendoncagalvao.extratos.validacao.model.ItemResposta;
import com.mendoncagalvao.extratos.validacao.model.LinhaLancamento;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Converte a resposta do Claude em linhas de lançamento e valida o fechamento de saldo.
 *
 * <p>Porte <b>verbatim</b> do nó "Normalizar e validar" do workflow n8n (Apêndice C da spec).
 * O comportamento (regras, ordem dos motivos, tolerância, status) <b>não pode</b> divergir do
 * original. A única diferença é a substituição de aritmética de ponto flutuante do JS por
 * {@link BigDecimal} (invariante de dinheiro da spec), com arredondamento {@code HALF_UP}
 * replicando {@code r2 = Math.round(v*100)/100}.
 *
 * <p>Serviço <b>puro</b> (sem I/O): o parsing de {@code tool_use}/fallback e o acesso a storage
 * ficam a montante (ClaudeClient / ExtracaoService).
 */
@Service
public class ValidacaoService {

    /** Tolerância de fechamento de saldo, em reais. Não alterar. */
    public static final BigDecimal TOLERANCIA = new BigDecimal("0.05");

    private final Clock clock;

    public ValidacaoService() {
        this(Clock.systemUTC());
    }

    /** Construtor com {@link Clock} injetável para testes determinísticos. */
    public ValidacaoService(Clock clock) {
        this.clock = clock;
    }

    // ---- helpers (equivalentes a r2 / digitos do JS) ------------------------------------

    /** {@code r2 = v == null ? null : Math.round(v*100)/100} — 2 casas, HALF_UP. */
    static BigDecimal r2(BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP);
    }

    /** Equivalente a {@code v ?? 0}. */
    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** {@code digitos = s == null ? '' : String(s).replace(/\D/g, '')}. */
    static String digitos(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }

    /** Formata um número como o {@code String(Number)} do JS faria (sem zeros à direita). */
    private static String jsNum(BigDecimal v) {
        return v == null ? "null" : v.stripTrailingZeros().toPlainString();
    }

    // ---- API ----------------------------------------------------------------------------

    /**
     * Normaliza e valida todos os itens de uma chamada ao Claude, retornando as linhas de
     * lançamento na mesma ordem em que o JS original as empilharia.
     */
    public List<LinhaLancamento> normalizarEValidar(List<ItemResposta> itens) {
        List<LinhaLancamento> linhas = new ArrayList<>();
        for (ItemResposta item : itens) {
            processarItem(item, linhas);
        }
        return linhas;
    }

    /** Conveniência: normaliza e valida um único item. */
    public List<LinhaLancamento> normalizarEValidar(ItemResposta item) {
        return normalizarEValidar(List.of(item));
    }

    // ---- porte do forEach do Apêndice C -------------------------------------------------

    private void processarItem(ItemResposta item, List<LinhaLancamento> linhas) {
        final String arquivo = item.arquivo();
        final String emailId = item.emailId();
        final Instant processadoEm = Instant.now(clock);

        final ExtratoExtraido d = item.dados();

        // Plano B (parsing tool_use/fallback) é feito a montante: dados == null => sem resposta estruturada.
        if (d == null) {
            linhas.add(linhaErro(
                    "ERRO|" + emailId + "|" + arquivo,
                    LinhaLancamento.STATUS_ERRO,
                    "Sem resposta estruturada",
                    null, null,
                    arquivo, emailId, processadoEm));
            return;
        }

        final List<Aplicacao> aps = d.aplicacoes == null ? List.of() : d.aplicacoes;
        final List<String> motivosDoc = new ArrayList<>();

        if (digitos(d.clienteCnpj).length() != 14) {
            motivosDoc.add("CNPJ não identificado no extrato (arquivo salvo em _SEM_CNPJ)");
        }
        if (Boolean.FALSE.equals(d.documentoReconhecido)) {
            motivosDoc.add("Documento não reconhecido como extrato de aplicação");
        }
        if ("max_tokens".equals(item.stopReason())) {
            motivosDoc.add("Resposta cortada (max_tokens)");
        }
        if (aps.isEmpty()) {
            motivosDoc.add("Nenhuma aplicação encontrada");
        }

        BigDecimal soma = BigDecimal.ZERO;
        for (Aplicacao a : aps) {
            soma = soma.add(nz(a.saldoFinalBruto));
        }
        if (d.totalBrutoInformado != null
                && soma.subtract(d.totalBrutoInformado).abs().compareTo(TOLERANCIA) > 0) {
            motivosDoc.add("Soma das aplicações (" + jsNum(r2(soma))
                    + ") difere do total do extrato (" + jsNum(d.totalBrutoInformado) + ")");
        }
        if (d.observacoes != null && !d.observacoes.isEmpty()) {
            motivosDoc.add("Obs. do extrator: " + d.observacoes);
        }

        if (aps.isEmpty()) {
            linhas.add(linhaErro(
                    "ERRO|" + emailId + "|" + arquivo,
                    LinhaLancamento.STATUS_REVISAR,
                    String.join(" | ", motivosDoc),
                    d.banco, d.competencia,
                    arquivo, emailId, processadoEm));
            return;
        }

        for (Aplicacao a : aps) {
            final List<String> motivos = new ArrayList<>(motivosDoc);
            final BigDecimal apl = nz(a.aplicacoes);
            final BigDecimal res = nz(a.resgatesBrutos);
            final BigDecimal rend = a.rendimentoBruto;
            final BigDecimal fin = a.saldoFinalBruto;
            BigDecimal ini = a.saldoInicialBruto;
            String origemIni = LinhaLancamento.ORIGEM_EXTRATO;
            BigDecimal diferenca = null;

            if (ini == null && fin != null && rend != null) {
                ini = fin.subtract(apl).subtract(rend).add(res);
                origemIni = LinhaLancamento.ORIGEM_CALCULADO;
            }

            final boolean detalhadoFalse = Boolean.FALSE.equals(a.detalhado);
            if (!detalhadoFalse) {
                if (ini == null || rend == null || fin == null) {
                    motivos.add("Campos obrigatórios ausentes");
                } else if (LinhaLancamento.ORIGEM_EXTRATO.equals(origemIni)) {
                    diferenca = fin.subtract(ini.add(apl).add(rend).subtract(res));
                    if (diferenca.abs().compareTo(TOLERANCIA) > 0) {
                        motivos.add("Saldo não fecha (diferença " + jsNum(r2(diferenca)) + ")");
                    }
                }
            }

            final boolean temProblema = motivos.stream().anyMatch(m -> !m.startsWith("Obs. do extrator"));
            final String status = detalhadoFalse
                    ? LinhaLancamento.STATUS_SEM_DETALHE
                    : (temProblema ? LinhaLancamento.STATUS_REVISAR : LinhaLancamento.STATUS_OK);

            final String chave = String.join("|",
                    digitos(d.clienteCnpj),
                    nvl(d.banco),
                    digitos(d.conta),
                    nvl(a.identificador),
                    nvl(d.competencia));

            linhas.add(new LinhaLancamento(
                    chave,
                    status,
                    String.join(" | ", motivos),
                    d.competencia,
                    d.banco,
                    d.clienteNome,
                    digitos(d.clienteCnpj),
                    d.agencia,
                    d.conta,
                    a.identificador,
                    a.produto,
                    a.dataAplicacao,
                    a.dataVencimento,
                    r2(ini),
                    origemIni,
                    r2(apl),
                    r2(res),
                    r2(rend),
                    r2(nz(a.irRetido)),
                    r2(nz(a.iofRetido)),
                    r2(fin),
                    r2(a.irProvisionado),
                    r2(a.iofProvisionado),
                    r2(a.saldoFinalLiquido),
                    r2(diferenca),
                    arquivo,
                    emailId,
                    processadoEm));
        }
    }

    /** {@code Array.join} converte null/undefined em string vazia. */
    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static LinhaLancamento linhaErro(String chave, String status, String motivos,
                                             String banco, String competencia,
                                             String arquivo, String emailId, Instant processadoEm) {
        return new LinhaLancamento(
                chave, status, motivos,
                competencia, banco,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                arquivo, emailId, processadoEm);
    }
}
