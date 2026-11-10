package com.mendoncagalvao.extratos.web;

import com.mendoncagalvao.extratos.domain.Lancamento;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** Specifications para os filtros da listagem (§7): status, banco, competencia, cnpj, q. */
public final class LancamentoSpecs {

    private LancamentoSpecs() {
    }

    public static Specification<Lancamento> filtros(String status, String banco, String competencia,
                                                    String cnpj, String q) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (temTexto(status)) {
                ps.add(cb.equal(root.get("status"), status.trim()));
            }
            if (temTexto(banco)) {
                ps.add(cb.equal(cb.lower(root.get("banco")), banco.trim().toLowerCase()));
            }
            if (temTexto(competencia)) {
                ps.add(cb.equal(root.get("competencia"), competencia.trim()));
            }
            if (temTexto(cnpj)) {
                ps.add(cb.equal(root.get("clienteCnpj"), apenasDigitos(cnpj)));
            }
            if (temTexto(q)) {
                String like = "%" + q.trim().toLowerCase() + "%";
                ps.add(cb.or(
                        cb.like(cb.lower(root.get("produto")), like),
                        cb.like(cb.lower(root.get("identificador")), like),
                        cb.like(cb.lower(root.get("clienteNome")), like)));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }

    private static boolean temTexto(String s) {
        return s != null && !s.isBlank();
    }

    private static String apenasDigitos(String s) {
        return s.replaceAll("\\D", "");
    }
}
