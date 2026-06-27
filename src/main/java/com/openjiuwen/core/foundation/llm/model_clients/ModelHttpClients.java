/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients;

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
        this.builder = HttpClient.newBuilder()
                .connectTimeout(timeoutDuration(clientConfig));
    }

    static ModelHttpClients builder(ModelClientConfig clientConfig, String targetUrl) {
        return new ModelHttpClients(clientConfig, targetUrl);
    }

    ModelHttpClients connectTimeout(Duration timeout) {
        if (timeout != null) {
            builder.connectTimeout(timeout);
        }
        return this;
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
        if (clientConfig == null || clientConfig.getHttpVersion() == null) {
            return;
        }
        builder.version(clientConfig.getHttpVersion().toJdkVersion());
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

    private static Duration timeoutDuration(ModelClientConfig clientConfig) {
        double seconds = clientConfig == null ? 60.0D : clientConfig.getTimeout();
        long millis = Math.max(1L, Math.round(seconds * 1000.0D));
        return Duration.ofMillis(millis);
    }

    enum ProxyPortMode {
        EXPLICIT_PORT_ONLY,
        DEFAULT_PORT_WHEN_MISSING
    }
}
