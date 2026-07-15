package ru.localdev.fileservice.config;

import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.context.annotation.Configuration;
import ru.localdev.fileservice.grpc.MetadataInterceptor;

/**
 * Регистрация глобального серверного интерцептора, читающего metadata
 * (product / x-user-id / x-trace-id) на каждый gRPC-вызов.
 */
@Configuration
public class GrpcConfig {

    @GrpcGlobalServerInterceptor
    MetadataInterceptor metadataInterceptor() {
        return new MetadataInterceptor();
    }
}
