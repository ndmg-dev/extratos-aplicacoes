package com.mendoncagalvao.extratos.claude;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.mendoncagalvao.extratos.config.AnthropicProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaudeClientTest {

    private static final String TOOL_USE_OK = """
            { "stop_reason": "tool_use",
              "content": [ { "type": "tool_use", "name": "registrar_extrato",
                "input": { "documento_reconhecido": true, "banco": "CAIXA", "competencia": "2026-09",
                  "aplicacoes": [ { "identificador": "1", "detalhado": true, "saldo_inicial_bruto": 1000.00,
                    "aplicacoes": 0, "resgates_brutos": 0, "rendimento_bruto": 10.00, "ir_retido": 0,
                    "iof_retido": 0, "saldo_final_bruto": 1010.00 } ] } } ] }
            """;

    private WireMockServer wm;
    private ClaudeClient client;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(options().dynamicPort());
        wm.start();
        configureFor("localhost", wm.port());
        AnthropicProperties props = new AnthropicProperties(
                "test-key", "claude-sonnet-5-5", "http://localhost:" + wm.port(),
                4096, 180_000, 3, 20 /* backoff curto p/ teste */, "2023-06-01");
        client = new ClaudeClient(props, new ClaudeResponseParser());
    }

    @AfterEach
    void tearDown() {
        wm.stop();
    }

    @Test
    @DisplayName("sucesso: envia headers e body corretos e parseia o tool_use")
    void sucesso() throws Exception {
        stubFor(post(urlEqualTo("/v1/messages"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("content-type", "application/json")
                        .withBody(TOOL_USE_OK)));

        byte[] pdf = "%PDF-1.4 fake".getBytes(StandardCharsets.UTF_8);
        ClaudeResult r = client.extrair(pdf, "caixa.pdf");

        assertThat(r.dados()).isNotNull();
        assertThat(r.dados().banco).isEqualTo("CAIXA");
        assertThat(r.stopReason()).isEqualTo("tool_use");

        verify(postRequestedFor(urlEqualTo("/v1/messages"))
                .withHeader("x-api-key", equalTo("test-key"))
                .withHeader("anthropic-version", equalTo("2023-06-01")));

        // o body deve conter o PDF em base64, o prompt e a tool
        ObjectMapper m = new ObjectMapper();
        JsonNode body = m.readTree(wm.getAllServeEvents().get(0).getRequest().getBodyAsString());
        assertThat(body.get("model").asText()).isEqualTo("claude-sonnet-5-5");
        assertThat(body.get("max_tokens").asInt()).isEqualTo(4096);
        assertThat(body.get("tools").get(0).get("name").asText()).isEqualTo("registrar_extrato");
        assertThat(body.get("tool_choice").get("type").asText()).isEqualTo("auto");
        JsonNode content = body.get("messages").get(0).get("content");
        assertThat(content.get(0).get("type").asText()).isEqualTo("document");
        assertThat(content.get(0).get("source").get("media_type").asText()).isEqualTo("application/pdf");
        assertThat(content.get(0).get("source").get("data").asText())
                .isEqualTo(Base64.getEncoder().encodeToString(pdf));
        assertThat(content.get(1).get("type").asText()).isEqualTo("text");
        assertThat(content.get(1).get("text").asText()).startsWith("Você extrai dados de extratos");
    }

    @Test
    @DisplayName("retry: falha uma vez (500) e tem sucesso na segunda tentativa")
    void retryDepoisDeFalha() {
        stubFor(post(urlEqualTo("/v1/messages")).inScenario("retry")
                .whenScenarioStateIs("Started")
                .willReturn(serverError())
                .willSetStateTo("ok"));
        stubFor(post(urlEqualTo("/v1/messages")).inScenario("retry")
                .whenScenarioStateIs("ok")
                .willReturn(aResponse().withStatus(200)
                        .withHeader("content-type", "application/json")
                        .withBody(TOOL_USE_OK)));

        ClaudeResult r = client.extrair("x".getBytes(StandardCharsets.UTF_8), "a.pdf");

        assertThat(r.dados()).isNotNull();
        verify(2, postRequestedFor(urlEqualTo("/v1/messages")));
    }

    @Test
    @DisplayName("falha persistente: 3 tentativas e ClaudeException")
    void falhaPersistente() {
        stubFor(post(urlEqualTo("/v1/messages")).willReturn(serverError()));

        assertThatThrownBy(() -> client.extrair("x".getBytes(StandardCharsets.UTF_8), "a.pdf"))
                .isInstanceOf(ClaudeException.class);

        verify(3, postRequestedFor(urlEqualTo("/v1/messages")));
    }
}
