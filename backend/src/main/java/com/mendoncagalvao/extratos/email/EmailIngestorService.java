package com.mendoncagalvao.extratos.email;

import com.mendoncagalvao.extratos.config.EmailProperties;
import com.mendoncagalvao.extratos.domain.EmailMensagemProcessada;
import com.mendoncagalvao.extratos.email.model.GmailAnexo;
import com.mendoncagalvao.extratos.email.model.GmailMensagem;
import com.mendoncagalvao.extratos.extracao.ArquivoUpload;
import com.mendoncagalvao.extratos.extracao.ExtracaoService;
import com.mendoncagalvao.extratos.extracao.Proveniencia;
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
    private final Clock clock;

    public EmailIngestorService(EmailProperties props, GmailOAuthService oauth, GmailClient gmail,
                                ExtracaoService extracaoService,
                                EmailMensagemProcessadaRepository processadasRepo,
                                EmailSyncStatus status, Clock clock) {
        this.props = props;
        this.oauth = oauth;
        this.gmail = gmail;
        this.extracaoService = extracaoService;
        this.processadasRepo = processadasRepo;
        this.status = status;
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

            String query = montarQuery();
            List<String> ids = gmail.listarIds(accessToken, query, props.maxPorCiclo());
            log.info("Ingestão e-mail: {} mensagens candidatas (query='{}').", ids.size(), query);

            for (String id : ids) {
                if (processadasRepo.existsById(id)) {
                    continue;
                }
                documentos += processarMensagem(accessToken, id);
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

    private int processarMensagem(String accessToken, String id) {
        GmailMensagem msg = gmail.buscarMensagem(accessToken, id);
        List<GmailAnexo> pdfs = msg.anexosPdf();
        int docs = 0;
        try {
            for (GmailAnexo anexo : pdfs) {
                Proveniencia prov = Proveniencia.email(msg.id(), msg.remetente(), msg.assunto(), msg.data());
                extracaoService.processar(ArquivoUpload.email(anexo.nomeArquivo(), anexo.conteudo(), prov));
                docs++;
            }
            marcarProcessada(msg, pdfs.size() == 0 ? "SEM_PDF" : "OK");
            log.info("E-mail processado message_id={} remetente='{}' pdfs={}", msg.id(), msg.remetente(), pdfs.size());
        } catch (RuntimeException e) {
            // Marca como ERRO para não ficar reprocessando em loop; o problema fica registrado.
            marcarProcessada(msg, "ERRO");
            log.error("Falha ao processar e-mail message_id={}: {}", msg.id(), e.getMessage());
        }
        return docs;
    }

    private void marcarProcessada(GmailMensagem msg, String statusMsg) {
        processadasRepo.save(new EmailMensagemProcessada(
                msg.id(), clock.instant(), msg.remetente(), msg.assunto(),
                msg.anexosPdf().size(), statusMsg));
    }

    /** Monta a query do Gmail: anexo PDF + assunto com palavras-chave + janela de retrospecto. */
    String montarQuery() {
        if (props.query() != null && !props.query().isBlank()) {
            return props.query();
        }
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
