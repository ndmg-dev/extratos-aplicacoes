package com.mendoncagalvao.extratos.web;

import com.mendoncagalvao.extratos.config.UploadProperties;
import com.mendoncagalvao.extratos.domain.Lancamento;
import com.mendoncagalvao.extratos.extracao.ArquivoUpload;
import com.mendoncagalvao.extratos.extracao.ExtracaoService;
import com.mendoncagalvao.extratos.extracao.ResultadoProcessamento;
import com.mendoncagalvao.extratos.web.dto.DocumentoDTO;
import com.mendoncagalvao.extratos.web.dto.LancamentoDTO;
import com.mendoncagalvao.extratos.web.dto.UploadRespostaDTO;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** POST /api/extratos/upload — recebe 1..N PDFs, processa e retorna documentos + lançamentos + resumo (§7). */
@RestController
@RequestMapping("/api/extratos")
public class UploadController {

    private final ExtracaoService extracaoService;
    private final UploadProperties uploadProps;

    public UploadController(ExtracaoService extracaoService, UploadProperties uploadProps) {
        this.extracaoService = extracaoService;
        this.uploadProps = uploadProps;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadRespostaDTO upload(@RequestParam("files") List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Envie ao menos um arquivo PDF no campo 'files'.");
        }
        List<ArquivoUpload> arquivos = new ArrayList<>(files.size());
        for (MultipartFile f : files) {
            validar(f);
            arquivos.add(ArquivoUpload.upload(nomeSeguro(f), f.getContentType(), ler(f)));
        }

        List<ResultadoProcessamento> resultados = extracaoService.processarLote(arquivos);

        List<DocumentoDTO> documentos = new ArrayList<>();
        List<LancamentoDTO> lancamentos = new ArrayList<>();
        for (ResultadoProcessamento r : resultados) {
            documentos.add(DtoMapper.toDto(r.documento(), false));
            r.lancamentos().forEach(l -> lancamentos.add(DtoMapper.toDto(l)));
        }
        Map<String, Long> contagem = resultados.stream()
                .flatMap(r -> r.lancamentos().stream())
                .collect(Collectors.groupingBy(Lancamento::getStatus, Collectors.counting()));

        return new UploadRespostaDTO(documentos, lancamentos, contagem);
    }

    private void validar(MultipartFile f) {
        if (f.isEmpty()) {
            throw new IllegalArgumentException("Arquivo vazio: " + f.getOriginalFilename());
        }
        if (f.getSize() > uploadProps.maxFileBytes()) {
            throw new IllegalArgumentException(
                    "Arquivo '" + f.getOriginalFilename() + "' excede o limite de " + uploadProps.maxFileMb() + " MB.");
        }
        String nome = f.getOriginalFilename() == null ? "" : f.getOriginalFilename().toLowerCase(Locale.ROOT);
        boolean pdfPorTipo = "application/pdf".equalsIgnoreCase(f.getContentType());
        boolean pdfPorNome = nome.endsWith(".pdf");
        if (!pdfPorTipo && !pdfPorNome) {
            throw new IllegalArgumentException(
                    "Arquivo '" + f.getOriginalFilename() + "' não é um PDF (apenas application/pdf é aceito).");
        }
    }

    private static String nomeSeguro(MultipartFile f) {
        String nome = f.getOriginalFilename();
        return (nome == null || nome.isBlank()) ? "documento.pdf" : nome;
    }

    private static byte[] ler(MultipartFile f) {
        try {
            return f.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler o arquivo " + f.getOriginalFilename(), e);
        }
    }
}
