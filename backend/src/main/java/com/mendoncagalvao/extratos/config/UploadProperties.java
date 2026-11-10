package com.mendoncagalvao.extratos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Limites de upload (§7/§11). */
@ConfigurationProperties(prefix = "upload")
public record UploadProperties(
        @DefaultValue("20") int maxFileMb
) {
    public long maxFileBytes() {
        return (long) maxFileMb * 1024 * 1024;
    }
}
