/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.unit_tests.core.common.clients;

import com.openjiuwen.core.common.clients.HttpXConnectorPool;
import com.openjiuwen.core.common.clients.HttpXConnectorPoolConfig;
import com.openjiuwen.core.common.clients.LlmClientFactory;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_llm_client.py} in
 * {@code tests.unit_tests.core.common.clients}.
 */
@Tag("unit-test")
class TestLlmClient {

    @Test
    @Tag("level0")
    @DisplayName("Test HttpXConnectorPoolConfig default values")
    void testDefaultValues() {
        HttpXConnectorPoolConfig config = new HttpXConnectorPoolConfig();

        assertEquals(20, config.getMaxKeepaliveConnections());
        assertEquals(100, config.getLimit());
        assertEquals(30, config.getLimitPerHost());
        assertTrue(config.isSslVerify());
        assertTrue(config.isNeedAsync());
        assertEquals(60.0, config.getKeepaliveTimeout());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test HttpXConnectorPoolConfig custom values")
    void testCustomValues() {
        HttpXConnectorPoolConfig config = new HttpXConnectorPoolConfig();
        config.setMaxKeepaliveConnections(50);
        config.setLocalAddress("192.168.1.100");
        config.setProxy("http://proxy.example.com:8080");
        config.setLimit(200);
        config.setLimitPerHost(50);
        config.setSslVerify(false);

        assertEquals(50, config.getMaxKeepaliveConnections());
        assertEquals("192.168.1.100", config.getLocalAddress());
        assertEquals("http://proxy.example.com:8080", config.getProxy());
        assertEquals(200, config.getLimit());
        assertEquals(50, config.getLimitPerHost());
        assertFalse(config.isSslVerify());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test positive value validation")
    void testValidationPositiveValues() {
        HttpXConnectorPoolConfig config = new HttpXConnectorPoolConfig();

        assertThrows(IllegalArgumentException.class, () -> config.setMaxKeepaliveConnections(0));
        assertThrows(IllegalArgumentException.class, () -> config.setMaxKeepaliveConnections(-5));
        assertThrows(IllegalArgumentException.class, () -> config.setLimit(0));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test key generation")
    void testKeyGeneration() {
        HttpXConnectorPoolConfig config1 = new HttpXConnectorPoolConfig();
        HttpXConnectorPoolConfig config2 = new HttpXConnectorPoolConfig();
        config2.setMaxKeepaliveConnections(30);
        HttpXConnectorPoolConfig config3 = new HttpXConnectorPoolConfig();
        config3.setProxy("http://proxy:8080");
        HttpXConnectorPoolConfig config4 = new HttpXConnectorPoolConfig();

        assertNotEquals(config1.generateKey(), config2.generateKey());
        assertNotEquals(config1.generateKey(), config3.generateKey());
        assertEquals(config1.generateKey(), config4.generateKey());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test key generation includes async flag")
    void testKeyGenerationIncludesAsyncFlag() {
        HttpXConnectorPoolConfig asyncConfig = new HttpXConnectorPoolConfig();
        HttpXConnectorPoolConfig syncConfig = new HttpXConnectorPoolConfig();
        syncConfig.setNeedAsync(false);

        assertNotEquals(asyncConfig.generateKey(), syncConfig.generateKey());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test HttpXConnectorPool initialization")
    void testInitializationBasic() {
        HttpXConnectorPoolConfig config = new HttpXConnectorPoolConfig();
        config.setLimit(100);
        config.setLimitPerHost(30);
        config.setMaxKeepaliveConnections(20);

        HttpXConnectorPool pool = new HttpXConnectorPool(config);

        assertNotNull(pool.conn());
        assertTrue(pool.conn() instanceof HttpClient);
        assertFalse(pool.isClosed());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test HttpXConnectorPool initialization with proxy")
    void testInitializationWithProxy() {
        HttpXConnectorPoolConfig config = new HttpXConnectorPoolConfig();
        config.setProxy("http://proxy.example.com:8080");

        HttpXConnectorPool pool = new HttpXConnectorPool(config);

        assertEquals("http://proxy.example.com:8080", config.getProxy());
        assertNotNull(pool.conn());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test HttpXConnectorPool initialization with local address")
    void testInitializationWithLocalAddress() {
        HttpXConnectorPoolConfig config = new HttpXConnectorPoolConfig();
        config.setLocalAddress("192.168.1.100");

        HttpXConnectorPool pool = new HttpXConnectorPool(config);

        assertEquals("192.168.1.100", config.getLocalAddress());
        assertNotNull(pool.conn());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test HttpXConnectorPool initialization with SSL config")
    void testInitializationWithSslContext() {
        HttpXConnectorPoolConfig config = new HttpXConnectorPoolConfig();
        config.setSslVerify(true);
        config.setSslCert("/path/to/cert.pem");

        HttpXConnectorPool pool = new HttpXConnectorPool(config);

        assertEquals("/path/to/cert.pem", config.getSslCert());
        assertTrue(config.isSslVerify());
        assertNotNull(pool.conn());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test HttpXConnectorPool initialization with extend params")
    void testInitializationWithExtendParams() {
        HttpXConnectorPoolConfig config = new HttpXConnectorPoolConfig();
        config.setExtendParams(Map.of("http2", true, "ud", "extra_param"));

        HttpXConnectorPool pool = new HttpXConnectorPool(config);

        assertEquals(true, config.getExtendParams().get("http2"));
        assertEquals("extra_param", config.getExtendParams().get("ud"));
        assertNotNull(pool.conn());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test inherited connector pool methods")
    void testInheritedMethods() {
        HttpXConnectorPool pool = new HttpXConnectorPool(new HttpXConnectorPoolConfig());

        assertFalse(pool.isClosed());
        assertTrue(pool.getStats().containsKey("closed"));
        assertTrue(pool.getStats().containsKey("ref_count"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test pool async client accessor")
    void testAsyncClientAccessor() {
        HttpXConnectorPool pool = new HttpXConnectorPool(new HttpXConnectorPoolConfig());

        CompletableFuture<HttpClient> future = pool.getAsyncClient();

        assertSame(pool.conn(), future.join());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test pool close lifecycle")
    void testConnectorPoolLifecycle() {
        HttpXConnectorPool pool = new HttpXConnectorPool(new HttpXConnectorPoolConfig());

        pool.close().join();

        assertTrue(pool.isClosed());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test create sync HTTPX client with dict config")
    void testCreateSyncClientWithDictConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("proxy", "http://proxy:8080");
        config.put("ssl_verify", false);
        config.put("max_keepalive_connections", 25);

        HttpClient client = LlmClientFactory.createHttpxClient(config, false).join();

        assertNotNull(client);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test create async HTTPX client with config object")
    void testCreateAsyncClientWithConfigObject() {
        HttpXConnectorPoolConfig config = new HttpXConnectorPoolConfig();
        config.setProxy("http://proxy:8080");
        config.setSslVerify(false);
        config.setMaxKeepaliveConnections(50);

        HttpClient client = LlmClientFactory.createHttpxClient(config, true).join();

        assertNotNull(client);
        assertTrue(config.isNeedAsync());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test create async OpenAI-compatible client")
    void testCreateAsyncOpenaiClientWithConfigObject() {
        ModelClientConfig config = modelConfig().build();

        HttpClient client = LlmClientFactory.createAsyncOpenaiClient(config, Map.of("extra_param", "value")).join();

        assertNotNull(client);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test create sync OpenAI-compatible client")
    void testCreateSyncOpenaiClientWithConfigObject() {
        ModelClientConfig config = modelConfig().verifySsl(false).build();

        HttpClient client = LlmClientFactory.createOpenaiClient(config, Map.of()).join();

        assertNotNull(client);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test OpenAI client without proxy")
    void testOpenaiClientWithoutProxy() {
        ModelClientConfig config = modelConfig().apiBase("https://api.openai.com/v1").build();

        HttpClient client = LlmClientFactory.createAsyncOpenaiClient(config, null).join();

        assertNotNull(client);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test sanitized custom headers helper")
    void testCreateAsyncOpenaiClientForwardsSanitizedCustomHeaders() {
        Map<String, Object> headers = new HashMap<>();
        headers.put(" X-Tenant ", " tenant-a ");
        headers.put("X-Number", 123);
        headers.put("Authorization", "Bearer blocked");
        headers.put("Content-Length", "999");
        headers.put("", "blocked");
        headers.put("X-Blank", null);

        Map<String, String> sanitized = LlmClientFactory.sanitizeHeaders(headers);

        assertEquals(" tenant-a ", sanitized.get("X-Tenant"));
        assertEquals("123", sanitized.get("X-Number"));
        assertFalse(sanitized.containsKey("Authorization"));
        assertFalse(sanitized.containsKey("Content-Length"));
        assertFalse(sanitized.containsKey(""));
        assertFalse(sanitized.containsKey("X-Blank"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test sync custom headers are carried by model config")
    void testCreateSyncOpenaiClientForwardsSanitizedCustomHeaders() {
        ModelClientConfig config = modelConfig()
                .customHeaders(Map.of("X-Default", "sync-override", "X-Trace-Id", "trace-001"))
                .build();

        assertEquals("sync-override", config.getCustomHeaders().get("X-Default"));
        assertEquals("trace-001", config.getCustomHeaders().get("X-Trace-Id"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test invalid config type")
    void testInvalidConfigType() {
        Map<String, Object> config = new HashMap<>();
        config.put("max_keepalive_connections", "invalid");

        assertThrows(ClassCastException.class, () -> LlmClientFactory.createHttpxClient(config, false));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test connector pool creation failure for null builder required fields")
    void testConnectorPoolCreationFailure() {
        assertThrows(NullPointerException.class, () -> ModelClientConfig.builder().build());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test ModelClientConfig values")
    void testModelClientConfig() {
        ModelClientConfig config = modelConfig()
                .clientId("client-001")
                .timeout(30)
                .maxRetries(5)
                .verifySsl(false)
                .build();

        assertEquals("client-001", config.getClientId());
        assertEquals("openai", config.getClientProvider());
        assertEquals("test-key", config.getApiKey());
        assertEquals("https://api.openai.com/v1", config.getApiBase());
        assertEquals(30, config.getTimeout());
        assertEquals(5, config.getMaxRetries());
        assertFalse(config.isVerifySsl());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test ModelClientConfig rejects non-positive timeout")
    void testModelClientConfigTimeoutValidation() {
        assertThrows(IllegalArgumentException.class, () -> modelConfig().timeout(0).build());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test factory methods do not throw for basic config")
    void testFactoryMethodsBasicSmoke() {
        ModelClientConfig config = modelConfig().build();

        assertDoesNotThrow(() -> LlmClientFactory.createOpenaiClient(config, Map.of()).join());
        assertDoesNotThrow(() -> LlmClientFactory.createAsyncOpenaiClient(config, Map.of()).join());
    }

    private static ModelClientConfig.Builder modelConfig() {
        return ModelClientConfig.builder()
                .clientProvider("openai")
                .apiKey("test-key")
                .apiBase("https://api.openai.com/v1");
    }
}
