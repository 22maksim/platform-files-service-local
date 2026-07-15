package ru.localdev.fileservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Настройки доступа к S3/MinIO.
 */
@ConfigurationProperties("storage")
public record StorageProperties(
        String endpoint,
        String region,
        String accessKey,
        String secretKey,
        String bucket,
        Duration presignTtl
) {
    public StorageProperties {
        if (region == null || region.isBlank()) {
            region = "us-east-1";
        }
        if (presignTtl == null) {
            presignTtl = Duration.ofMinutes(15);
        }
    }
}
