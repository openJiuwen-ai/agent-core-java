/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.modelclients;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelHttpVersion;
import com.openjiuwen.core.foundation.llm.schema.ProviderType;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModelHttpClientsTest {

    @Test
    void buildsHttp2ClientWhenVersionConfigured() {
        HttpClient client = ModelHttpClients.builder(
                clientConfig(ModelHttpVersion.HTTP_2, 7.0D), "https://api.example.test/v1")
                .build();

        assertThat(client.version()).isEqualTo(HttpClient.Version.HTTP_2);
        assertThat(client.connectTimeout()).hasValue(Duration.ofSeconds(7));
    }

    @Test
    void buildsHttp11ClientWhenVersionConfigured() {
        HttpClient client = ModelHttpClients.builder(
                clientConfig(ModelHttpVersion.HTTP_1_1, 5.0D), "https://api.example.test/v1")
                .build();

        assertThat(client.version()).isEqualTo(HttpClient.Version.HTTP_1_1);
        assertThat(client.connectTimeout()).hasValue(Duration.ofSeconds(5));
    }

    @Test
    void defaultsToHttp11WhenHttpVersionIsNotConfigured() {
        HttpClient client = ModelHttpClients.builder(
                clientConfig(null, 6.0D), "https://api.example.test/v1")
                .build();

        assertThat(client.version()).isEqualTo(HttpClient.Version.HTTP_1_1);
        assertThat(client.connectTimeout()).hasValue(Duration.ofSeconds(6));
    }

    @Test
    void connectTimeoutComesFromConfigField() {
        HttpClient client = ModelHttpClients.builder(
                clientConfig(ModelHttpVersion.HTTP_1_1, 11.0D), "https://api.example.test/v1")
                .build();

        assertThat(client.connectTimeout()).hasValue(Duration.ofSeconds(11));
    }

    @Test
    void legacyTimeoutDoesNotDriveConnectTimeout() {
        HttpClient client = ModelHttpClients.builder(
                clientConfig(ModelHttpVersion.HTTP_2, null), "https://api.example.test/v1")
                .build();

        assertThat(client.version()).isEqualTo(HttpClient.Version.HTTP_2);
        assertThat(client.connectTimeout()).hasValue(Duration.ofSeconds(10));
    }

    @Test
    void explicitPortProxyModeIgnoresProxyWithoutPort() {
        ProxySelector proxySelector = ModelHttpClients.proxySelector(
                "http://proxy.example.test",
                ModelHttpClients.ProxyPortMode.EXPLICIT_PORT_ONLY);

        assertThat(proxySelector).isNull();
    }

    @Test
    void defaultPortProxyModeAppliesDefaultPortWhenProxyOmitsPort() {
        ProxySelector httpProxySelector = ModelHttpClients.proxySelector(
                "http://proxy.example.test",
                ModelHttpClients.ProxyPortMode.DEFAULT_PORT_WHEN_MISSING);

        assertThat(httpProxySelector).isNotNull();
        assertProxyAddress(httpProxySelector, "proxy.example.test", 80);

        ProxySelector httpsProxySelector = ModelHttpClients.proxySelector(
                "https://secure-proxy.example.test",
                ModelHttpClients.ProxyPortMode.DEFAULT_PORT_WHEN_MISSING);

        assertThat(httpsProxySelector).isNotNull();
        assertProxyAddress(httpsProxySelector, "secure-proxy.example.test", 443);
    }

    private static ModelClientConfig clientConfig(ModelHttpVersion httpVersion, Double connectTimeoutSeconds) {
        ModelClientConfig.ModelClientConfigBuilder builder = ModelClientConfig.builder()
                .clientProvider(ProviderType.OPEN_AI)
                .apiBase("https://api.example.test/v1")
                .httpVersion(httpVersion)
                .timeout(9.0D);
        if (connectTimeoutSeconds != null) {
            builder.connectTimeoutSeconds(connectTimeoutSeconds);
        }
        return builder.build();
    }

    private static void assertProxyAddress(ProxySelector proxySelector, String host, int port) {
        List<Proxy> proxies = proxySelector.select(URI.create("https://api.example.test/v1"));
        InetSocketAddress address = (InetSocketAddress) proxies.get(0).address();
        assertThat(address.getHostString()).isEqualTo(host);
        assertThat(address.getPort()).isEqualTo(port);
    }
}
