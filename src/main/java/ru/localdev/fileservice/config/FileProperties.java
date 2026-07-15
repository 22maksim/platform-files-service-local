package ru.localdev.fileservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.util.List;

/**
 * Настройки файлового флоу.
 *
 * @param minChunkSize    минимальный размер части multipart-загрузки (по умолчанию 5 MB)
 * @param allowedProducts список зарегистрированных продуктов (пусто = разрешить любой)
 */
@ConfigurationProperties("file")
public record FileProperties(
        DataSize minChunkSize,
        List<String> allowedProducts
) {
    public FileProperties {
        if (minChunkSize == null) {
            minChunkSize = DataSize.ofMegabytes(5);
        }
        if (allowedProducts == null) {
            allowedProducts = List.of();
        }
    }
}
