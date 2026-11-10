package com.mendoncagalvao.extratos.web;

import com.mendoncagalvao.extratos.config.EmailProperties;
import com.mendoncagalvao.extratos.email.EmailIngestorService;
import com.mendoncagalvao.extratos.email.EmailIngestorService.EmailSyncResultado;
import com.mendoncagalvao.extratos.email.EmailSyncStatus;
import com.mendoncagalvao.extratos.email.GmailOAuthService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Endpoints da ingestão por e-mail (§13): status para a interface, URL de consentimento OAuth,
 * callback de autorização e um gatilho manual de sincronização (para teste).
 * As credenciais/tokens nunca são expostos aqui.
 */
@RestController
@RequestMapping("/api/email")
public class EmailController {

    private final EmailProperties props;
    private final GmailOAuthService oauth;
    private final EmailIngestorService ingestor;
    private final EmailSyncStatus status;

    public EmailController(EmailProperties props, GmailOAuthService oauth,
                           EmailIngestorService ingestor, EmailSyncStatus status) {
        this.props = props;
        this.oauth = oauth;
        this.ingestor = ingestor;
        this.status = status;
    }

    @GetMapping("/status")
    public EmailStatusDTO status() {
        return new EmailStatusDTO(
                props.enabled(),
                props.temCredenciais(),
                oauth.autorizado(),
                oauth.emailConta(),
                status.getUltimaExecucao(),
                status.isEmExecucao(),
                status.getNovosUltimaExecucao(),
                status.getDocumentosUltimaExecucao(),
                status.getTotalMensagensProcessadas(),
                status.getUltimoErro(),
                props.pollIntervalMs());
    }

    @GetMapping("/oauth/url")
    public Map<String, String> urlAutorizacao() {
        return Map.of("url", oauth.urlAutorizacao());
    }

    @GetMapping(value = "/oauth/callback", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error) {
        if (error != null) {
            return ResponseEntity.badRequest().body(pagina("Autorização negada", "Erro: " + error, false));
        }
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(pagina("Autorização inválida", "Código ausente.", false));
        }
        try {
            oauth.trocarCodigo(code);
            return ResponseEntity.ok(pagina("Conta autorizada ✓",
                    "A ingestão por e-mail está ativa. Você já pode fechar esta aba.", true));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(pagina("Falha na autorização", e.getMessage(), false));
        }
    }

    /** Gatilho manual (teste): executa um ciclo de sincronização agora. */
    @PostMapping("/sync")
    public ResponseEntity<?> sync() {
        if (!oauth.autorizado()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "mensagem", "E-mail não autorizado. Gere a URL em GET /api/email/oauth/url e autorize."));
        }
        EmailSyncResultado r = ingestor.sincronizar();
        return ResponseEntity.ok(Map.of(
                "novos", r.novos(),
                "documentos", r.documentos(),
                "erro", r.erro() == null ? "" : r.erro()));
    }

    private static String pagina(String titulo, String msg, boolean ok) {
        String cor = ok ? "#35c07a" : "#ef6b61";
        return """
                <!doctype html><html lang="pt-BR"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%s</title></head>
                <body style="margin:0;font-family:system-ui,sans-serif;background:#0b0e12;color:#e7eaef;
                display:grid;place-items:center;height:100vh">
                <div style="text-align:center;max-width:28rem;padding:2rem">
                <h1 style="color:%s;font-size:1.3rem">%s</h1>
                <p style="color:#9aa4b0">%s</p></div></body></html>
                """.formatted(titulo, cor, titulo, msg);
    }

    /** Estado da ingestão para a interface. */
    public record EmailStatusDTO(
            boolean habilitado,
            boolean temCredenciais,
            boolean autorizado,
            String emailConta,
            Instant ultimaSincronizacao,
            boolean emExecucao,
            int novosUltimaExecucao,
            int documentosUltimaExecucao,
            long totalMensagensProcessadas,
            String ultimoErro,
            long pollIntervalMs
    ) {
    }
}
