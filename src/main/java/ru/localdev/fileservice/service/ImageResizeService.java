package ru.localdev.fileservice.service;

import io.grpc.Status;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.localdev.fileservice.config.StorageProperties;
import ru.localdev.fileservice.domain.FileEntity;
import ru.localdev.fileservice.domain.FileRepository;
import ru.localdev.fileservice.domain.FileStatus;
import ru.localdev.fileservice.grpc.RequestContext;
import ru.localdev.fileservice.mapper.UuidMapper;
import ru.localdev.fileservice.storage.S3StorageService;
import ru.sbercom.platform.files.Api.GetResizedImagesRequest;
import ru.sbercom.platform.files.Api.GetResizedImagesResponse;
import ru.sbercom.platform.files.Api.Image;
import ru.sbercom.platform.files.Api.ResizeImageRequest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Полноценный ресайз: скачать оригинал из S3, отресайзить под каждый запрошенный
 * размер, сохранить варианты как новые файлы и вернуть их file_id.
 */
@Service
public class ImageResizeService {

    private static final Logger log = LoggerFactory.getLogger(ImageResizeService.class);

    private static final String OUTPUT_FORMAT = "png";
    private static final String OUTPUT_CONTENT_TYPE = "image/png";

    private final S3StorageService storage;
    private final FileRepository repository;
    private final StorageProperties storageProps;

    public ImageResizeService(S3StorageService storage,
                              FileRepository repository,
                              StorageProperties storageProps) {
        this.storage = storage;
        this.repository = repository;
        this.storageProps = storageProps;
    }

    @Transactional
    public GetResizedImagesResponse getResizedImages(GetResizedImagesRequest request, String product) {
        UUID originalId = UuidMapper.toJava(request.getFileId());
        log.debug("getResizedImages: originalId={}, запрошено размеров={}",
                originalId, request.getNewSizesList().size());

        FileEntity original = repository.findById(originalId)
                .orElseThrow(() -> {
                    log.warn("Файл не найден: fileId={}", originalId);
                    return Status.NOT_FOUND
                            .withDescription("file not found: " + originalId)
                            .asRuntimeException();
                });
        if (!original.getProductName().equals(product)) {
            log.warn("Отказано в доступе: fileId={} принадлежит product={}, запрошено product={}",
                    originalId, original.getProductName(), product);
            throw Status.PERMISSION_DENIED
                    .withDescription("file does not belong to product '" + product + "'")
                    .asRuntimeException();
        }

        byte[] originalBytes = storage.getObjectBytes(original.getS3Key());
        BufferedImage source = readImage(originalBytes);
        log.debug("getResizedImages: оригинал прочитан, fileId={}, {}x{}",
                originalId, source.getWidth(), source.getHeight());

        List<Image> resized = new ArrayList<>(request.getNewSizesList().size());
        for (ResizeImageRequest size : request.getNewSizesList()) {
            int width = size.getWidth();
            int height = size.getHeight();
            byte[] data = resize(source, width, height);

            UUID newId = UUID.randomUUID();
            String key = product + "/" + newId + "." + OUTPUT_FORMAT;
            storage.putObject(key, data, OUTPUT_CONTENT_TYPE);
            log.debug("getResizedImages: сохранён вариант {}x{}, newFileId={}, bytes={}",
                    width, height, newId, data.length);

            FileEntity variant = new FileEntity();
            variant.setFileId(newId);
            variant.setName(width + "x" + height + "_" + nullSafe(original.getName()));
            variant.setExtension(OUTPUT_FORMAT);
            variant.setSize(data.length);
            variant.setProductName(product);
            variant.setStatus(FileStatus.UPLOADED);
            variant.setBucket(storageProps.bucket());
            variant.setS3Key(key);
            variant.setPartCount(0);
            variant.setUploaderUserId(RequestContext.userId());
            variant.setInitializedAt(Instant.now());
            variant.setCreatedAt(Instant.now());
            repository.save(variant);

            resized.add(Image.newBuilder()
                    .setFileId(UuidMapper.toProto(newId))
                    .setWidth(width)
                    .setHeight(height)
                    .build());
        }

        log.info("Ресайз завершён: originalId={}, product={}, вариантов={}",
                originalId, product, resized.size());

        return GetResizedImagesResponse.newBuilder()
                .addAllResizedImages(resized)
                .build();
    }

    private static BufferedImage readImage(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw Status.INVALID_ARGUMENT
                        .withDescription("file is not a supported image")
                        .asRuntimeException();
            }
            return image;
        } catch (IOException e) {
            throw Status.INTERNAL.withDescription("failed to read image").withCause(e).asRuntimeException();
        }
    }

    private static byte[] resize(BufferedImage source, int width, int height) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Thumbnails.of(source)
                    .size(width, height)
                    .outputFormat(OUTPUT_FORMAT)
                    .toOutputStream(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw Status.INTERNAL.withDescription("failed to resize image").withCause(e).asRuntimeException();
        }
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
