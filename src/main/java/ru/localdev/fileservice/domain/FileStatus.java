package ru.localdev.fileservice.domain;

public enum FileStatus {
    /** Загрузка инициализирована, multipart создан, части ещё не подтверждены. */
    INITIALIZED,
    /** Multipart завершён, файл лежит в S3 целиком. */
    UPLOADED
}
