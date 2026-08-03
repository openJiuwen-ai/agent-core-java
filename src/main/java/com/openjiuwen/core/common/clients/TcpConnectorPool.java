/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * TCP connector pool implementation.
 * <p>
 * Provides a pool of TCP connections for HTTP client reuse.
 * <p>
 * Mirrors Python's {@code TcpConnectorPool} in
 * {@code openjiuwen/core/common/clients/connector_pool.py}.
 */
public class TcpConnectorPool extends ConnectorPool {

    private final HttpClient client;

    public TcpConnectorPool(ConnectorPoolConfig config) {
        super(config);
        this.client = buildClient(config);
    }

    private HttpClient buildClient(ConnectorPoolConfig config) {
        HttpClient.Builder builder = HttpClient.newBuilder();
        builder.connectTimeout(Duration.ofSeconds(30));
        if (config.getSslCert() != null || !config.isSslVerify()) {
            Object sslContext = config.createSslContext();
            if (sslContext instanceof javax.net.ssl.SSLContext context) {
                builder.sslContext(context);
            }
        }
        return builder.build();
    }

    @Override
    public Object getConn() {
        return client;
    }

    @Override
    protected CompletableFuture<Void> doClose(Map<String, Object> kwargs) {
        // HttpClient doesn't need explicit closing
        return CompletableFuture.completedFuture(null);
    }
}
