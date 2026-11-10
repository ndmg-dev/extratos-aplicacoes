package com.mendoncagalvao.extratos.email;

import com.mendoncagalvao.extratos.config.EmailProperties;
import com.mendoncagalvao.extratos.domain.EmailMensagemProcessada;
import com.mendoncagalvao.extratos.email.model.GmailAnexo;
import com.mendoncagalvao.extratos.email.model.GmailMensagem;
import com.mendoncagalvao.extratos.extracao.ArquivoUpload;
import com.mendoncagalvao.extratos.extracao.ExtracaoService;
import com.mendoncagalvao.extratos.extracao.Proveniencia;
import com.mendoncagalvao.extratos.extracao.ResultadoProcessamento;
import com.mendoncagalvao.extratos.repository.EmailMensagemProcessadaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.StringJoiner;

/**
 * Ingestão de extratos a partir do Gmail (§13): lista mensagens novas que casam o filtro
 * (anexo PDF + assunto), baixa os PDFs e reusa o {@link ExtracaoService} — mesmo pipeline do
 * upload web (extração → nomeação/arquivamento → validação → upsert). Mensagens já vistas são
 * puladas via {@link EmailMensagemProcessadaRepository}.
 */
@Service
public class EmailIngestorService {

    private static final Logger log = LoggerFactory.getLogger(EmailIngestorService.class);

    private final EmailProperties props;
    private final GmailOAuthService oauth;
    private final GmailClient gmail;
    private final ExtracaoService extracaoService;
    private final EmailMensagemProcessadaRepository processadasRepo;
    private final EmailSyncStatus status;
    private final GoogleDriveService drive;
    private final Clock clock;

    public EmailIngestorService(EmailProperties props, GmailOAuthService oauth, GmailClient gmail,
                                ExtracaoService extracaoService,
                                EmailMensagemProcessadaRepository processadasRepo,
                                EmailSyncStatus status, GoogleDriveService drive, Clock clock) {
        this.props = props;
        this.oauth = oauth;
        this.gmail = gmail;
        this.extracaoService = extracaoService;
        this.processadasRepo = processadasRepo;
        this.status = status;
        this.drive = drive;
        this.clock = clock;
    }

    /** Chamado pelo polling: só roda se habilitado e autorizado. */
    public EmailSyncResultado poll() {
        if (!props.enabled()) {
            return EmailSyncResultado.nenhum();
        }
        if (!oauth.autorizado()) {
            log.debug("Ingestão por e-mail habilitada, mas ainda não autorizada (sem refresh token).");
            return EmailSyncResultado.nenhum();
        }
        return sincronizar();
    }

    /** Executa um ciclo de sincronização. Usado pelo polling e pelo gatilho manual (teste). */
    public EmailSyncResultado sincronizar() {
        status.iniciar();
        int novos = 0;
        int documentos = 0;
        String erro = null;
        try {
            String accessToken = oauth.accessTokenValido();
            if (oauth.emailConta() == null) {
                try {
                    oauth.registrarEmailConta(gmail.emailDaConta(accessToken));
                } catch (RuntimeException e) {
                    log.debug("Não foi possível obter o e-mail da conta: {}", e.getMessage());
                }
            }

            // Resolve (ou cria) a label de "processado" uma vez por ciclo (best-effort).
            String labelId = resolverLabel(accessToken);

            String query = montarQuery();
            List<String> ids = gmail.listarIds(accessToken, query, props.maxPorCiclo());
            log.info("Ingestão e-mail: {} mensagens candidatas (query='{}').", ids.size(), query);

            for (String id : ids) {
                if (processadasRepo.existsById(id)) {
                    continue;
                }
                documentos += processarMensagem(accessToken, id, labelId);
                novos++;
            }
        } catch (RuntimeException e) {
            erro = e.getMessage();
            log.error("Falha no ciclo de ingestão por e-mail: {}", e.getMessage());
        } finally {
            status.concluir(novos, documentos, erro);
        }
        return new EmailSyncResultado(novos, documentos, erro);
    }

    private int processarMensagem(String accessToken, String id, String labelId) {
        GmailMensagem msg = gmail.buscarMensagem(accessToken, id);
        List<GmailAnexo> pdfs = msg.anexosPdf();
        int ok = 0;
        int falhas = 0;
        // Isola cada anexo: um PDF com problema não derruba os demais do mesmo e-mail.
        for (GmailAnexo anexo : pdfs) {
            try {
                Proveniencia prov = Proveniencia.email(msg.id(), msg.remetente(), msg.assunto(), msg.data());
                ResultadoProcessamento r = extracaoService.processar(
                        ArquivoUpload.email(anexo.nomeArquivo(), anexo.conteudo(), prov));
                arquivarNoDrive(accessToken, r, anexo.conteudo());
                ok++;
            } catch (RuntimeException e) {
                falhas++;
                log.error("Falha ao processar anexo '{}' do e-mail message_id={}: {}",
                        anexo.nomeArquivo(), msg.id(), e.getMessage());
            }
        }
        String statusMsg = pdfs.isEmpty() ? "SEM_PDF"
                : falhas == 0 ? "OK"
                : ok == 0 ? "ERRO"
                : "PARCIAL";
        marcarProcessada(msg, statusMsg);
        marcarNoGmail(accessToken, msg.id(), labelId);
        log.info("E-mail processado message_id={} remetente='{}' pdfs={} ok={} falhas={} status={}",
                msg.id(), msg.remetente(), pdfs.size(), ok, falhas, statusMsg);
        return ok;
    }

    /** Resolve/cria a label de "processado" (best-effort; null se indisponível). */
    private String resolverLabel(String accessToken) {
        String nome = props.processedLabel();
        if (nome == null || nome.isBlank()) {
            return null;
        }
        try {
            return gmail.acharOuCriarLabel(accessToken, nome);
        } catch (RuntimeException e) {
            log.warn("Não foi possível resolver a label '{}': {}", nome, e.getMessage());
            return null;
        }
    }

    /** Aplica a label no Gmail (best-effort): marca o e-mail como processado, excluindo-o das buscas. */
    private void marcarNoGmail(String accessToken, String messageId, String labelId) {
        if (labelId == null) {
            return;
        }
        try {
            gmail.aplicarLabel(accessToken, messageId, labelId);
        } catch (RuntimeException e) {
            log.error("Falha ao etiquetar e-mail message_id={}: {}", messageId, e.getMessage());
        }
    }

    /**
     * Arquiva a cópia no Google Drive (best-effort): uma falha aqui não derruba o processamento,
     * pois o storage primário já guardou o PDF.
     */
    private void arquivarNoDrive(String accessToken, ResultadoProcessamento r, byte[] pdf) {
        if (!props.driveConfigurado()) {
            return;
        }
        try {
            String clienteNome = r.lancamentos().stream()
                    .map(l -> l.getClienteNome())
                    .filter(n -> n != null && !n.isBlank())
                    .findFirst()
                    .orElse(null);
            drive.arquivar(accessToken, props.driveFolderId(), r.documento(), clienteNome, pdf);
        } catch (RuntimeException e) {
            log.error("Falha ao arquivar no Drive (documento_id={}): {}",
                    r.documento().getId(), e.getMessage());
        }
    }

    private void marcarProcessada(GmailMensagem msg, String statusMsg) {
        processadasRepo.save(new EmailMensagemProcessada(
                msg.id(), clock.instant(), msg.remetente(), msg.assunto(),
                msg.anexosPdf().size(), statusMsg));
    }

    /**
     * Monta a query do Gmail: anexo PDF + assunto, **excluindo os já etiquetados** como processados
     * (robustez: não re-puxa mesmo que a tabela de controle seja zerada).
     */
    String montarQuery() {
        String base = (props.query() != null && !props.query().isBlank())
                ? props.query()
                : construirBaseQuery();
        String label = props.processedLabel();
        if (label != null && !label.isBlank()) {
            base = base + " -label:\"" + label + "\"";
        }
        return base;
    }

    private String construirBaseQuery() {
        StringBuilder q = new StringBuilder("has:attachment filename:pdf");
        if (props.lookbackDays() > 0) {
            q.append(" newer_than:").append(props.lookbackDays()).append("d");
        }
        List<String> kws = props.subjectKeywords();
        if (kws != null && !kws.isEmpty()) {
            StringJoiner sj = new StringJoiner(" OR ");
            for (String k : kws) {
                if (k != null && !k.isBlank()) {
                    sj.add("\"" + k.trim() + "\"");
                }
            }
            if (sj.length() > 0) {
                q.append(" subject:(").append(sj).append(")");
            }
        }
        return q.toString();
    }

    /** Resultado de um ciclo de sincronização. */
    public record EmailSyncResultado(int novos, int documentos, String erro) {
        public static EmailSyncResultado nenhum() {
            return new EmailSyncResultado(0, 0, null);
        }
    }
}
