package com.mendoncagalvao.extratos.extracao;

import com.mendoncagalvao.extratos.claude.ClaudeClient;
import com.mendoncagalvao.extratos.claude.ClaudeException;
import com.mendoncagalvao.extratos.claude.ClaudeResult;
import com.mendoncagalvao.extratos.config.ExtracaoProperties;
import com.mendoncagalvao.extratos.domain.Documento;
import com.mendoncagalvao.extratos.repository.DocumentoRepository;
import com.mendoncagalvao.extratos.storage.NomeadorArquivo;
import com.mendoncagalvao.extratos.storage.ResultadoNomeacao;
import com.mendoncagalvao.extratos.storage.StorageService;
import com.mendoncagalvao.extratos.support.RecursoNaoEncontradoException;
import com.mendoncagalvao.extratos.validacao.ValidacaoService;
import com.mendoncagalvao.extratos.validacao.model.ExtratoExtraido;
import com.mendoncagalvao.extratos.validacao.model.ItemResposta;
import com.mendoncagalvao.extratos.validacao.model.LinhaLancamento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Implementação do fluxo da §5. Síncrono por requisição; a chamada (lenta) ao Claude roda fora
 * de transação, e a persistência (documento + upsert dos lançamentos) acontece em uma transação
 * curta via {@link ExtracaoPersistencia}.
 */
@Service
public class ExtracaoServiceImpl implements ExtracaoService {

    private static final Logger log = LoggerFactory.getLogger(ExtracaoServiceImpl.class);

    private final ClaudeClient claudeClient;
    private final ValidacaoService validacaoService;
    private final NomeadorArquivo nomeadorArquivo;
    private final StorageService storageService;
    private final DocumentoRepository documentoRepository;
    private final ExtracaoPersistencia persistencia;
    private final ExtracaoProperties props;
    private final Clock clock;

    public ExtracaoServiceImpl(ClaudeClient claudeClient,
                               ValidacaoService validacaoService,
                               NomeadorArquivo nomeadorArquivo,
                               StorageService storageService,
                               DocumentoRepository documentoRepository,
                               ExtracaoPersistencia persistencia,
                               ExtracaoProperties props,
                               Clock clock) {
        this.claudeClient = claudeClient;
        this.validacaoService = validacaoService;
        this.nomeadorArquivo = nomeadorArquivo;
        this.storageService = storageService;
        this.documentoRepository = documentoRepository;
        this.persistencia = persistencia;
        this.props = props;
        this.clock = clock;
    }

    @Override
    public ResultadoProcessamento processar(ArquivoUpload arquivo) {
        byte[] bytes = arquivo.conteudo();
        String sha = sha256(bytes);

        // Idempotência (§5): reaproveita o documento existente com mesmo sha256 em vez de duplicar.
        Documento documento = documentoRepository.findFirstBySha256(sha).orElseGet(Documento::new);

        return processarDocumento(documento, bytes, arquivo.nomeOriginal(), sha, arquivo.proveniencia());
    }

    @Override
    public List<ResultadoProcessamento> processarLote(List<ArquivoUpload> arquivos) {
        List<ResultadoProcessamento> resultados = new ArrayList<>(arquivos.size());
        for (int i = 0; i < arquivos.size(); i++) {
            ArquivoUpload arquivo = arquivos.get(i);
            try {
                resultados.add(processar(arquivo));
            } catch (RuntimeException e) {
                // Não derruba o lote: registra o item como ERRO não persistido e segue (§12).
                log.error("Falha inesperada ao processar arquivo={}", arquivo.nomeOriginal(), e);
                resultados.add(resultadoErroTransitorio(arquivo.nomeOriginal(), arquivo.proveniencia(), e));
            }
            if (props.throttleMs() > 0 && i < arquivos.size() - 1) {
                dormir(props.throttleMs());
            }
        }
        return resultados;
    }

    @Override
    public ResultadoProcessamento reprocessar(UUID documentoId) {
        Documento documento = documentoRepository.findById(documentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Documento não encontrado: " + documentoId));
        byte[] bytes = storageService.carregar(documento.getStorageKey());
        String nomeOriginal = documento.getNomeArquivoOriginal();
        String sha = documento.getSha256() != null ? documento.getSha256() : sha256(bytes);
        return processarDocumento(documento, bytes, nomeOriginal, sha, provenienciaDe(documento));
    }

    /** Reconstrói a proveniência de um documento já persistido (para reprocessamento). */
    private static Proveniencia provenienciaDe(Documento d) {
        if (Documento.ORIGEM_EMAIL.equals(d.getOrigem())) {
            return Proveniencia.email(d.getEmailMessageId(), d.getEmailRemetente(),
                    d.getEmailAssunto(), d.getEmailData());
        }
        return Proveniencia.upload();
    }

    // ---- núcleo do fluxo ----------------------------------------------------------------

    private ResultadoProcessamento processarDocumento(Documento documento, byte[] bytes,
                                                      String nomeOriginal, String sha,
                                                      Proveniencia proveniencia) {
        final Instant agora = clock.instant();

        ExtratoExtraido dados = null;
        String stopReason = null;
        String rawJson = null;
        try {
            ClaudeResult cr = claudeClient.extrair(bytes, nomeOriginal);
            dados = cr.dados();
            stopReason = cr.stopReason();
            rawJson = cr.rawJson();
        } catch (ClaudeException e) {
            // Falha na API: item vira ERRO sem derrubar o lote (§12).
            log.error("Extração falhou (Claude) arquivo={}: {}", nomeOriginal, e.getMessage());
        }

        // Nomear e arquivar (§9 / Apêndice D). Mesmo em ERRO arquivamos o PDF (nome original).
        ResultadoNomeacao nomeacao = nomeadorArquivo.nomear(dados, nomeOriginal, LocalDate.ofInstant(agora, zona()));
        storageService.salvar(nomeacao.storageKey(), bytes);

        preencherDocumento(documento, dados, nomeOriginal, nomeacao, stopReason, rawJson, sha, bytes.length, agora, proveniencia);

        // Normalizar e validar (Apêndice C). email_id preenchido quando a origem é e-mail.
        List<LinhaLancamento> linhas = validacaoService.normalizarEValidar(
                new ItemResposta(dados, stopReason, nomeOriginal, proveniencia.emailIdOuVazio()));

        ResultadoProcessamento resultado = persistencia.persistir(documento, linhas);
        log.info("Processado arquivo={} documento_id={} status={} lancamentos={}",
                nomeOriginal, resultado.documento().getId(),
                resultado.documento().getStatusProcessamento(), resultado.lancamentos().size());
        return resultado;
    }

    private void preencherDocumento(Documento d, ExtratoExtraido dados, String nomeOriginal,
                                    ResultadoNomeacao nomeacao, String stopReason, String rawJson,
                                    String sha, long tamanho, Instant agora, Proveniencia prov) {
        d.setOrigem(prov.origem());
        if (Proveniencia.ORIGEM_EMAIL.equals(prov.origem())) {
            d.setEmailMessageId(prov.emailMessageId());
            d.setEmailRemetente(prov.emailRemetente());
            d.setEmailAssunto(prov.emailAssunto());
            d.setEmailData(prov.emailData());
        }
        d.setNomeArquivoOriginal(nomeOriginal);
        d.setNomePadronizado(nomeacao.nomePadronizado());
        d.setStorageKey(nomeacao.storageKey());
        d.setSha256(sha);
        d.setTamanhoBytes(tamanho);
        d.setStopReason(stopReason);
        d.setRawClaudeJson(rawJson);

        if (dados != null) {
            d.setBanco(dados.banco);
            d.setCompetencia(dados.competencia);
            d.setConta(dados.conta);
            d.setCnpj(nomeacao.semCnpj() ? null : digitos(dados.clienteCnpj));
            d.setDocumentoReconhecido(dados.documentoReconhecido);
            d.setTotalBrutoInformado(dados.totalBrutoInformado);
            d.setObservacoes(dados.observacoes);
            d.setStatusProcessamento(Documento.STATUS_PROCESSADO);
        } else {
            d.setDocumentoReconhecido(null);
            d.setStatusProcessamento(Documento.STATUS_ERRO);
        }

        if (d.getCriadoEm() == null) {
            d.setCriadoEm(agora);
        }
        d.setProcessadoEm(agora);
    }

    private ResultadoProcessamento resultadoErroTransitorio(String nomeOriginal, Proveniencia prov,
                                                            RuntimeException causa) {
        Documento d = new Documento();
        d.setOrigem(prov.origem());
        d.setNomeArquivoOriginal(nomeOriginal);
        d.setStatusProcessamento(Documento.STATUS_ERRO);
        d.setObservacoes("Falha no processamento: " + causa.getMessage());
        d.setCriadoEm(clock.instant());
        d.setProcessadoEm(clock.instant());
        return new ResultadoProcessamento(d, List.of());
    }

    private ZoneId zona() {
        return ZoneId.of(props.zona());
    }

    private static String digitos(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
