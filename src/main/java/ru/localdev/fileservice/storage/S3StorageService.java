package ru.localdev.fileservice.storage;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.localdev.fileservice.config.StorageProperties;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListPartsRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.util.Comparator;
import java.util.List;

/**
 * Тонкая обёртка над S3 (MinIO): multipart-загрузка, presigned URL частей,
 * завершение загрузки, чтение/запись объектов.
 */
@Service
public class S3StorageService {

    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);

    private final S3Client s3;
    private final S3Presigner presigner;
    private final StorageProperties props;

    public S3StorageService(S3Client s3, S3Presigner presigner, StorageProperties props) {
        this.s3 = s3;
        this.presigner = presigner;
        this.props = props;
    }

    @PostConstruct
    void ensureBucket() {
        try {
            s3.headBucket(b -> b.bucket(props.bucket()));
        } catch (Exception headFailed) {
            try {
                s3.createBucket(b -> b.bucket(props.bucket()));
                log.info("Создан бакет '{}'", props.bucket());
            } catch (Exception createFailed) {
                log.warn("Не удалось проверить/создать бакет '{}': {}",
                        props.bucket(), createFailed.getMessage());
            }
        }
    }

    /** Инициировать multipart-загрузку, вернуть uploadId. */
    public String createMultipartUpload(String key, String contentType) {
        log.debug("S3 createMultipartUpload: bucket={}, key={}, contentType={}", props.bucket(), key, contentType);
        CreateMultipartUploadRequest request = CreateMultipartUploadRequest.builder()
                .bucket(props.bucket())
                .key(key)
                .contentType(contentType)
                .build();
        String uploadId = s3.createMultipartUpload(request).uploadId();
        log.debug("S3 createMultipartUpload: получен uploadId для key={}", key);
        return uploadId;
    }

    /** Presigned URL для PUT одной части (partNumber начинается с 1). */
    public String presignUploadPart(String key, String uploadId, int partNumber) {
        // URL содержит подпись доступа — сам URL не логируем, только идентификаторы запроса.
        log.debug("S3 presignUploadPart: key={}, uploadId={}, partNumber={}, ttl={}",
                key, uploadId, partNumber, props.presignTtl());
        UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                .bucket(props.bucket())
                .key(key)
                .uploadId(uploadId)
                .partNumber(partNumber)
                .build();

        UploadPartPresignRequest presignRequest = UploadPartPresignRequest.builder()
                .signatureDuration(props.presignTtl())
                .uploadPartRequest(uploadPartRequest)
                .build();

        return presigner.presignUploadPart(presignRequest).url().toString();
    }

    /**
     * Завершить multipart-загрузку. ETag'и частей сервер получает сам через ListParts
     * (клиент их не передаёт — соответствует контракту).
     */
    public void completeMultipartUpload(String key, String uploadId) {
        log.debug("S3 completeMultipartUpload: key={}, uploadId={}, читаем части через ListParts", key, uploadId);
        ListPartsRequest listRequest = ListPartsRequest.builder()
                .bucket(props.bucket())
                .key(key)
                .uploadId(uploadId)
                .build();

        List<CompletedPart> completedParts = s3.listParts(listRequest).parts().stream()
                .sorted(Comparator.comparingInt(software.amazon.awssdk.services.s3.model.Part::partNumber))
                .map(p -> CompletedPart.builder()
                        .partNumber(p.partNumber())
                        .eTag(p.eTag())
                        .build())
                .toList();
        log.debug("S3 completeMultipartUpload: найдено частей={}, key={}", completedParts.size(), key);

        CompleteMultipartUploadRequest completeRequest = CompleteMultipartUploadRequest.builder()
                .bucket(props.bucket())
                .key(key)
                .uploadId(uploadId)
                .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build())
                .build();

        s3.completeMultipartUpload(completeRequest);
        log.debug("S3 completeMultipartUpload: завершено, key={}", key);
    }

    /** Скачать объект целиком (используется для ресайза). */
    public byte[] getObjectBytes(String key) {
        log.debug("S3 getObjectBytes: bucket={}, key={}", props.bucket(), key);
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(props.bucket())
                .key(key)
                .build();
        byte[] bytes = s3.getObjectAsBytes(request).asByteArray();
        log.debug("S3 getObjectBytes: прочитано {} байт, key={}", bytes.length, key);
        return bytes;
    }

    /** Записать объект целиком (используется для сохранения ресайзов). */
    public void putObject(String key, byte[] data, String contentType) {
        log.debug("S3 putObject: bucket={}, key={}, contentType={}, bytes={}",
                props.bucket(), key, contentType, data.length);
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(props.bucket())
                .key(key)
                .contentType(contentType)
                .build();
        s3.putObject(request, RequestBody.fromBytes(data));
        log.debug("S3 putObject: сохранено, key={}", key);
    }
}
