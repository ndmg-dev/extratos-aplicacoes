package com.mendoncagalvao.extratos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuração de storage (§11).
 *
 * @param type      {@code fs} (filesystem, default) ou {@code s3} (MinIO/S3)
 * @param basePath  diretório base para o {@code FilesystemStorage}
 * @param bucket    bucket S3/MinIO (quando {@code type=s3})
 * @param endpoint  endpoint S3/MinIO (ex.: {@code http://localhost:9000} para MinIO)
 * @param region    região (default {@code us-east-1})
 * @param accessKey credencial de acesso (MinIO/S3)
 * @param secretKey credencial secreta (MinIO/S3)
 * @param pathStyle usar path-style access (necessário para MinIO; default {@code true})
 */
@ConfigurationProperties(prefix = "storage")
public record StorageProperties(
        @DefaultValue("fs") String type,
        @DefaultValue("data/arquivos") String basePath,
        String bucket,
        String endpoint,
        @DefaultValue("us-east-1") String region,
        String accessKey,
        String secretKey,
        @DefaultValue("true") boolean pathStyle
) {
}
