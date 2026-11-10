package com.mendoncagalvao.extratos.email;

import com.mendoncagalvao.extratos.config.EmailProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polling periódico da caixa de e-mail (§13). O intervalo vem de {@code email.poll-interval-ms}
 * (default 5 min). O próprio {@link EmailIngestorService#poll()} verifica habilitação/autorização.
 */
@Component
public class EmailScheduler {

    private static final Logger log = LoggerFactory.getLogger(EmailScheduler.class);

    private final EmailIngestorService ingestor;
    private final EmailProperties props;

    public EmailScheduler(EmailIngestorService ingestor, EmailProperties props) {
        this.ingestor = ingestor;
        this.props = props;
    }

    @Scheduled(
            fixedDelayString = "${email.poll-interval-ms:300000}",
            initialDelayString = "${email.poll-interval-ms:300000}")
    public void poll() {
        if (!props.enabled()) {
            return;
        }
        try {
            ingestor.poll();
        } catch (RuntimeException e) {
            log.error("Erro inesperado no polling de e-mail: {}", e.getMessage());
        }
    }
}
