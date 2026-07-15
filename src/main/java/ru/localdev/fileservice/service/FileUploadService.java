package ru.localdev.fileservice.service;

import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.localdev.fileservice.config.FileProperties;
import ru.localdev.fileservice.config.StorageProperties;
import ru.localdev.fileservice.domain.FileEntity;
import ru.localdev.fileservice.domain.FileRepository;
import ru.localdev.fileservice.domain.FileStatus;
import ru.localdev.fileservice.grpc.RequestContext;
import ru.localdev.fileservice.mapper.UuidMapper;
import ru.localdev.fileservice.storage.S3StorageService;
import ru.sbercom.platform.files.Api.ConfirmMultipartRequest;
import ru.sbercom.platform.files.Api.ConfirmMultipartResponse;
import ru.sbercom.platform.files.Api.GetPresignedUploadPartURLRequest;
import ru.sbercom.platform.files.Api.GetPresignedUploadPartURLResponse;
import ru.sbercom.platform.files.Api.InitUploadPartInfo;
import ru.sbercom.platform.files.Api.InitUploadRequest;
import ru.sbercom.platform.files.Api.InitUploadResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Оркестрация upload-флоу: InitUpload -> (presigned PUT частей клиентом) -> ConfirmMultipart.
 */
@Service
public class FileUploadService {

    private static final Logger log = LoggerFactory.getLogger(FileUploadService.class);

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final S3StorageService storage;
    private final FileRepository repository;
    private final FileProperties fileProps;
    private final StorageProperties storageProps;

    public FileUploadService(S3StorageService storage,
                             FileRepository repository,
                             FileProperties fileProps,
                             StorageProperties storageProps) {
        this.storage = storage;
        this.repository = repository;
        this.fileProps = fileProps;
        this.storageProps = storageProps;
    }

    @Transactional
    public InitUploadResponse initUpload(InitUploadRequest request, String product) {
        validateProductRegistered(product);

        long size = request.getSize();
        String extension = request.getExtension();
        UUID fileId = UUID.randomUUID();
        String key = buildKey(product, fileId, extension);
        log.debug("initUpload: сгенерирован fileId={}, s3Key={}", fileId, key);

        long chunk = fileProps.minChunkSize().toBytes();
        int partCount = size <= 0 ? 1 : (int) ((size + chunk - 1) / chunk);
        log.debug("initUpload: fileId={}, size={}, chunkSize={}, partCount={}", fileId, size, chunk, partCount);

        String uploadId = storage.createMultipartUpload(key, DEFAULT_CONTENT_TYPE);
        log.debug("initUpload: multipart upload создан, fileId={}, uploadId={}", fileId, uploadId);

        List<InitUploadPartInfo> parts = new ArrayList<>(partCount);
        long remaining = size;
        for (int i = 0; i < partCount; i++) {
            long partSize = Math.min(chunk, Math.max(remaining, 0));
            String url = storage.presignUploadPart(key, uploadId, i + 1);
            parts.add(InitUploadPartInfo.newBuilder()
                    .setPartIndex(i)
                    .setSizeBytes(partSize)
                    .setUrl(url)
                    .build());
            remaining -= partSize;
        }
        log.debug("initUpload: сгенерировано {} presigned URL для частей, fileId={}", partCount, fileId);

        FileEntity entity = new FileEntity();
        entity.setFileId(fileId);
        entity.setName(request.getName());
        entity.setExtension(extension);
        entity.setSize(size);
        entity.setCheckSum(emptyToNull(request.getCheckSum()));
        entity.setProductName(product);
        entity.setStatus(FileStatus.INITIALIZED);
        entity.setBucket(storageProps.bucket());
        entity.setS3Key(key);
        entity.setMultipartUploadId(uploadId);
        entity.setPartCount(partCount);
        entity.setUploaderUserId(RequestContext.userId());
        entity.setInitializedAt(Instant.now());
        repository.save(entity);

        log.info("Загрузка инициализирована: fileId={}, product={}, name={}, size={}, partCount={}",
                fileId, product, request.getName(), size, partCount);

        return InitUploadResponse.newBuilder()
                .setFileId(UuidMapper.toProto(fileId))
                .setPartCount(partCount)
                .addAllParts(parts)
                .build();
    }

    @Transactional(readOnly = true)
    public GetPresignedUploadPartURLResponse getPresignedUploadPartURL(
            GetPresignedUploadPartURLRequest request, String product) {

        UUID fileId = UuidMapper.toJava(request.getFileId());
        FileEntity entity = findOwned(fileId, product);
        log.debug("getPresignedUploadPartURL: fileId={}, partIndex={}, uploadId={}",
                fileId, request.getPartIndex(), entity.getMultipartUploadId());
        String url = storage.presignUploadPart(
                entity.getS3Key(), entity.getMultipartUploadId(), request.getPartIndex() + 1);
        return GetPresignedUploadPartURLResponse.newBuilder().setUrl(url).build();
    }

    @Transactional
    public ConfirmMultipartResponse confirmMultipart(ConfirmMultipartRequest request, String product) {
        UUID fileId = UuidMapper.toJava(request.getFileId());
        FileEntity entity = findOwned(fileId, product);
        log.debug("confirmMultipart: завершаем multipart upload, fileId={}, uploadId={}",
                fileId, entity.getMultipartUploadId());

        storage.completeMultipartUpload(entity.getS3Key(), entity.getMultipartUploadId());
        log.debug("confirmMultipart: multipart upload завершён в S3, fileId={}", fileId);

        if (request.getCheckSum() != null && !request.getCheckSum().isBlank()) {
            entity.setCheckSum(request.getCheckSum());
        }
        entity.setStatus(FileStatus.UPLOADED);
        entity.setCreatedAt(Instant.now());
        repository.save(entity);

        log.info("Файл загружен: fileId={}, product={}, name={}, size={}",
                fileId, product, entity.getName(), entity.getSize());

        return ConfirmMultipartResponse.getDefaultInstance();
    }

    private FileEntity findOwned(UUID fileId, String product) {
        FileEntity entity = repository.findById(fileId)
                .orElseThrow(() -> {
                    log.warn("Файл не найден: fileId={}", fileId);
                    return Status.NOT_FOUND
                            .withDescription("file not found: " + fileId)
                            .asRuntimeException();
                });
        if (!entity.getProductName().equals(product)) {
            log.warn("Отказано в доступе: fileId={} принадлежит product={}, запрошено product={}",
                    fileId, entity.getProductName(), product);
            throw Status.PERMISSION_DENIED
                    .withDescription("file does not belong to product '" + product + "'")
                    .asRuntimeException();
        }
        return entity;
    }

    private void validateProductRegistered(String product) {
        List<String> allowed = fileProps.allowedProducts();
        if (!allowed.isEmpty() && !allowed.contains(product)) {
            log.warn("Продукт '{}' не зарегистрирован, разрешены: {}", product, allowed);
            throw Status.PERMISSION_DENIED
                    .withDescription("product '" + product + "' is not registered")
                    .asRuntimeException();
        }
    }

    private static String buildKey(String product, UUID fileId, String extension) {
        String suffix = (extension == null || extension.isBlank()) ? "" : "." + extension;
        return product + "/" + fileId + suffix;
    }

    private static String emptyToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
