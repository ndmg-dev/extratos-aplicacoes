package com.mendoncagalvao.extratos.claude;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.mendoncagalvao.extratos.validacao.model.ExtratoExtraido;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converte o corpo JSON da resposta da API Anthropic em {@link ClaudeResult}.
 *
 * <p>Porte <b>verbatim</b> do parsing do Apêndice C / §6: procura o bloco
 * {@code content[].type === "tool_use"} e usa {@code .input}; se não houver, junta os blocos de
 * texto e extrai o primeiro objeto {@code {...}} como JSON (fallback). Guarda {@code stop_reason}.
 *
 * <p>Preserva precisão monetária: números são lidos como {@link java.math.BigDecimal}
 * ({@code USE_BIG_DECIMAL_FOR_FLOATS}). Mapeia {@code snake_case} do esquema para os campos
 * {@code camelCase} do modelo.
 */
@Component
public class ClaudeResponseParser {

    /** Equivale a {@code /\{[\s\S]*\}/} do JS (guloso: do primeiro {@code &#123;} ao último {@code &#125;}). */
    private static final Pattern JSON_OBJETO = Pattern.compile("\\{[\\s\\S]*\\}");

    private final ObjectMapper mapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    /**
     * @param rawJson corpo JSON bruto retornado por {@code POST /v1/messages}
     * @return dados estruturados (ou {@code null}) + {@code stop_reason} + o JSON bruto
     * @throws ClaudeException se o corpo não for um JSON válido
     */
    public ClaudeResult parse(String rawJson) {
        final JsonNode root;
        try {
            root = mapper.readTree(rawJson);
        } catch (Exception e) {
            throw new ClaudeException("Resposta do Claude não é um JSON válido", e);
        }

        final String stopReason = root.path("stop_reason").isNull() || root.path("stop_reason").isMissingNode()
                ? null
                : root.path("stop_reason").asText(null);

        final JsonNode content = root.path("content");

        ExtratoExtraido dados = null;

        // 1) bloco tool_use -> input
        if (content.isArray()) {
            for (JsonNode bloco : content) {
                if ("tool_use".equals(bloco.path("type").asText())) {
                    JsonNode input = bloco.get("input");
                    if (input != null && !input.isNull()) {
                        dados = mapper.convertValue(input, ExtratoExtraido.class);
                    }
                    break;
                }
            }
        }

        // 2) fallback: JSON em bloco de texto
        if (dados == null && content.isArray()) {
            StringBuilder texto = new StringBuilder();
            for (JsonNode bloco : content) {
                if ("text".equals(bloco.path("type").asText())) {
                    if (texto.length() > 0) {
                        texto.append('\n');
                    }
                    texto.append(bloco.path("text").asText(""));
                }
            }
            Matcher achado = JSON_OBJETO.matcher(texto.toString());
            if (achado.find()) {
                try {
                    dados = mapper.readValue(achado.group(), ExtratoExtraido.class);
                } catch (Exception e) {
                    dados = null; // JSON inválido no texto -> sem dados
                }
            }
        }

        return new ClaudeResult(dados, stopReason, rawJson);
    }
}
