package com.mendoncagalvao.extratos.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mendoncagalvao.extratos.config.EmailProperties;
import com.mendoncagalvao.extratos.domain.OAuthToken;
import com.mendoncagalvao.extratos.repository.OAuthTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Fluxo OAuth2 do Google para acesso ao Gmail (§13), via HTTP puro — sem SDK.
 *
 * <ul>
 *   <li>{@link #urlAutorizacao()} monta a URL de consentimento (com {@code access_type=offline}
 *       e {@code prompt=consent} para garantir o refresh token).</li>
 *   <li>{@link #trocarCodigo(String)} troca o {@code code} do callback por tokens e persiste o
 *       refresh token.</li>
 *   <li>{@link #accessTokenValido()} devolve um access token válido, renovando-o pelo refresh
 *       token quando necessário.</li>
 * </ul>
 *
 * <p>Nunca registra em log os tokens nem o client secret.
 */
@Service
public class GmailOAuthService {

    private static final Logger log = LoggerFactory.getLogger(GmailOAuthService.class);

    private static final String AUTH_URI = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final Duration FOLGA_EXPIRACAO = Duration.ofSeconds(60);

    private final EmailProperties props;
    private final OAuthTokenRepository tokenRepo;
    private final Clock clock;
    private final ObjectMapper mapper = new ObjectMapper();
    private final RestClient http;

    public GmailOAuthService(EmailProperties props, OAuthTokenRepository tokenRepo, Clock clock) {
        this.props = props;
        this.tokenRepo = tokenRepo;
        this.clock = clock;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(props.timeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(props.timeoutMs()));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    /** Já existe um refresh token guardado (app autorizada). */
    public boolean autorizado() {
        return tokenRepo.findById(props.provider())
                .map(t -> t.getRefreshToken() != null && !t.getRefreshToken().isBlank())
                .orElse(false);
    }

    public String emailConta() {
        return tokenRepo.findById(props.provider()).map(OAuthToken::getEmailConta).orElse(null);
    }

    /** URL de consentimento para o usuário autorizar uma única vez. */
    public String urlAutorizacao() {
        exigirCredenciais();
        return UriComponentsBuilder.fromUriString(AUTH_URI)
                .queryParam("client_id", props.clientId())
                .queryParam("redirect_uri", enc(props.redirectUri()))
                .queryParam("response_type", "code")
                .queryParam("scope", enc(props.scope()))
                .queryParam("access_type", "offline")
                .queryParam("include_granted_scopes", "true")
                .queryParam("prompt", "consent")
                .build(true)
                .toUriString();
    }

    /** Troca o {@code code} do callback por tokens e persiste o refresh token. */
    public synchronized void trocarCodigo(String code) {
        exigirCredenciais();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", props.clientId());
        form.add("client_secret", props.clientSecret());
        form.add("redirect_uri", props.redirectUri());
        form.add("grant_type", "authorization_code");

        JsonNode resp = postToken(form, "troca de código");
        String refresh = texto(resp, "refresh_token");
        if (refresh == null) {
            throw new EmailException(
                    "O Google não retornou refresh_token. Revogue o acesso do app na conta "
                            + "(myaccount.google.com/permissions) e autorize novamente.");
        }
        OAuthToken token = tokenRepo.findById(props.provider()).orElseGet(OAuthToken::new);
        token.setProvedor(props.provider());
        token.setRefreshToken(refresh);
        aplicarAccessToken(token, resp);
        token.setEscopo(texto(resp, "scope"));
        token.setAtualizadoEm(clock.instant());
        tokenRepo.save(token);
        log.info("OAuth {} autorizado; refresh token armazenado.", props.provider());
    }

    /** Access token válido, renovado pelo refresh token quando necessário. */
    public synchronized String accessTokenValido() {
        OAuthToken token = tokenRepo.findById(props.provider())
                .orElseThrow(() -> new EmailException("E-mail não autorizado. Acesse /api/email/oauth/url."));
        if (token.getRefreshToken() == null || token.getRefreshToken().isBlank()) {
            throw new EmailException("Sem refresh token. Reautorize em /api/email/oauth/url.");
        }
        boolean valido = token.getAccessToken() != null && token.getExpiraEm() != null
                && clock.instant().isBefore(token.getExpiraEm().minus(FOLGA_EXPIRACAO));
        if (valido) {
            return token.getAccessToken();
        }
        return renovar(token);
    }

    private String renovar(OAuthToken token) {
        exigirCredenciais();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", props.clientId());
        form.add("client_secret", props.clientSecret());
        form.add("refresh_token", token.getRefreshToken());
        form.add("grant_type", "refresh_token");

        JsonNode resp = postToken(form, "refresh de token");
        aplicarAccessToken(token, resp);
        token.setAtualizadoEm(clock.instant());
        tokenRepo.save(token);
        log.debug("Access token do {} renovado.", props.provider());
        return token.getAccessToken();
    }

    public void registrarEmailConta(String email) {
        tokenRepo.findById(props.provider()).ifPresent(t -> {
            if (email != null && !email.equals(t.getEmailConta())) {
                t.setEmailConta(email);
                tokenRepo.save(t);
            }
        });
    }

    // ---- helpers ------------------------------------------------------------------------

    private void aplicarAccessToken(OAuthToken token, JsonNode resp) {
        String access = texto(resp, "access_token");
        if (access == null) {
            throw new EmailException("Resposta de token sem access_token.");
        }
        token.setAccessToken(access);
        long expiresIn = resp.has("expires_in") ? resp.get("expires_in").asLong() : 3600L;
        token.setExpiraEm(clock.instant().plusSeconds(expiresIn));
    }

    private JsonNode postToken(MultiValueMap<String, String> form, String contexto) {
        try {
            String corpo = http.post()
                    .uri(TOKEN_URI)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            return mapper.readTree(corpo);
        } catch (RestClientResponseException e) {
            throw new EmailException(
                    "Falha na " + contexto + " OAuth (" + e.getStatusCode().value() + "): "
                            + resumo(e.getResponseBodyAsString()), e);
        } catch (EmailException e) {
            throw e;
        } catch (Exception e) {
            throw new EmailException("Falha na " + contexto + " OAuth com o Google: " + e.getMessage(), e);
        }
    }

    private String resumo(String corpo) {
        if (corpo == null || corpo.isBlank()) {
            return "(sem corpo)";
        }
        try {
            JsonNode n = mapper.readTree(corpo);
            String err = texto(n, "error");
            String desc = texto(n, "error_description");
            if (err != null) {
                return desc != null ? err + " — " + desc : err;
            }
        } catch (Exception ignore) {
            // corpo não-JSON
        }
        return corpo.length() > 300 ? corpo.substring(0, 300) + "…" : corpo;
    }

    private void exigirCredenciais() {
        if (!props.temCredenciais()) {
            throw new EmailException("Defina EMAIL_CLIENT_ID e EMAIL_CLIENT_SECRET para usar a ingestão por e-mail.");
        }
    }

    private static String texto(JsonNode node, String campo) {
        JsonNode v = node.get(campo);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    Instant agora() {
        return clock.instant();
    }
}
