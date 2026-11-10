package com.mendoncagalvao.extratos.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mendoncagalvao.extratos.config.EmailProperties;
import com.mendoncagalvao.extratos.domain.Documento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Arquiva os PDFs dos extratos no Google Drive (§13), na pasta configurada
 * ({@code email.drive-folder-id}) organizados por <b>ANO/MÊS/CNPJ</b>.
 *
 * <p>Usa o mesmo token OAuth do Gmail (escopo {@code drive}). É um arquivamento
 * secundário/auditável — o storage primário (filesystem) continua sendo a fonte do app.
 * Idempotente: se já existir um arquivo com o mesmo nome na pasta, não sobe de novo.
 */
@Service
public class GoogleDriveService {

    private static final Logger log = LoggerFactory.getLogger(GoogleDriveService.class);
    private static final String BASE = "https://www.googleapis.com";
    private static final String MIME_FOLDER = "application/vnd.google-apps.folder";

    private final ObjectMapper mapper = new ObjectMapper();
    private final RestClient http;
    // Cache de pastas resolvidas (parentId + "/" + nome → folderId) para poupar chamadas.
    private final ConcurrentHashMap<String, String> cachePastas = new ConcurrentHashMap<>();

    public GoogleDriveService(EmailProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(props.timeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(props.timeoutMs()));
        this.http = RestClient.builder().baseUrl(BASE).requestFactory(factory).build();
    }

    /**
     * Arquiva o PDF no Drive em {@code <pastaRaiz>/ANO/MÊS/CNPJ/<nome>}.
     *
     * @param accessToken token OAuth válido (escopo drive)
     * @param pastaRaizId id da pasta "APLICAÇÕES"
     * @param d           documento já processado (competência/cnpj/nome padronizado)
     * @param pdf         bytes do PDF
     * @return id do arquivo no Drive (novo ou já existente), ou {@code null} se não arquivou
     */
    public String arquivar(String accessToken, String pastaRaizId, Documento d, byte[] pdf) {
        String ano = fatia(d.getCompetencia(), 0, 4, "SEM_ANO");
        String mes = fatia(d.getCompetencia(), 5, 7, "SEM_MES");
        String cnpj = (d.getCnpj() != null && !d.getCnpj().isBlank()) ? d.getCnpj() : "_SEM_CNPJ";
        String nome = (d.getNomePadronizado() != null && !d.getNomePadronizado().isBlank())
                ? d.getNomePadronizado()
                : (d.getNomeArquivoOriginal() != null ? d.getNomeArquivoOriginal() : "extrato.pdf");

        String raiz = normalizarFolderId(pastaRaizId);
        String pastaAno = acharOuCriarPasta(accessToken, ano, raiz);
        String pastaMes = acharOuCriarPasta(accessToken, mes, pastaAno);
        String pastaCnpj = acharOuCriarPasta(accessToken, cnpj, pastaMes);

        String existente = acharArquivo(accessToken, nome, pastaCnpj);
        if (existente != null) {
            log.debug("Drive: '{}' já existe em {}/{}/{} — não reenviado.", nome, ano, mes, cnpj);
            return existente;
        }
        String id = subirPdf(accessToken, nome, pdf, pastaCnpj);
        log.info("Drive: arquivado '{}' em {}/{}/{} (fileId={}).", nome, ano, mes, cnpj, id);
        return id;
    }

    // ---- pastas -------------------------------------------------------------------------

    private String acharOuCriarPasta(String accessToken, String nome, String parentId) {
        String chave = parentId + "/" + nome;
        String cacheado = cachePastas.get(chave);
        if (cacheado != null) {
            return cacheado;
        }
        String id = acharPasta(accessToken, nome, parentId);
        if (id == null) {
            id = criarPasta(accessToken, nome, parentId);
        }
        cachePastas.put(chave, id);
        return id;
    }

    private String acharPasta(String accessToken, String nome, String parentId) {
        String q = "name = '" + escapar(nome) + "' and mimeType = '" + MIME_FOLDER
                + "' and '" + parentId + "' in parents and trashed = false";
        return primeiroId(listar(accessToken, q));
    }

    private String criarPasta(String accessToken, String nome, String parentId) {
        ObjectNode body = mapper.createObjectNode();
        body.put("name", nome);
        body.put("mimeType", MIME_FOLDER);
        ArrayNode parents = body.putArray("parents");
        parents.add(parentId);
        JsonNode resp = postJson(accessToken, "/drive/v3/files?fields=id", body.toString());
        return resp.get("id").asText();
    }

    private String acharArquivo(String accessToken, String nome, String parentId) {
        String q = "name = '" + escapar(nome) + "' and mimeType != '" + MIME_FOLDER
                + "' and '" + parentId + "' in parents and trashed = false";
        return primeiroId(listar(accessToken, q));
    }

    // ---- upload -------------------------------------------------------------------------

    /** Cria o recurso (metadados) e envia o conteúdo (uploadType=media) em dois passos. */
    private String subirPdf(String accessToken, String nome, byte[] pdf, String parentId) {
        ObjectNode meta = mapper.createObjectNode();
        meta.put("name", nome);
        ArrayNode parents = meta.putArray("parents");
        parents.add(parentId);
        JsonNode criado = postJson(accessToken, "/drive/v3/files?fields=id", meta.toString());
        String id = criado.get("id").asText();

        try {
            http.patch()
                    .uri("/upload/drive/v3/files/" + id + "?uploadType=media")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw new EmailException("Drive: falha no upload do conteúdo (" + e.getStatusCode().value()
                    + "): " + resumo(e.getResponseBodyAsString()), e);
        } catch (Exception e) {
            throw new EmailException("Drive: falha no upload do conteúdo: " + e.getMessage(), e);
        }
        return id;
    }

    // ---- HTTP helpers -------------------------------------------------------------------

    private JsonNode listar(String accessToken, String q) {
        try {
            String corpo = http.get()
                    .uri(u -> u
                            .path("/drive/v3/files")
                            .queryParam("q", q)
                            .queryParam("fields", "files(id,name)")
                            .queryParam("spaces", "drive")
                            .queryParam("pageSize", 10)
                            .build())
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(String.class);
            return mapper.readTree(corpo);
        } catch (RestClientResponseException e) {
            throw new EmailException("Drive: falha ao listar (" + e.getStatusCode().value()
                    + "): " + resumo(e.getResponseBodyAsString()), e);
        } catch (Exception e) {
            throw new EmailException("Drive: falha ao listar: " + e.getMessage(), e);
        }
    }

    private JsonNode postJson(String accessToken, String url, String json) {
        try {
            String corpo = http.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json)
                    .retrieve()
                    .body(String.class);
            return mapper.readTree(corpo);
        } catch (RestClientResponseException e) {
            throw new EmailException("Drive: falha na chamada (" + e.getStatusCode().value()
                    + "): " + resumo(e.getResponseBodyAsString()), e);
        } catch (Exception e) {
            throw new EmailException("Drive: falha na chamada: " + e.getMessage(), e);
        }
    }

    private static String primeiroId(JsonNode resp) {
        JsonNode files = resp.get("files");
        if (files != null && files.isArray() && !files.isEmpty()) {
            return files.get(0).get("id").asText();
        }
        return null;
    }

    /**
     * Aceita tanto o ID puro quanto a URL da pasta colada inteira
     * (ex.: https://drive.google.com/drive/folders/&lt;ID&gt;?usp=drive_link) e devolve só o ID.
     */
    static String normalizarFolderId(String v) {
        if (v == null) {
            return null;
        }
        String s = v.trim();
        int i = s.indexOf("/folders/");
        if (i >= 0) {
            s = s.substring(i + "/folders/".length());
        }
        int q = s.indexOf('?');
        if (q >= 0) {
            s = s.substring(0, q);
        }
        int h = s.indexOf('#');
        if (h >= 0) {
            s = s.substring(0, h);
        }
        int sl = s.indexOf('/');
        if (sl >= 0) {
            s = s.substring(0, sl);
        }
        return s;
    }

    private static String fatia(String s, int ini, int fim, String fallback) {
        if (s == null || s.length() < fim) {
            return fallback;
        }
        return s.substring(ini, fim);
    }

    /** Escapa aspas simples e barras para a sintaxe de query do Drive. */
    private static String escapar(String s) {
        return s.replace("\\", "\\\\").replace("'", "\\'");
    }

    private String resumo(String corpo) {
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
}
