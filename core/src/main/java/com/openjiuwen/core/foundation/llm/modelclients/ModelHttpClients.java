/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.modelclients;

import com.openjiuwen.core.common.security.SslUtils;
import com.openjiuwen.core.common.security.UrlUtils;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

final class ModelHttpClients {
    private final ModelClientConfig clientConfig;
    private final String targetUrl;
    private final HttpClient.Builder builder;

    private ModelHttpClients(ModelClientConfig clientConfig, String targetUrl) {
        this.clientConfig = clientConfig;
        this.targetUrl = targetUrl;
        // Connect timeout is tiered independently so refused LLM hosts fail fast.
        this.builder = HttpClient.newBuilder()
                .connectTimeout(connectTimeoutDuration(clientConfig));
        ModelTimeoutResolver.warnIfLegacyTimeoutExplicit(clientConfig);
    }

    static ModelHttpClients builder(ModelClientConfig clientConfig, String targetUrl) {
        return new ModelHttpClients(clientConfig, targetUrl);
    }

    ModelHttpClients withSsl() {
        if (clientConfig != null) {
            SslUtils.configureHttpClientSsl(
                    builder,
                    targetUrl,
                    clientConfig.isVerifySsl(),
                    clientConfig.getSslCert());
        }
        return this;
    }

    ModelHttpClients withProxy() {
        return withProxy(ProxyPortMode.DEFAULT_PORT_WHEN_MISSING);
    }

    ModelHttpClients withExplicitPortProxy() {
        return withProxy(ProxyPortMode.EXPLICIT_PORT_ONLY);
    }

    private ModelHttpClients withProxy(ProxyPortMode portMode) {
        String proxyUrl = UrlUtils.getGlobalProxyUrl(targetUrl);
        if (proxyUrl == null || proxyUrl.isBlank()) {
            return this;
        }
        ProxySelector proxySelector = proxySelector(proxyUrl, portMode);
        if (proxySelector != null) {
            builder.proxy(proxySelector);
        }
        return this;
    }

    HttpClient build() {
        applyHttpVersion();
        return builder.build();
    }

    private void applyHttpVersion() {
        if (clientConfig != null && clientConfig.getHttpVersion() != null) {
            builder.version(clientConfig.getHttpVersion().toJdkVersion());
            return;
        }
        // Unconfigured clients use HTTP/1.1 so HTTP/1.1-only servers keep working.
        builder.version(HttpClient.Version.HTTP_1_1);
    }

    static ProxySelector proxySelector(String proxyUrl, ProxyPortMode portMode) {
        if (proxyUrl == null || proxyUrl.isBlank()) {
            return null;
        }
        URI proxyUri = URI.create(proxyUrl);
        String host = proxyUri.getHost();
        if (host == null || host.isBlank()) {
            return null;
        }
        int port = proxyUri.getPort();
        if (port <= 0 && portMode == ProxyPortMode.EXPLICIT_PORT_ONLY) {
            return null;
        }
        int resolvedPort = port > 0 ? port : defaultProxyPort(proxyUri);
        return ProxySelector.of(new InetSocketAddress(host, resolvedPort));
    }

    private static int defaultProxyPort(URI proxyUri) {
        String scheme = proxyUri.getScheme();
        if ("https".equalsIgnoreCase(scheme)) {
            return 443;
        }
        return 80;
    }

    /**
     * Connect timeout is resolved by the tiered resolver (config field, then system property,
     * then default 10s) so a refused LLM host fails fast; response-header wait and stream idle
     * timeouts live elsewhere.
     */
    private static Duration connectTimeoutDuration(ModelClientConfig clientConfig) {
        return ModelTimeoutResolver.toDuration(
                ModelTimeoutResolver.resolveConnectTimeoutSeconds(clientConfig));
    }

    enum ProxyPortMode {
        EXPLICIT_PORT_ONLY,
        DEFAULT_PORT_WHEN_MISSING
    }
}
