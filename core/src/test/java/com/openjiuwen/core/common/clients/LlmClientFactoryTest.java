/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's HTTPX/OpenAI client factories in
 * {@code openjiuwen/core/common/clients/llm_client.py}.
 */
class LlmClientFactoryTest {

    @Test
    void httpxConnectorConfigParsesDictAndExtendsBaseKey() {
        HttpXConnectorPoolConfig config = new HttpXConnectorPoolConfig(Map.of(
                "limit", 5,
                "limit_per_host", 2,
                "max_keepalive_connections", 7,
                "local_address", "127.0.0.1",
                "proxy", "http://proxy.example:8080",
                "need_async", false
        ));

        assertThat(config.getLimit()).isEqualTo(5);
        assertThat(config.getLimitPerHost()).isEqualTo(2);
        assertThat(config.getMaxKeepaliveConnections()).isEqualTo(7);
        assertThat(config.getLocalAddress()).isEqualTo("127.0.0.1");
        assertThat(config.getProxy()).isEqualTo("http://proxy.example:8080");
        assertThat(config.isNeedAsync()).isFalse();
        assertThat(config.generateKey()).contains("max_keepalive_connections:7");
    }

    @Test
    void httpxConnectorConfigRejectsInvalidKeepaliveCount() {
        HttpXConnectorPoolConfig config = new HttpXConnectorPoolConfig();

        assertThatThrownBy(() -> config.setMaxKeepaliveConnections(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max_keepalive_connections must be >= 1");
    }

    @Test
    void httpxConnectorPoolProvidesJavaHttpClient() {
        HttpXConnectorPool pool = new HttpXConnectorPool(new HttpXConnectorPoolConfig());

        assertThat(pool.conn()).isInstanceOf(HttpClient.class);
        assertThat(pool.getConn()).isSameAs(pool.conn());
        assertThat(pool.close().join()).isNull();
        assertThat(pool.isClosed()).isTrue();
    }

    @Test
    void createHttpxClientForcesRequestedAsyncMode() {
        HttpXConnectorPoolConfig config = new HttpXConnectorPoolConfig();
        config.setNeedAsync(true);

        HttpClient client = LlmClientFactory.createHttpxClient(config, false).join();

        assertThat(client).isNotNull();
        assertThat(config.isNeedAsync()).isTrue();
    }

    @Test
    void openAiFactoriesReturnConstructorHandleWithSanitizedHeaders() {
        ModelClientConfig config = ModelClientConfig.builder()
                .apiKey("sk-test")
                .apiBase("https://api.example/v1")
                .timeout(12.0d)
                .maxRetries(4)
                .customHeaders(Map.of("X-Trace", "abc", "Authorization", "secret"))
                .build();

        LlmClientFactory.OpenAiClientHandle asyncHandle =
                LlmClientFactory.createAsyncOpenaiClient(config, Map.of("max_keepalive_connections", 3)).join();
        LlmClientFactory.OpenAiClientHandle syncHandle =
                LlmClientFactory.createOpenaiClient(config, Map.of()).join();

        assertThat(asyncHandle.asyncClient()).isTrue();
        assertThat(syncHandle.asyncClient()).isFalse();
        assertThat(asyncHandle.apiKey()).isEqualTo("sk-test");
        assertThat(asyncHandle.baseUrl()).isEqualTo("https://api.example/v1");
        assertThat(asyncHandle.timeout()).isEqualTo(12.0d);
        assertThat(asyncHandle.maxRetries()).isEqualTo(4);
        assertThat(asyncHandle.defaultHeaders()).containsEntry("X-Trace", "abc");
        assertThat(asyncHandle.defaultHeaders()).doesNotContainKey("Authorization");
        assertThat(asyncHandle.httpClient()).isNotNull();
    }

    @Test
    void clientsFacadePromotesHttpxExport() {
        assertThat(Clients.typedExportNames()).contains("HttpXConnectorPoolConfig");
        assertThat(Clients.httpXConnectorPoolConfigClass()).isEqualTo(HttpXConnectorPoolConfig.class);
        assertThat(Clients.futureTypedExportNames()).isEmpty();
    }
}
