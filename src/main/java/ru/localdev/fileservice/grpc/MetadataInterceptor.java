package ru.localdev.fileservice.grpc;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Читает обязательный заголовок {@code product} и опциональные
 * {@code x-user-id} / {@code x-trace-id}. Без {@code product} — INVALID_ARGUMENT.
 * Если {@code x-trace-id} не передан, генерирует requestId для корреляции логов вызова.
 */
public class MetadataInterceptor implements ServerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(MetadataInterceptor.class);

    private static final Metadata.Key<String> PRODUCT_KEY =
            Metadata.Key.of("product", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> USER_KEY =
            Metadata.Key.of("x-user-id", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> TRACE_KEY =
            Metadata.Key.of("x-trace-id", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String method = call.getMethodDescriptor().getFullMethodName();
        String product = headers.get(PRODUCT_KEY);
        if (product == null || product.isBlank()) {
            log.warn("Отклонён вызов {}: отсутствует обязательный заголовок 'product'", method);
            call.close(
                    Status.INVALID_ARGUMENT.withDescription("missing required 'product' metadata"),
                    new Metadata());
            return new ServerCall.Listener<>() {
            };
        }

        String userId = headers.get(USER_KEY);
        String traceId = headers.get(TRACE_KEY);
        String requestId = (traceId == null || traceId.isBlank()) ? UUID.randomUUID().toString() : traceId;

        log.debug("-> {} product={} userId={} requestId={}", method, product, userId, requestId);

        Context ctx = Context.current()
                .withValue(RequestContext.PRODUCT, product)
                .withValue(RequestContext.USER_ID, userId)
                .withValue(RequestContext.TRACE_ID, requestId);

        return Contexts.interceptCall(ctx, call, headers, next);
    }
}
