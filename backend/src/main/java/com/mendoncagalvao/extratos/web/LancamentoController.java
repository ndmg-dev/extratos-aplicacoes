package com.mendoncagalvao.extratos.web;

import com.mendoncagalvao.extratos.domain.Lancamento;
import com.mendoncagalvao.extratos.repository.LancamentoRepository;
import com.mendoncagalvao.extratos.web.dto.LancamentoDTO;
import com.mendoncagalvao.extratos.web.dto.ResumoDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** GET /api/lancamentos (lista paginada + filtros) e /summary (§7). */
@RestController
@RequestMapping("/api/lancamentos")
public class LancamentoController {

    private static final List<String> STATUSES = List.of("OK", "REVISAR", "SEM_DETALHE", "ERRO");

    private final LancamentoRepository repo;

    public LancamentoController(LancamentoRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public Page<LancamentoDTO> listar(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String banco,
            @RequestParam(required = false) String competencia,
            @RequestParam(required = false) String cnpj,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 50, sort = "processadoEm", direction = Sort.Direction.DESC) Pageable pageable) {
        Specification<Lancamento> spec = LancamentoSpecs.filtros(status, banco, competencia, cnpj, q);
        return repo.findAll(spec, pageable).map(DtoMapper::toDto);
    }

    @GetMapping("/summary")
    public ResumoDTO resumo(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String banco,
            @RequestParam(required = false) String competencia,
            @RequestParam(required = false) String cnpj,
            @RequestParam(required = false) String q) {
        Specification<Lancamento> spec = LancamentoSpecs.filtros(status, banco, competencia, cnpj, q);
        List<Lancamento> linhas = repo.findAll(spec);

        Map<String, Long> contagem = new LinkedHashMap<>();
        for (String s : STATUSES) {
            contagem.put(s, 0L);
        }
        Map<String, BigDecimal> somaPorComp = new TreeMap<>();
        for (Lancamento l : linhas) {
            contagem.merge(l.getStatus(), 1L, Long::sum);
            if (l.getCompetencia() != null && l.getSaldoFinalBruto() != null) {
                somaPorComp.merge(l.getCompetencia(), l.getSaldoFinalBruto(), BigDecimal::add);
            }
        }
        return new ResumoDTO(contagem, linhas.size(), somaPorComp);
    }
}
