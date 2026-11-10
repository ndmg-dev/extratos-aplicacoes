package com.mendoncagalvao.extratos.storage;

import com.mendoncagalvao.extratos.validacao.model.ExtratoExtraido;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class NomeadorArquivoTest {

    private final NomeadorArquivo nomeador = new NomeadorArquivo();
    private static final LocalDate UPLOAD = LocalDate.of(2026, 3, 15);

    private static ExtratoExtraido extrato(String banco, String cnpj, String conta, String competencia) {
        ExtratoExtraido d = new ExtratoExtraido();
        d.banco = banco;
        d.clienteCnpj = cnpj;
        d.conta = conta;
        d.competencia = competencia;
        return d;
    }

    @Test
    @DisplayName("Nome e chave padrão com CNPJ, competência e conta")
    void nomeCompleto() {
        ExtratoExtraido d = extrato("CAIXA", "12.345.678/0001-95", "0001-2345", "2026-09");
        ResultadoNomeacao r = nomeador.nomear(d, "upload.pdf", UPLOAD);

        assertThat(r.nomePadronizado()).isEqualTo("CAIXA_2026-09_00012345.pdf");
        assertThat(r.storageKey()).isEqualTo("12345678000195/2026/09/aplicacoes/CAIXA_2026-09_00012345.pdf");
        assertThat(r.semCnpj()).isFalse();
    }

    @Test
    @DisplayName("Banco com acentos e símbolos vira MAIÚSCULAS sem acento, não-alfanumérico -> _")
    void bancoNormalizado() {
        ExtratoExtraido d = extrato("Banco Içá & Cia", "12345678000195", "99", "2026-01");
        ResultadoNomeacao r = nomeador.nomear(d, "x.pdf", UPLOAD);
        assertThat(r.nomePadronizado()).isEqualTo("BANCO_ICA_CIA_2026-01_99.pdf");
    }

    @Test
    @DisplayName("Conta vazia é omitida do nome")
    void contaVazia() {
        ExtratoExtraido d = extrato("SICOOB", "12345678000195", null, "2026-07");
        ResultadoNomeacao r = nomeador.nomear(d, "x.pdf", UPLOAD);
        assertThat(r.nomePadronizado()).isEqualTo("SICOOB_2026-07.pdf");
    }

    @Test
    @DisplayName("Sem CNPJ -> prefixo _SEM_CNPJ e flag semCnpj")
    void semCnpj() {
        ExtratoExtraido d = extrato("SANTANDER", null, "13000", "2026-08");
        ResultadoNomeacao r = nomeador.nomear(d, "x.pdf", UPLOAD);
        assertThat(r.semCnpj()).isTrue();
        assertThat(r.storageKey()).isEqualTo("_SEM_CNPJ/2026/08/aplicacoes/SANTANDER_2026-08_13000.pdf");
    }

    @Test
    @DisplayName("Sem competência -> usa ano/mês do upload (mês sem zero à esquerda) e nome original")
    void semCompetencia() {
        ExtratoExtraido d = extrato("CAIXA", "12345678000195", "1", null);
        ResultadoNomeacao r = nomeador.nomear(d, "extrato original.pdf", UPLOAD);

        // mês sem zero à esquerda, como String(dt.getMonth()+1) do JS
        assertThat(r.storageKey()).isEqualTo("12345678000195/2026/3/aplicacoes/extrato_original.pdf");
        assertThat(r.nomePadronizado()).isEqualTo("extrato_original.pdf");
    }

    @Test
    @DisplayName("Dados nulos (leitura falhou) -> nome original saneado em _SEM_CNPJ")
    void dadosNulos() {
        ResultadoNomeacao r = nomeador.nomear(null, "meu extrato.pdf", UPLOAD);
        assertThat(r.semCnpj()).isTrue();
        assertThat(r.nomePadronizado()).isEqualTo("meu_extrato.pdf");
        assertThat(r.storageKey()).isEqualTo("_SEM_CNPJ/2026/3/aplicacoes/meu_extrato.pdf");
    }

    @Test
    @DisplayName("Nome original com path traversal é reduzido ao basename seguro")
    void pathTraversal() {
        ResultadoNomeacao r = nomeador.nomear(null, "../../etc/passwd", UPLOAD);
        assertThat(r.nomePadronizado()).isEqualTo("passwd");
        assertThat(r.storageKey()).doesNotContain("..");
    }

    @Test
    @DisplayName("Nome original ausente -> documento.pdf")
    void nomeOriginalAusente() {
        ResultadoNomeacao r = nomeador.nomear(null, null, UPLOAD);
        assertThat(r.nomePadronizado()).isEqualTo("documento.pdf");
    }
}
