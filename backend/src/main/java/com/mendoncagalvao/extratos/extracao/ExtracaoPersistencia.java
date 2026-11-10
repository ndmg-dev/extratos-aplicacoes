package com.mendoncagalvao.extratos.extracao;

import com.mendoncagalvao.extratos.domain.Documento;
import com.mendoncagalvao.extratos.domain.Lancamento;
import com.mendoncagalvao.extratos.repository.DocumentoRepository;
import com.mendoncagalvao.extratos.repository.LancamentoRepository;
import com.mendoncagalvao.extratos.validacao.model.LinhaLancamento;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistência transacional de um documento e seus lançamentos (upsert por {@code chave}).
 * Isolada do {@link ExtracaoService} para que a transação <b>não</b> permaneça aberta durante a
 * chamada (lenta) ao Claude.
 */
@Component
public class ExtracaoPersistencia {

    private final DocumentoRepository documentoRepo;
    private final LancamentoRepository lancamentoRepo;

    public ExtracaoPersistencia(DocumentoRepository documentoRepo, LancamentoRepository lancamentoRepo) {
        this.documentoRepo = documentoRepo;
        this.lancamentoRepo = lancamentoRepo;
    }

    @Transactional
    public ResultadoProcessamento persistir(Documento documento, List<LinhaLancamento> linhas) {
        Documento salvo = documentoRepo.save(documento);
        List<Lancamento> lancamentos = new ArrayList<>(linhas.size());
        for (LinhaLancamento linha : linhas) {
            lancamentos.add(lancamentoRepo.upsertPorChave(LancamentoMapper.toEntity(linha, salvo.getId())));
        }
        return new ResultadoProcessamento(salvo, lancamentos);
    }
}
