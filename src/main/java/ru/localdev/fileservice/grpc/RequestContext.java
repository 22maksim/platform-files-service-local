package ru.localdev.fileservice.grpc;

import io.grpc.Context;

/**
 * Значения, прокинутые из gRPC metadata в текущий {@link io.grpc.Context}.
 */
public final class RequestContext {

    public static final Context.Key<String> PRODUCT = Context.key("product");
    public static final Context.Key<String> USER_ID = Context.key("x-user-id");
    public static final Context.Key<String> TRACE_ID = Context.key("x-trace-id");

    private RequestContext() {
    }

    public static String product() {
        return PRODUCT.get();
    }

    public static String userId() {
        return USER_ID.get();
    }

    public static String traceId() {
        return TRACE_ID.get();
    }
}
