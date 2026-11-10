package com.mendoncagalvao.extratos.extracao;

/**
 * Um arquivo recebido para processamento, com sua proveniência (upload web ou e-mail).
 *
 * @param nomeOriginal  nome do arquivo enviado
 * @param contentType   content-type informado (validado no controller)
 * @param conteudo      bytes do PDF
 * @param proveniencia  origem do arquivo (§13)
 */
public record ArquivoUpload(String nomeOriginal, String contentType, byte[] conteudo, Proveniencia proveniencia) {

    public ArquivoUpload {
        if (proveniencia == null) {
            proveniencia = Proveniencia.upload();
        }
    }

    /** Arquivo de upload manual pela interface web. */
    public static ArquivoUpload upload(String nomeOriginal, String contentType, byte[] conteudo) {
        return new ArquivoUpload(nomeOriginal, contentType, conteudo, Proveniencia.upload());
    }

    /** Arquivo vindo de um anexo de e-mail (§13). */
    public static ArquivoUpload email(String nomeOriginal, byte[] conteudo, Proveniencia proveniencia) {
        return new ArquivoUpload(nomeOriginal, "application/pdf", conteudo, proveniencia);
    }
}
