package com.mendoncagalvao.extratos.web.export;

import com.mendoncagalvao.extratos.domain.Lancamento;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;

/**
 * Exportação das linhas filtradas em CSV e XLSX, na <b>mesma ordem de colunas da §4.2</b>.
 * CSV usa {@code ;} como separador e vírgula decimal (Excel pt-BR), com BOM UTF-8.
 */
@Service
public class ExportService {

    /** Coluna de exportação: cabeçalho, extrator e se é valor monetário. */
    private record Coluna(String header, Function<Lancamento, Object> valor, boolean monetario) {
    }

    private static final List<Coluna> COLUNAS = List.of(
            new Coluna("chave", Lancamento::getChave, false),
            new Coluna("status", Lancamento::getStatus, false),
            new Coluna("motivos", Lancamento::getMotivos, false),
            new Coluna("competencia", Lancamento::getCompetencia, false),
            new Coluna("banco", Lancamento::getBanco, false),
            new Coluna("cliente_nome", Lancamento::getClienteNome, false),
            new Coluna("cliente_cnpj", Lancamento::getClienteCnpj, false),
            new Coluna("agencia", Lancamento::getAgencia, false),
            new Coluna("conta", Lancamento::getConta, false),
            new Coluna("identificador", Lancamento::getIdentificador, false),
            new Coluna("produto", Lancamento::getProduto, false),
            new Coluna("data_aplicacao", Lancamento::getDataAplicacao, false),
            new Coluna("data_vencimento", Lancamento::getDataVencimento, false),
            new Coluna("saldo_inicial_bruto", Lancamento::getSaldoInicialBruto, true),
            new Coluna("origem_saldo_inicial", Lancamento::getOrigemSaldoInicial, false),
            new Coluna("aplicacoes", Lancamento::getAplicacoes, true),
            new Coluna("resgates_brutos", Lancamento::getResgatesBrutos, true),
            new Coluna("rendimento_bruto", Lancamento::getRendimentoBruto, true),
            new Coluna("ir_retido", Lancamento::getIrRetido, true),
            new Coluna("iof_retido", Lancamento::getIofRetido, true),
            new Coluna("saldo_final_bruto", Lancamento::getSaldoFinalBruto, true),
            new Coluna("ir_provisionado", Lancamento::getIrProvisionado, true),
            new Coluna("iof_provisionado", Lancamento::getIofProvisionado, true),
            new Coluna("saldo_final_liquido", Lancamento::getSaldoFinalLiquido, true),
            new Coluna("diferenca_fechamento", Lancamento::getDiferencaFechamento, true),
            new Coluna("arquivo", Lancamento::getArquivo, false),
            new Coluna("email_id", Lancamento::getEmailId, false),
            new Coluna("processado_em", Lancamento::getProcessadoEm, false),
            new Coluna("documento_id", Lancamento::getDocumentoId, false)
    );

    // ---- CSV ----------------------------------------------------------------------------

    public byte[] csv(List<Lancamento> linhas) {
        StringBuilder sb = new StringBuilder();
        sb.append('﻿'); // BOM UTF-8 para o Excel pt-BR reconhecer acentos
        sb.append(cabecalhoCsv()).append("\r\n");
        for (Lancamento l : linhas) {
            StringBuilder linha = new StringBuilder();
            for (int c = 0; c < COLUNAS.size(); c++) {
                if (c > 0) {
                    linha.append(';');
                }
                linha.append(campoCsv(COLUNAS.get(c), l));
            }
            sb.append(linha).append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String cabecalhoCsv() {
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c < COLUNAS.size(); c++) {
            if (c > 0) {
                sb.append(';');
            }
            sb.append(COLUNAS.get(c).header());
        }
        return sb.toString();
    }

    private static String campoCsv(Coluna col, Lancamento l) {
        Object v = col.valor().apply(l);
        if (v == null) {
            return "";
        }
        String s;
        if (col.monetario() && v instanceof BigDecimal bd) {
            s = bd.toPlainString().replace('.', ','); // vírgula decimal (pt-BR)
        } else if (v instanceof Instant instant) {
            s = instant.toString();
        } else {
            s = v.toString();
        }
        return escaparCsv(s);
    }

    private static String escaparCsv(String s) {
        if (s.contains(";") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    // ---- XLSX ---------------------------------------------------------------------------

    public byte[] xlsx(List<Lancamento> linhas) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("lancamentos");

            Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFont(bold);

            DataFormat df = wb.createDataFormat();
            CellStyle moneyStyle = wb.createCellStyle();
            moneyStyle.setDataFormat(df.getFormat("#,##0.00"));

            Row header = sheet.createRow(0);
            for (int c = 0; c < COLUNAS.size(); c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(COLUNAS.get(c).header());
                cell.setCellStyle(headerStyle);
            }

            int r = 1;
            for (Lancamento l : linhas) {
                Row row = sheet.createRow(r++);
                for (int c = 0; c < COLUNAS.size(); c++) {
                    Coluna col = COLUNAS.get(c);
                    Cell cell = row.createCell(c);
                    Object v = col.valor().apply(l);
                    if (v == null) {
                        continue;
                    }
                    if (col.monetario() && v instanceof BigDecimal bd) {
                        cell.setCellValue(bd.doubleValue());
                        cell.setCellStyle(moneyStyle);
                    } else if (v instanceof Instant instant) {
                        cell.setCellValue(instant.toString());
                    } else {
                        cell.setCellValue(v.toString());
                    }
                }
            }

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gerar XLSX", e);
        }
    }
}
