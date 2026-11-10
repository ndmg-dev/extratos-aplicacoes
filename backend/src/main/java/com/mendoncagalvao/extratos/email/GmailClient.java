package com.mendoncagalvao.extratos.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mendoncagalvao.extratos.config.EmailProperties;
import com.mendoncagalvao.extratos.email.model.GmailAnexo;
import com.mendoncagalvao.extratos.email.model.GmailMensagem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Cliente REST da Gmail API (§13) — sem SDK, no mesmo estilo do {@code ClaudeClient}.
 * Lista mensagens que casam a query, lê cabeçalhos e baixa os anexos PDF.
 */
@Component
public class GmailClient {

    private static final Logger log = LoggerFactory.getLogger(GmailClient.class);
    private static final String BASE = "https://gmail.googleapis.com/gmail/v1/users/me";

    private final ObjectMapper mapper = new ObjectMapper();
    private final RestClient http;

    public GmailClient(EmailProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(props.timeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(props.timeoutMs()));
        this.http = RestClient.builder().baseUrl(BASE).requestFactory(factory).build();
    }

    /** E-mail da conta autorizada. */
    public String emailDaConta(String accessToken) {
        JsonNode node = getJson(accessToken, b -> b.path("/profile").build());
        return texto(node, "emailAddress");
    }

    /** Ids das mensagens que casam a query (mais recentes primeiro). */
    public List<String> listarIds(String accessToken, String query, int max) {
        JsonNode node = getJson(accessToken, b -> b
                .path("/messages")
                .queryParam("q", query)
                .queryParam("maxResults", max)
                .build());
        List<String> ids = new ArrayList<>();
        JsonNode arr = node.get("messages");
        if (arr != null && arr.isArray()) {
            arr.forEach(m -> ids.add(m.get("id").asText()));
        }
        return ids;
    }

    /** Busca uma mensagem (cabeçalhos + anexos PDF baixados). */
    public GmailMensagem buscarMensagem(String accessToken, String id) {
        JsonNode msg = getJson(accessToken, b -> b
                .path("/messages/" + id)
                .queryParam("format", "full")
                .build());

        JsonNode payload = msg.get("payload");
        String assunto = header(payload, "Subject");
        String remetente = header(payload, "From");
        Instant data = msg.has("internalDate")
                ? Instant.ofEpochMilli(msg.get("internalDate").asLong())
                : null;

        List<GmailAnexo> anexos = new ArrayList<>();
        coletarAnexosPdf(accessToken, id, payload, anexos);
        return new GmailMensagem(id, remetente, assunto, data, anexos);
    }

    // ---- parsing recursivo das partes ---------------------------------------------------

    private void coletarAnexosPdf(String accessToken, String messageId, JsonNode parte, List<GmailAnexo> out) {
        if (parte == null) {
            return;
        }
        String filename = texto(parte, "filename");
        JsonNode body = parte.get("body");
        String mimeType = texto(parte, "mimeType");

        boolean pareceePdf = (filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".pdf"))
                || "application/pdf".equalsIgnoreCase(mimeType);

        if (pareceePdf && body != null && body.has("attachmentId")) {
            String attachmentId = body.get("attachmentId").asText();
            byte[] bytes = baixarAnexo(accessToken, messageId, attachmentId);
            String nome = (filename == null || filename.isBlank()) ? (messageId + ".pdf") : filename;
            out.add(new GmailAnexo(nome, bytes));
        }

        JsonNode partes = parte.get("parts");
        if (partes != null && partes.isArray()) {
            for (JsonNode sub : partes) {
                coletarAnexosPdf(accessToken, messageId, sub, out);
            }
        }
    }

    private byte[] baixarAnexo(String accessToken, String messageId, String attachmentId) {
        JsonNode node = getJson(accessToken, b -> b
                .path("/messages/" + messageId + "/attachments/" + attachmentId)
                .build());
        String data = texto(node, "data");
        if (data == null) {
            throw new EmailException("Anexo sem dados (messageId=" + messageId + ").");
        }
        return Base64.getUrlDecoder().decode(data);
    }

    // ---- helpers ------------------------------------------------------------------------

    @FunctionalInterface
    private interface UriFn {
        java.net.URI build(org.springframework.web.util.UriBuilder b);
    }

    private JsonNode getJson(String accessToken, UriFn uri) {
        try {
            String corpo = http.get()
                    .uri(uri::build)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(String.class);
            return mapper.readTree(corpo);
        } catch (RestClientResponseException e) {
            throw new EmailException(
                    "Gmail API " + e.getStatusCode().value() + ": " + mensagemErro(e.getResponseBodyAsString()), e);
        } catch (EmailException e) {
            throw e;
        } catch (Exception e) {
            throw new EmailException("Falha ao chamar a Gmail API: " + e.getMessage(), e);
        }
    }

    /** Extrai {@code error.message} do corpo JSON do Google, ou devolve o corpo truncado. */
    private String mensagemErro(String corpo) {
        if (corpo == null || corpo.isBlank()) {
            return "(sem corpo)";
        }
        try {
            JsonNode err = mapper.readTree(corpo).get("error");
            if (err != null && err.has("message")) {
                return err.get("message").asText();
            }
        } catch (Exception ignore) {
            // corpo não-JSON
        }
        return corpo.length() > 300 ? corpo.substring(0, 300) + "…" : corpo;
    }

    private static String header(JsonNode payload, String nome) {
        if (payload == null) {
            return null;
        }
        JsonNode headers = payload.get("headers");
        if (headers != null && headers.isArray()) {
            for (JsonNode h : headers) {
                if (nome.equalsIgnoreCase(texto(h, "name"))) {
                    return texto(h, "value");
                }
            }
        }
        return null;
    }

    private static String texto(JsonNode node, String campo) {
        if (node == null) {
            return null;
        }
        JsonNode v = node.get(campo);
        return v == null || v.isNull() ? null : v.asText();
    }
}
