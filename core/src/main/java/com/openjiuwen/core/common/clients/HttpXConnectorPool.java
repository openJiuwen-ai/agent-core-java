/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * HTTPX-style connector pool implementation.
 *
 * <p>Mirrors Python's {@code HttpXConnectorPool} in
 * {@code openjiuwen/core/common/clients/llm_client.py}.</p>
 */
public class HttpXConnectorPool extends ConnectorPool {

    private final HttpXConnectorPoolConfig httpxConfig;

    public HttpXConnectorPool(HttpXConnectorPoolConfig config) {
        super(config == null ? new HttpXConnectorPoolConfig() : config);
        this.httpxConfig = config == null ? new HttpXConnectorPoolConfig() : config;
        this.conn = buildClient(this.httpxConfig);
    }

    @Override
    public HttpClient getConn() {
        return (HttpClient) conn;
    }

    public HttpXConnectorPoolConfig getHttpxConfig() {
        return httpxConfig;
    }

    @Override
    protected CompletableFuture<Void> doClose(Map<String, Object> kwargs) {
        return CompletableFuture.completedFuture(null);
    }

    private HttpClient buildClient(HttpXConnectorPoolConfig config) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_2);
        if (config.getProxy() != null && !config.getProxy().isBlank()) {
            builder.proxy(proxySelector(config.getProxy()));
        }
        return builder.build();
    }

    private static ProxySelector proxySelector(String proxy) {
        URI uri = proxy.contains("://") ? URI.create(proxy) : URI.create("http://" + proxy);
        int port = uri.getPort();
        if (port < 0) {
            port = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
        }
        return ProxySelector.of(new InetSocketAddress(uri.getHost(), port));
    }
}
