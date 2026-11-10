package com.mendoncagalvao.extratos.web;

import com.mendoncagalvao.extratos.domain.Documento;
import com.mendoncagalvao.extratos.extracao.ExtracaoService;
import com.mendoncagalvao.extratos.extracao.ResultadoProcessamento;
import com.mendoncagalvao.extratos.repository.DocumentoRepository;
import com.mendoncagalvao.extratos.repository.LancamentoRepository;
import com.mendoncagalvao.extratos.storage.StorageService;
import com.mendoncagalvao.extratos.support.RecursoNaoEncontradoException;
import com.mendoncagalvao.extratos.web.dto.DocumentoDetalheDTO;
import com.mendoncagalvao.extratos.web.dto.LancamentoDTO;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** GET /api/documentos/{id}, /{id}/pdf e POST /{id}/reprocessar (§7). */
@RestController
@RequestMapping("/api/documentos")
public class DocumentoController {

    private final DocumentoRepository documentoRepo;
    private final LancamentoRepository lancamentoRepo;
    private final StorageService storageService;
    private final ExtracaoService extracaoService;

    public DocumentoController(DocumentoRepository documentoRepo,
                              LancamentoRepository lancamentoRepo,
                              StorageService storageService,
                              ExtracaoService extracaoService) {
        this.documentoRepo = documentoRepo;
        this.lancamentoRepo = lancamentoRepo;
        this.storageService = storageService;
        this.extracaoService = extracaoService;
    }

    @GetMapping("/{id}")
    public DocumentoDetalheDTO detalhe(@PathVariable UUID id,
                                       @RequestParam(name = "incluirRaw", defaultValue = "false") boolean incluirRaw) {
        Documento doc = documentoRepo.findById(id).orElseThrow(() -> naoEncontrado(id));
        List<LancamentoDTO> lancamentos = lancamentoRepo.findByDocumentoIdOrderByIdentificadorAsc(id).stream()
                .map(DtoMapper::toDto)
                .toList();
        return new DocumentoDetalheDTO(DtoMapper.toDto(doc, incluirRaw), lancamentos);
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> baixarPdf(@PathVariable UUID id) {
        Documento doc = documentoRepo.findById(id).orElseThrow(() -> naoEncontrado(id));
        if (doc.getStorageKey() == null) {
            throw new RecursoNaoEncontradoException("Documento " + id + " não possui PDF arquivado.");
        }
        byte[] pdf = storageService.carregar(doc.getStorageKey());
        String nome = doc.getNomePadronizado() != null ? doc.getNomePadronizado() : doc.getNomeArquivoOriginal();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(nome == null ? "documento.pdf" : nome).build().toString())
                .body(pdf);
    }

    @PostMapping("/{id}/reprocessar")
    public DocumentoDetalheDTO reprocessar(@PathVariable UUID id) {
        ResultadoProcessamento r = extracaoService.reprocessar(id);
        List<LancamentoDTO> lancamentos = r.lancamentos().stream().map(DtoMapper::toDto).toList();
        return new DocumentoDetalheDTO(DtoMapper.toDto(r.documento(), false), lancamentos);
    }

    private static RecursoNaoEncontradoException naoEncontrado(UUID id) {
        return new RecursoNaoEncontradoException("Documento não encontrado: " + id);
    }
}
