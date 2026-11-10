package com.mendoncagalvao.extratos.claude;

import com.mendoncagalvao.extratos.validacao.model.ExtratoExtraido;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaudeResponseParserTest {

    private final ClaudeResponseParser parser = new ClaudeResponseParser();

    @Test
    @DisplayName("tool_use.input é mapeado e os números viram BigDecimal (sem perda de precisão)")
    void toolUse() {
        String json = """
                {
                  "id": "msg_1", "type": "message", "role": "assistant", "model": "claude-sonnet-5-5",
                  "stop_reason": "tool_use",
                  "content": [
                    { "type": "text", "text": "ok" },
                    { "type": "tool_use", "id": "tu_1", "name": "registrar_extrato", "input": {
                        "documento_reconhecido": true,
                        "banco": "CAIXA",
                        "cliente_cnpj": "12345678000195",
                        "competencia": "2026-09",
                        "conta": "0001",
                        "total_bruto_informado": 1234.56,
                        "aplicacoes": [
                          { "identificador": "123", "detalhado": true,
                            "saldo_inicial_bruto": 1000.00, "aplicacoes": 0, "resgates_brutos": 0,
                            "rendimento_bruto": 10.00, "ir_retido": 0, "iof_retido": 0,
                            "saldo_final_bruto": 1010.00 }
                        ]
                    }}
                  ]
                }
                """;
        ClaudeResult r = parser.parse(json);

        assertThat(r.stopReason()).isEqualTo("tool_use");
        assertThat(r.rawJson()).isEqualTo(json);
        ExtratoExtraido d = r.dados();
        assertThat(d).isNotNull();
        assertThat(d.banco).isEqualTo("CAIXA");
        assertThat(d.documentoReconhecido).isTrue();
        assertThat(d.totalBrutoInformado).isEqualByComparingTo("1234.56");
        assertThat(d.totalBrutoInformado.scale()).isEqualTo(2); // BigDecimal, não double
        assertThat(d.aplicacoes).hasSize(1);
        assertThat(d.aplicacoes.get(0).saldoFinalBruto).isEqualByComparingTo("1010.00");
    }

    @Test
    @DisplayName("fallback: JSON embutido em bloco de texto é extraído")
    void fallbackJsonNoTexto() {
        String json = """
                {
                  "stop_reason": "end_turn",
                  "content": [
                    { "type": "text", "text": "Aqui estão os dados:\\n{\\"documento_reconhecido\\": true, \\"banco\\": \\"SICOOB\\", \\"competencia\\": \\"2026-07\\", \\"aplicacoes\\": []}\\nfim" }
                  ]
                }
                """;
        ClaudeResult r = parser.parse(json);

        assertThat(r.stopReason()).isEqualTo("end_turn");
        assertThat(r.dados()).isNotNull();
        assertThat(r.dados().banco).isEqualTo("SICOOB");
        assertThat(r.dados().aplicacoes).isEmpty();
    }

    @Test
    @DisplayName("sem tool_use e sem JSON no texto -> dados null")
    void semDados() {
        String json = """
                { "stop_reason": "end_turn",
                  "content": [ { "type": "text", "text": "Não consegui ler o documento." } ] }
                """;
        ClaudeResult r = parser.parse(json);
        assertThat(r.dados()).isNull();
        assertThat(r.stopReason()).isEqualTo("end_turn");
    }

    @Test
    @DisplayName("JSON malformado no texto -> dados null (não lança)")
    void jsonMalformadoNoTexto() {
        String json = """
                { "stop_reason": "end_turn",
                  "content": [ { "type": "text", "text": "resultado: { banco: SICOOB, } pronto" } ] }
                """;
        ClaudeResult r = parser.parse(json);
        assertThat(r.dados()).isNull();
    }

    @Test
    @DisplayName("stop_reason max_tokens é preservado")
    void maxTokens() {
        String json = """
                { "stop_reason": "max_tokens",
                  "content": [ { "type": "tool_use", "name": "registrar_extrato",
                    "input": { "documento_reconhecido": true, "banco": "CAIXA", "competencia": "2026-09", "aplicacoes": [] } } ] }
                """;
        ClaudeResult r = parser.parse(json);
        assertThat(r.stopReason()).isEqualTo("max_tokens");
        assertThat(r.dados()).isNotNull();
    }

    @Test
    @DisplayName("corpo não-JSON -> ClaudeException")
    void corpoInvalido() {
        assertThatThrownBy(() -> parser.parse("isto não é json"))
                .isInstanceOf(ClaudeException.class);
    }
}
