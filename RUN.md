# file-service-local

Локальный аналог `platform-files` — gRPC-сервер `file.v1.FileService` поверх MinIO,
для разработки сервисов-клиентов без доступа к настоящему (Go) микросервису.

Контракт берётся напрямую из submodule [api-schema](api-schema/proto/file/v1/api.proto)
и генерируется в Java при сборке. Реализованы все 4 RPC из proto:
`InitUpload`, `GetPresignedUploadPartURL`, `ConfirmMultipart`, `GetResizedImages`.

> Методов скачивания (`GetPresignedDownloadURL` и т.п.) в proto **нет** — клиенты качают
> файлы напрямую из MinIO. См. раздел про download в README схемы (там они описаны, но в
> контракте отсутствуют).

## Стек

Java 21 (toolchain) · Spring Boot 3.3 · net.devh grpc-server-spring-boot-starter ·
AWS SDK v2 (S3) · PostgreSQL (Hibernate `ddl-auto=update`) · Thumbnailator.

> Kafka пока не используется (нечего публиковать) — событие `FileUploaded` из proto
> доступно в сгенерированных классах, паблишер можно вернуть позже.

## 1. Поднять инфраструктуру

```bash
docker compose up -d
```

Поднимется только **MinIO** — S3 API `http://localhost:9000`, консоль `http://localhost:9001`
(minioadmin/minioadmin), бакет `files` создаётся автоматически.

**PostgreSQL** берётся локальный (не в compose): `localhost:5432`, `postgres/postgres`,
БД `postgres`. При необходимости поправь в [application.yml](src/main/resources/application.yml).

## 2. Запустить сервис

```bash
./gradlew bootRun
```

gRPC слушает `localhost:9090` (reflection включён). Hibernate создаст таблицу `files` на старте.

## 3. Проверка флоу (grpcurl + curl)

Полное имя сервиса — `com.platform.files.v1.FileService`. На каждый вызов обязателен
заголовок `product` (без него — `INVALID_ARGUMENT`).

### 3.1 InitUpload

```bash
grpcurl -plaintext -H "product: store" \
  -d '{"size": 11, "extension": "txt", "name": "hello.txt"}' \
  localhost:9090 com.platform.files.v1.FileService/InitUpload
```

Ответ содержит `fileId {high, low}` и массив `parts[].url` (presigned PUT).

### 3.2 Залить часть(и) напрямую в MinIO

```bash
# URL берётся из parts[0].url ответа InitUpload
curl -X PUT --data-binary "hello world" "<PRESIGNED_URL>"
```

### 3.3 ConfirmMultipart

```bash
grpcurl -plaintext -H "product: store" \
  -d '{"fileId": {"high": <HIGH>, "low": <LOW>}}' \
  localhost:9090 com.platform.files.v1.FileService/ConfirmMultipart
```

После этого:
- объект появляется в бакете `files` (проверить в консоли MinIO :9001);
- в таблице `files` статус становится `UPLOADED`.

### 3.4 GetResizedImages (для картинок)

```bash
grpcurl -plaintext -H "product: store" \
  -d '{"fileId": {"high": <HIGH>, "low": <LOW>}, "newSizes": [{"width": 128, "height": 128}]}' \
  localhost:9090 com.platform.files.v1.FileService/GetResizedImages
```

Вернёт `file_id` сохранённых вариантов (png), сами файлы — в бакете `files`.

## 4. Конфигурация

Всё в [application.yml](src/main/resources/application.yml): эндпоинт MinIO, креды, бакет,
размер части (`file.min-chunk-size`, по умолчанию 5MB), топик событий, список
зарегистрированных продуктов (`file.allowed-products`, по умолчанию включает `store`).

## 5. Java 25

Окружение собрано под установленный JDK 21. Для перехода на Java 25 поменяй
`JavaLanguageVersion.of(21)` → `of(25)` в [build.gradle](build.gradle) (нужен установленный JDK 25).
