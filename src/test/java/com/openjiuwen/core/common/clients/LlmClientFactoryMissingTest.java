/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Missing parity tests for HTTPX/OpenAI client factory behavior.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.core.common.clients.test_llm_client} in
 * {@code tests/unit_tests/core/common/clients/test_llm_client.py}.</p>
 */
class LlmClientFactoryMissingTest {

    @Test
    void httpxConnectorPoolConfigDefaultValues() {
        HttpXConnectorPoolConfig config = new HttpXConnectorPoolConfig();

        assertThat(config.getMaxKeepaliveConnections()).isEqualTo(20);
        assertThat(config.getLocalAddress()).isNull();
        assertThat(config.getProxy()).isNull();
        assertThat(config.getLimit()).isEqualTo(100);
        assertThat(config.getLimitPerHost()).isEqualTo(30);
        assertThat(config.isSslVerify()).isTrue();
    }

    @Test
    void httpxConnectorPoolConfigCustomValues() {
        HttpXConnectorPoolConfig config = new HttpXConnectorPoolConfig(Map.of(
                "max_keepalive_connections", 50,
                "local_address", "192.168.1.100",
                "proxy", "http://proxy.example.com:8080",
                "limit", 200,
                "limit_per_host", 50,
                "ssl_verify", false
        ));

        assertThat(config.getMaxKeepaliveConnections()).isEqualTo(50);
        assertThat(config.getLocalAddress()).isEqualTo("192.168.1.100");
        assertThat(config.getProxy()).isEqualTo("http://proxy.example.com:8080");
        assertThat(config.getLimit()).isEqualTo(200);
        assertThat(config.getLimitPerHost()).isEqualTo(50);
        assertThat(config.isSslVerify()).isFalse();
    }

    @Test
    void httpxConnectorPoolConfigRejectsZeroKeepaliveConnections() {
        assertThatThrownBy(() -> new HttpXConnectorPoolConfig(Map.of("max_keepalive_connections", 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max_keepalive_connections");
    }

    @Test
    void httpxConnectorPoolConfigRejectsNegativeKeepaliveConnections() {
        assertThatThrownBy(() -> new HttpXConnectorPoolConfig(Map.of("max_keepalive_connections", -5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max_keepalive_connections");
    }

    @Test
    void httpxConnectorPoolConfigGeneratesDifferentKeysForMaxKeepalive() {
        HttpXConnectorPoolConfig first = new HttpXConnectorPoolConfig(Map.of("max_keepalive_connections", 20));
        HttpXConnectorPoolConfig second = new HttpXConnectorPoolConfig(Map.of("max_keepalive_connections", 30));

        assertThat(first.generateKey()).isNotEqualTo(second.generateKey());
    }

    @Test
    void httpxConnectorPoolConfigGeneratesDifferentKeysForProxy() {
        HttpXConnectorPoolConfig first = new HttpXConnectorPoolConfig(Map.of("max_keepalive_connections", 20));
        HttpXConnectorPoolConfig second = new HttpXConnectorPoolConfig(Map.of(
                "max_keepalive_connections", 20,
                "proxy", "http://proxy:8080"
        ));

        assertThat(first.generateKey()).isNotEqualTo(second.generateKey());
    }

    @Test
    void httpxConnectorPoolConfigGeneratesSameKeyForSameConfig() {
        HttpXConnectorPoolConfig first = new HttpXConnectorPoolConfig(Map.of("max_keepalive_connections", 20));
        HttpXConnectorPoolConfig second = new HttpXConnectorPoolConfig(Map.of("max_keepalive_connections", 20));

        assertThat(first.generateKey()).isEqualTo(second.generateKey());
    }

    @Test
    void httpxConnectorPoolInitializesBasicClient() {
        HttpXConnectorPoolConfig config = new HttpXConnectorPoolConfig(Map.of(
                "limit", 100,
                "limit_per_host", 30,
                "max_keepalive_connections", 20,
                "keepalive_timeout", 60
        ));

        HttpXConnectorPool pool = new HttpXConnectorPool(config);

        assertThat(pool.conn()).isInstanceOf(HttpClient.class);
        assertThat(pool.getHttpxConfig()).isSameAs(config);
        assertThat(pool.close().join()).isNull();
    }

    @Test
    void httpxConnectorPoolInitializesWithProxy() {
        HttpXConnectorPool pool = new HttpXConnectorPool(new HttpXConnectorPoolConfig(Map.of(
                "proxy", "http://proxy.example.com:8080",
                "ssl_verify", true
        )));

        assertThat(((HttpClient) pool.conn()).proxy()).isPresent();
    }

    @Test
    void httpxConnectorPoolRetainsLocalAddressConfig() {
        HttpXConnectorPoolConfig config = new HttpXConnectorPoolConfig(Map.of("local_address", "192.168.1.100"));
        HttpXConnectorPool pool = new HttpXConnectorPool(config);

        assertThat(pool.getHttpxConfig().getLocalAddress()).isEqualTo("192.168.1.100");
    }

    @Test
    void httpxConnectorPoolRetainsSslContextConfig() {
        HttpXConnectorPoolConfig config = new HttpXConnectorPoolConfig(Map.of(
                "ssl_verify", true,
                "ssl_cert", "/path/to/cert.pem"
        ));
        HttpXConnectorPool pool = new HttpXConnectorPool(config);

        assertThat(pool.getHttpxConfig().isSslVerify()).isTrue();
        assertThat(pool.getHttpxConfig().getSslCert()).isEqualTo("/path/to/cert.pem");
    }

    @Test
    void httpxConnectorPoolRetainsExtendParams() {
        HttpXConnectorPoolConfig config = new HttpXConnectorPoolConfig(Map.of(
                "extend_params", Map.of("http2", true, "ud", "extra_param")
        ));
        HttpXConnectorPool pool = new HttpXConnectorPool(config);

        assertThat(pool.getHttpxConfig().getExtendParams())
                .containsEntry("http2", true)
                .containsEntry("ud", "extra_param");
    }

    @Test
    void httpxConnectorPoolInheritedIsExpiredFalseByDefault() {
        HttpXConnectorPool pool = new HttpXConnectorPool(new HttpXConnectorPoolConfig());

        assertThat(pool.isExpired()).isFalse();
    }

    @Test
    void httpxConnectorPoolInheritedStatContainsLifecycleFields() {
        HttpXConnectorPool pool = new HttpXConnectorPool(new HttpXConnectorPoolConfig());

        Map<String, Object> stats = pool.stat();

        @SuppressWarnings("unchecked")
        Map<String, Object> refDetail = (Map<String, Object>) stats.get("ref_detail");
        assertThat(stats).containsKey("closed");
        assertThat(refDetail).containsKeys("created_at", "last_used");
    }

    @Test
    void createHttpxClientWithMapConfigCreatesSyncClient() {
        HttpClient client = LlmClientFactory.createHttpxClient(new LinkedHashMap<>(Map.of(
                "proxy", "http://proxy:8080",
                "ssl_verify", false,
                "limit", 200
        )), false).join();

        assertThat(client).isNotNull();
        assertThat(client.proxy()).isPresent();
    }

    @Test
    void createHttpxClientWithConfigObjectCreatesAsyncClient() {
        HttpXConnectorPoolConfig config = new HttpXConnectorPoolConfig(Map.of(
                "proxy", "http://proxy:8080",
                "ssl_verify", false,
                "max_keepalive_connections", 50
        ));

        HttpClient client = LlmClientFactory.createHttpxClient(config, true).join();

        assertThat(client).isNotNull();
        assertThat(config.isNeedAsync()).isTrue();
    }

    @Test
    void createAsyncOpenAiClientWithMapConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("api_key", "test-api-key");
        config.put("api_base", "https://api.openai.com/v1");
        config.put("timeout", 30);
        config.put("max_retries", 3);
        config.put("verify_ssl", true);
        config.put("ssl_cert", "/path/to/cert.pem");
        config.put("client_provider", "openai");

        LlmClientFactory.OpenAiClientHandle handle =
                LlmClientFactory.createAsyncOpenaiClient(config, Map.of("extra_param", "value")).join();

        assertThat(handle.asyncClient()).isTrue();
        assertThat(handle.apiKey()).isEqualTo("test-api-key");
        assertThat(handle.baseUrl()).isEqualTo("https://api.openai.com/v1");
        assertThat(handle.timeout()).isEqualTo(30.0d);
        assertThat(handle.maxRetries()).isEqualTo(3);
        assertThat(handle.httpClient()).isNotNull();
    }

    @Test
    void createSyncOpenAiClientWithModelConfigObject() {
        ModelClientConfig config = ModelClientConfig.builder()
                .apiKey("test-api-key")
                .apiBase("https://api.openai.com/v1")
                .timeout(30.0d)
                .maxRetries(3)
                .verifySsl(false)
                .clientProvider("openai")
                .build();

        LlmClientFactory.OpenAiClientHandle handle = LlmClientFactory.createOpenaiClient(config, Map.of()).join();

        assertThat(handle.asyncClient()).isFalse();
        assertThat(handle.apiKey()).isEqualTo("test-api-key");
        assertThat(handle.baseUrl()).isEqualTo("https://api.openai.com/v1");
        assertThat(handle.timeout()).isEqualTo(30.0d);
        assertThat(handle.maxRetries()).isEqualTo(3);
    }

    @Test
    void createOpenAiClientWithoutProxyStillCreatesHandle() {
        LlmClientFactory.OpenAiClientHandle handle = LlmClientFactory.createAsyncOpenaiClient(Map.of(
                "api_key", "test-api-key",
                "api_base", "https://api.openai.com/v1",
                "client_provider", "openai"
        ), Map.of()).join();

        assertThat(handle.asyncClient()).isTrue();
        assertThat(handle.httpClient()).isNotNull();
    }

    @Test
    void createAsyncOpenAiClientForwardsSanitizedCustomHeaders() {
        ModelClientConfig config = ModelClientConfig.builder()
                .apiKey("test-api-key")
                .apiBase("https://api.openai.com/v1")
                .timeout(30.0d)
                .maxRetries(3)
                .verifySsl(false)
                .clientProvider("openai")
                .customHeaders(Map.of(
                        "x-default", "custom-override",
                        "x-tenant", "tenant-a",
                        "X-Request-Num", 7,
                        "Authorization", "Bearer blocked",
                        "Content-Length", "blocked",
                        "X-Blank", "   "
                ))
                .build();

        LlmClientFactory.OpenAiClientHandle handle = LlmClientFactory.createAsyncOpenaiClient(config, Map.of()).join();

        assertThat(handle.defaultHeaders()).containsOnly(
                Map.entry("x-default", "custom-override"),
                Map.entry("x-tenant", "tenant-a"),
                Map.entry("X-Request-Num", "7")
        );
    }

    @Test
    void createSyncOpenAiClientForwardsSanitizedCustomHeaders() {
        ModelClientConfig config = ModelClientConfig.builder()
                .apiKey("test-api-key")
                .apiBase("https://api.openai.com/v1")
                .timeout(30.0d)
                .maxRetries(3)
                .verifySsl(false)
                .clientProvider("openai")
                .customHeaders(Map.of(
                        "X-Default", "sync-override",
                        "X-Trace-Id", "trace-001"
                ))
                .build();

        LlmClientFactory.OpenAiClientHandle handle = LlmClientFactory.createOpenaiClient(config, Map.of()).join();

        assertThat(handle.defaultHeaders()).containsOnly(
                Map.entry("X-Default", "sync-override"),
                Map.entry("X-Trace-Id", "trace-001")
        );
    }

    @Test
    void openAiFactoriesAreRegisteredWithClientRegistry() {
        ensureLlmClientFactoryLoaded();

        assertThat(ClientRegistry.getClientRegistry().listClients())
                .contains("common_async_openai", "common_openai", "common_httpx");
    }

    @Test
    void getHttpxClientViaRegistry() {
        ensureLlmClientFactoryLoaded();

        Object client = ClientRegistry.getClientRegistry().getClient("httpx", "common", Map.of(
                "proxy", "http://proxy:8080",
                "ssl_verify", false
        ));

        assertThat(client).isInstanceOf(HttpClient.class);
    }

    @Test
    void connectorPoolLifecycleWithOpenAiClient() {
        LlmClientFactory.OpenAiClientHandle handle = LlmClientFactory.createOpenaiClient(Map.of(
                "api_key", "test-key",
                "api_base", "https://api.openai.com/v1",
                "proxy", "http://proxy:8080",
                "client_provider", "openai",
                "verify_ssl", false
        ), Map.of()).join();

        assertThat(handle.asyncClient()).isFalse();
        assertThat(handle.httpClient()).isNotNull();
        assertThat(handle.apiKey()).isEqualTo("test-key");
    }

    @Test
    void invalidConfigTypeIsRejected() {
        assertThatThrownBy(() -> LlmClientFactory.createHttpxClient(Map.of("limit", "invalid_config"), false))
                .isInstanceOf(NumberFormatException.class);
    }

    @Test
    void connectorPoolCreationFailurePropagates() {
        String providerName = "mtt00122-failing-httpx";
        ConnectorPoolManager.registerProvider(providerName,
                ignored -> CompletableFutureFailure.failedFuture(new IllegalStateException("Pool creation failed")));

        assertThatThrownBy(() -> ConnectorPoolManager.getConnectorPoolManager()
                .getConnectorPool(providerName, new HttpXConnectorPoolConfig()).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Pool creation failed");
    }

    private static void ensureLlmClientFactoryLoaded() {
        try {
            Class.forName(LlmClientFactory.class.getName());
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class CompletableFutureFailure {
        private CompletableFutureFailure() {
        }

        private static <T> java.util.concurrent.CompletableFuture<T> failedFuture(Throwable error) {
            return java.util.concurrent.CompletableFuture.failedFuture(error);
        }
    }
}
