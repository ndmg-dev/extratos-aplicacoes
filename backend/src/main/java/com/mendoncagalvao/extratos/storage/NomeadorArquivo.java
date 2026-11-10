package com.mendoncagalvao.extratos.storage;

import com.mendoncagalvao.extratos.validacao.model.ExtratoExtraido;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nomeação e organização do PDF no storage — porte <b>verbatim</b> do nó "Preparar documento"
 * (Apêndice D) e da organização sugerida na §9.
 *
 * <ul>
 *   <li>{@code banco} em maiúsculas, sem acentos, não-alfanumérico vira {@code _} (bordas removidas).</li>
 *   <li>{@code competencia} = {@code AAAA-MM} do extrato; se ausente, usa ano/mês da data de upload.</li>
 *   <li>{@code conta} = só dígitos.</li>
 *   <li>Nome: {@code BANCO_AAAA-MM_CONTA.pdf} (campos vazios omitidos); se a leitura falhou, usa o nome original.</li>
 *   <li>Organização: {@code cnpj|_SEM_CNPJ/ano/mes/aplicacoes/<nome>}.</li>
 * </ul>
 */
@Component
public class NomeadorArquivo {

    private static final Pattern COMPETENCIA = Pattern.compile("^(\\d{4})-(\\d{2})$");
    private static final Pattern MARCAS = Pattern.compile("\\p{M}+");
    private static final Pattern NAO_ALNUM = Pattern.compile("[^A-Z0-9]+");
    private static final Pattern BORDA_UNDERSCORE = Pattern.compile("^_|_$");
    /** Sanitização do nome original para evitar path traversal / caracteres inseguros (§11). */
    private static final Pattern NOME_INSEGURO = Pattern.compile("[^A-Za-z0-9._-]+");

    /**
     * @param d            dados extraídos (pode ser {@code null} quando a leitura falhou)
     * @param nomeOriginal nome do arquivo enviado
     * @param dataUpload   data do upload (fallback para ano/mês quando não há competência)
     */
    public ResultadoNomeacao nomear(ExtratoExtraido d, String nomeOriginal, LocalDate dataUpload) {
        final String cnpj = digitos(d == null ? null : d.clienteCnpj);
        final String competencia = d == null ? null : d.competencia;
        final Matcher comp = COMPETENCIA.matcher(competencia == null ? "" : competencia);
        final boolean compOk = comp.matches();

        final String ano;
        final String mes;
        if (compOk) {
            ano = comp.group(1);
            mes = comp.group(2);
        } else {
            ano = String.valueOf(dataUpload.getYear());
            mes = String.valueOf(dataUpload.getMonthValue()); // igual a String(dt.getMonth()+1) — sem zero à esquerda
        }

        final String banco = normalizarBanco(d == null ? null : d.banco);
        final String conta = digitos(d == null ? null : d.conta);

        final String nomeSaneado = sanitizarNomeOriginal(nomeOriginal);
        final String nome;
        if (!banco.isEmpty() && compOk) {
            List<String> partes = new ArrayList<>();
            partes.add(banco);
            partes.add(competencia);
            if (!conta.isEmpty()) {
                partes.add(conta);
            }
            nome = String.join("_", partes) + ".pdf";
        } else {
            nome = nomeSaneado.isEmpty() ? "documento.pdf" : nomeSaneado;
        }

        final boolean semCnpj = cnpj.length() != 14;
        final String prefixo = semCnpj ? "_SEM_CNPJ" : cnpj;
        final String storageKey = String.join("/", prefixo, ano, mes, "aplicacoes", nome);

        return new ResultadoNomeacao(nome, storageKey, semCnpj);
    }

    private static String normalizarBanco(String banco) {
        if (banco == null) {
            return "";
        }
        String s = banco.toUpperCase();
        s = Normalizer.normalize(s, Normalizer.Form.NFD);
        s = MARCAS.matcher(s).replaceAll("");
        s = NAO_ALNUM.matcher(s).replaceAll("_");
        s = BORDA_UNDERSCORE.matcher(s).replaceAll(""); // global: remove underscore inicial e final (igual ao /g do JS)
        return s;
    }

    private static String sanitizarNomeOriginal(String nomeOriginal) {
        if (nomeOriginal == null) {
            return "";
        }
        // só o nome base (remove qualquer caminho) e caracteres inseguros
        String base = nomeOriginal.replace('\\', '/');
        int barra = base.lastIndexOf('/');
        if (barra >= 0) {
            base = base.substring(barra + 1);
        }
        base = NOME_INSEGURO.matcher(base).replaceAll("_");
        base = BORDA_UNDERSCORE.matcher(base).replaceAll("");
        return base;
    }

    private static String digitos(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }
}
