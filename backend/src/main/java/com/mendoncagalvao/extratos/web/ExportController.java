package com.mendoncagalvao.extratos.web;

import com.mendoncagalvao.extratos.domain.Lancamento;
import com.mendoncagalvao.extratos.repository.LancamentoRepository;
import com.mendoncagalvao.extratos.web.export.ExportService;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** GET /api/lancamentos/export?format=csv|xlsx — exporta as linhas filtradas (§7). */
@RestController
@RequestMapping("/api/lancamentos/export")
public class ExportController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final LancamentoRepository repo;
    private final ExportService exportService;

    public ExportController(LancamentoRepository repo, ExportService exportService) {
        this.repo = repo;
        this.exportService = exportService;
    }

    @GetMapping
    public ResponseEntity<byte[]> exportar(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String banco,
            @RequestParam(required = false) String competencia,
            @RequestParam(required = false) String cnpj,
            @RequestParam(required = false) String q) {

        Specification<Lancamento> spec = LancamentoSpecs.filtros(status, banco, competencia, cnpj, q);
        List<Lancamento> linhas = repo.findAll(spec,
                Sort.by("competencia", "banco", "clienteCnpj", "identificador"));

        String fmt = format == null ? "csv" : format.trim().toLowerCase();
        byte[] corpo;
        MediaType tipo;
        String nome;
        switch (fmt) {
            case "xlsx" -> {
                corpo = exportService.xlsx(linhas);
                tipo = XLSX;
                nome = "lancamentos.xlsx";
            }
            case "csv" -> {
                corpo = exportService.csv(linhas);
                tipo = new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8);
                nome = "lancamentos.csv";
            }
            default -> throw new IllegalArgumentException("Formato inválido: " + format + " (use csv ou xlsx).");
        }

        return ResponseEntity.ok()
                .contentType(tipo)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(nome).build().toString())
                .body(corpo);
    }
}
