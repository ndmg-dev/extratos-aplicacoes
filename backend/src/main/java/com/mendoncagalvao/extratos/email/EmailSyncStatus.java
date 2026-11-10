package com.mendoncagalvao.extratos.email;

import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Estado da última sincronização de e-mail (em memória), para o indicador na interface (§13).
 */
@Component
public class EmailSyncStatus {

    private volatile Instant ultimaExecucao;
    private volatile Instant emExecucaoDesde;
    private volatile int novosUltimaExecucao;
    private volatile int documentosUltimaExecucao;
    private volatile long totalMensagensProcessadas;
    private volatile String ultimoErro;

    public synchronized void iniciar() {
        emExecucaoDesde = Instant.now();
    }

    public synchronized void concluir(int novos, int documentos, String erro) {
        ultimaExecucao = Instant.now();
        emExecucaoDesde = null;
        novosUltimaExecucao = novos;
        documentosUltimaExecucao = documentos;
        totalMensagensProcessadas += novos;
        ultimoErro = erro;
    }

    public Instant getUltimaExecucao() { return ultimaExecucao; }
    public boolean isEmExecucao() { return emExecucaoDesde != null; }
    public int getNovosUltimaExecucao() { return novosUltimaExecucao; }
    public int getDocumentosUltimaExecucao() { return documentosUltimaExecucao; }
    public long getTotalMensagensProcessadas() { return totalMensagensProcessadas; }
    public String getUltimoErro() { return ultimoErro; }
}
