package com.mendoncagalvao.extratos.claude;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mendoncagalvao.extratos.config.AnthropicProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Cliente da API Anthropic — replica o nó HTTP {@code Claude - Extrair dados} (§6).
 *
 * <ul>
 *   <li>{@code POST https://api.anthropic.com/v1/messages}</li>
 *   <li>headers {@code x-api-key}, {@code anthropic-version}, {@code content-type}</li>
 *   <li>body com {@code document} (base64) + {@code text} (prompt) e a tool {@code registrar_extrato}</li>
 *   <li>timeout 180s; até 3 tentativas com espera de 5s; parsing via {@link ClaudeResponseParser}</li>
 * </ul>
 *
 * <p>Nunca registra em log o base64 do PDF nem a chave da API.
 */
@Component
public class ClaudeClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeClient.class);

    private final AnthropicProperties props;
    private final ClaudeResponseParser parser;
    private final ObjectMapper mapper = new ObjectMapper();
    private final RestClient restClient;

    private final String prompt;
    private final JsonNode toolSchema;

    public ClaudeClient(AnthropicProperties props, ClaudeResponseParser parser) {
        this.props = props;
        this.parser = parser;
        this.prompt = carregarRecurso("/claude/prompt.txt");
        try {
            this.toolSchema = mapper.readTree(carregarRecurso("/claude/tool-registrar-extrato.json"));
        } catch (IOException e) {
            throw new IllegalStateException("Esquema da tool registrar_extrato inválido", e);
        }

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(props.timeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(props.timeoutMs()));
        this.restClient = RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .build();
    }

    /**
     * Envia o PDF ao Claude e retorna os dados estruturados + {@code stop_reason} + JSON bruto.
     *
     * @param pdfBytes      conteúdo do PDF
     * @param nomeArquivo   nome do arquivo (apenas para log)
     * @throws ClaudeException se todas as tentativas falharem
     */
    public ClaudeResult extrair(byte[] pdfBytes, String nomeArquivo) {
        final String base64 = Base64.getEncoder().encodeToString(pdfBytes);
        final String body = montarBody(base64);

        RuntimeException ultimaFalha = null;
        for (int tentativa = 1; tentativa <= props.maxTentativas(); tentativa++) {
            try {
                long inicio = System.currentTimeMillis();
                String resposta = restClient.post()
                        .uri("/v1/messages")
                        .header("x-api-key", props.apiKey())
                        .header("anthropic-version", props.versao())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(String.class);

                ClaudeResult resultado = parser.parse(resposta);
                log.info("Claude OK arquivo={} tentativa={} tempo_ms={} stop_reason={} dados={}",
                        nomeArquivo, tentativa, System.currentTimeMillis() - inicio,
                        resultado.stopReason(), resultado.dados() != null);
                return resultado;
            } catch (RuntimeException e) {
                ultimaFalha = e;
                log.warn("Claude falhou arquivo={} tentativa={}/{} causa={}",
                        nomeArquivo, tentativa, props.maxTentativas(), e.toString());
                if (tentativa < props.maxTentativas()) {
                    dormir(props.backoffMs());
                }
            }
        }
        throw new ClaudeException(
                "Falha ao chamar o Claude para " + nomeArquivo + " após " + props.maxTentativas() + " tentativas",
                ultimaFalha);
    }

    String montarBody(String base64) {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", props.model());
        root.put("max_tokens", props.maxTokens());

        ArrayNode tools = root.putArray("tools");
        tools.add(toolSchema);

        ObjectNode toolChoice = root.putObject("tool_choice");
        toolChoice.put("type", "auto");

        ArrayNode messages = root.putArray("messages");
        ObjectNode msg = messages.addObject();
        msg.put("role", "user");
        ArrayNode content = msg.putArray("content");

        ObjectNode doc = content.addObject();
        doc.put("type", "document");
        ObjectNode source = doc.putObject("source");
        source.put("type", "base64");
        source.put("media_type", "application/pdf");
        source.put("data", base64);

        ObjectNode text = content.addObject();
        text.put("type", "text");
        text.put("text", prompt);

        return root.toString();
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new ClaudeException("Interrompido durante o backoff da chamada ao Claude", ie);
        }
    }

    private static String carregarRecurso(String caminho) {
        try (InputStream in = ClaudeClient.class.getResourceAsStream(caminho)) {
            if (in == null) {
                throw new IllegalStateException("Recurso não encontrado: " + caminho);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao carregar recurso: " + caminho, e);
        }
    }
}
