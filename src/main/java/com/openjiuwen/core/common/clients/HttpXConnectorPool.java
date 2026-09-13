/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * HTTPX-style connector pool backed by JDK HttpClient.
 * 
 * @since 0.1.7
 */
public class HttpXConnectorPool extends ConnectorPool {
    private final HttpClient client;

    /**
     * HttpXConnectorPool.
     * 
     * @param config config
     * @since 0.1.7
     */
    public HttpXConnectorPool(HttpXConnectorPoolConfig config) {
        super(config);
        HttpClient.Builder builder =
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).version(HttpClient.Version.HTTP_1_1);
        if (config.getKeepaliveTimeout() != null) {
            builder.connectTimeout(Duration.ofMillis(Math.max(1L, Math.round(config.getKeepaliveTimeout() * 1000))));
        }
        if (config.isSslVerify()) {
            if (config.getSslCert() != null && !config.getSslCert().isBlank()) {
                builder.sslContext(config.createSslContext());
            }
        } else {
            builder.sslContext(config.createSslContext());
        }
        if (config.getProxy() != null && !config.getProxy().isBlank()) {
            URI proxyUri = URI.create(config.getProxy());
            builder.proxy(ProxySelector.of(new InetSocketAddress(proxyUri.getHost(), proxyUri.getPort())));
        }
        this.client = builder.build();
    }

    /**
     * doClose.
     * 
     * @since 0.1.7
     */
    @Override
    protected void doClose() {
        // JDK HttpClient does not expose an explicit close hook.
    }

    /**
     * conn.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public HttpClient conn() {
        return client;
    }
}
