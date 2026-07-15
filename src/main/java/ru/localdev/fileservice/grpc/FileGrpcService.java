package ru.localdev.fileservice.grpc;

import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import ru.localdev.fileservice.mapper.UuidMapper;
import ru.localdev.fileservice.service.FileUploadService;
import ru.localdev.fileservice.service.ImageResizeService;
import ru.sbercom.platform.files.Api.ConfirmMultipartRequest;
import ru.sbercom.platform.files.Api.ConfirmMultipartResponse;
import ru.sbercom.platform.files.Api.GetPresignedUploadPartURLRequest;
import ru.sbercom.platform.files.Api.GetPresignedUploadPartURLResponse;
import ru.sbercom.platform.files.Api.GetResizedImagesRequest;
import ru.sbercom.platform.files.Api.GetResizedImagesResponse;
import ru.sbercom.platform.files.Api.InitUploadRequest;
import ru.sbercom.platform.files.Api.InitUploadResponse;
import ru.sbercom.platform.files.FileServiceGrpc;

/**
 * gRPC-реализация file.v1.FileService (серверная сторона аналога platform-files).
 * Исключения {@link io.grpc.StatusRuntimeException} из сервисов автоматически
 * транслируются gRPC-рантаймом в соответствующие статусы.
 */
@GrpcService
public class FileGrpcService extends FileServiceGrpc.FileServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(FileGrpcService.class);

    private final FileUploadService uploadService;
    private final ImageResizeService resizeService;

    public FileGrpcService(FileUploadService uploadService, ImageResizeService resizeService) {
        this.uploadService = uploadService;
        this.resizeService = resizeService;
    }

    @Override
    public void initUpload(InitUploadRequest request,
                           StreamObserver<InitUploadResponse> responseObserver) {
        putMdc();
        try {
            log.debug("initUpload: name={}, size={}, extension={}",
                    request.getName(), request.getSize(), request.getExtension());
            InitUploadResponse response = uploadService.initUpload(request, RequestContext.product());
            log.debug("initUpload OK: partCount={}", response.getPartCount());
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (StatusRuntimeException e) {
            log.warn("initUpload failed: {}", e.getStatus());
            throw e;
        } finally {
            MDC.clear();
        }
    }

    @Override
    public void getPresignedUploadPartURL(GetPresignedUploadPartURLRequest request,
                                          StreamObserver<GetPresignedUploadPartURLResponse> responseObserver) {
        putMdc();
        try {
            log.debug("getPresignedUploadPartURL: fileId={}, partIndex={}",
                    UuidMapper.toJava(request.getFileId()), request.getPartIndex());
            GetPresignedUploadPartURLResponse response =
                    uploadService.getPresignedUploadPartURL(request, RequestContext.product());
            log.debug("getPresignedUploadPartURL OK");
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (StatusRuntimeException e) {
            log.warn("getPresignedUploadPartURL failed: {}", e.getStatus());
            throw e;
        } finally {
            MDC.clear();
        }
    }

    @Override
    public void confirmMultipart(ConfirmMultipartRequest request,
                                 StreamObserver<ConfirmMultipartResponse> responseObserver) {
        putMdc();
        try {
            log.debug("confirmMultipart: fileId={}", UuidMapper.toJava(request.getFileId()));
            ConfirmMultipartResponse response = uploadService.confirmMultipart(request, RequestContext.product());
            log.debug("confirmMultipart OK: fileId={}", UuidMapper.toJava(request.getFileId()));
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (StatusRuntimeException e) {
            log.warn("confirmMultipart failed: fileId={}, status={}",
                    UuidMapper.toJava(request.getFileId()), e.getStatus());
            throw e;
        } finally {
            MDC.clear();
        }
    }

    @Override
    public void getResizedImages(GetResizedImagesRequest request,
                                 StreamObserver<GetResizedImagesResponse> responseObserver) {
        putMdc();
        try {
            log.debug("getResizedImages: fileId={}, sizes={}",
                    UuidMapper.toJava(request.getFileId()), request.getNewSizesList().size());
            GetResizedImagesResponse response = resizeService.getResizedImages(request, RequestContext.product());
            log.debug("getResizedImages OK: resizedCount={}", response.getResizedImagesCount());
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (StatusRuntimeException e) {
            log.warn("getResizedImages failed: fileId={}, status={}",
                    UuidMapper.toJava(request.getFileId()), e.getStatus());
            throw e;
        } finally {
            MDC.clear();
        }
    }

    private static void putMdc() {
        MDC.put("requestId", RequestContext.traceId());
        MDC.put("product", RequestContext.product());
        MDC.put("userId", RequestContext.userId());
    }
}
