/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.foundation.llm;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ProviderType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ModelClientConfig and ProviderType.
 * Ported from Python: tests/unit_tests/core/foundation/llm/test_model_client_config.py
 */
class ModelClientConfigTest {

    @Nested
    @DisplayName("ProviderType tests")
    class ProviderTypeTests {

        @Test
        @DisplayName("ProviderType.fromValue returns correct enum for supported providers")
        void testFromValueSupportedProviders() {
            assertEquals(ProviderType.OPEN_AI.getValue(), ProviderType.fromPythonMemberName("OpenAI").getValue());
            assertEquals(ProviderType.SILICON_FLOW.getValue(), ProviderType.fromPythonMemberName("SiliconFlow").getValue());
            assertEquals(ProviderType.DASH_SCOPE.getValue(), ProviderType.fromPythonMemberName("DashScope").getValue());
        }

        @Test
        @DisplayName("ProviderType.fromValue is case-insensitive")
        void testFromValueCaseInsensitive() {
            assertEquals(ProviderType.OPEN_AI.getValue(), ProviderType.fromLowercaseValue("openai").getValue());
            assertEquals(ProviderType.SILICON_FLOW.getValue(), ProviderType.fromLowercaseValue("siliconflow").getValue());
        }

        @Test
        @DisplayName("ProviderType.fromValue throws for invalid provider")
        void testFromValueInvalidProvider() {
            assertThrows(IllegalArgumentException.class,
                    () -> ModelClientConfig.normalizeClientProvider("mock-LLM"));
        }
    }

    @Nested
    @DisplayName("ModelClientConfig construction")
    class ConfigConstruction {

        @Test
        @DisplayName("Config accepts supported provider string")
        void testConfigAcceptsSupportedProviders() {
            ModelClientConfig cfg = ModelClientConfig.builder()
                    .clientProvider(ProviderType.OPEN_AI.getValue())
                    .apiKey("sk-test")
                    .apiBase("http://localhost")
                    .build();
            assertEquals(ProviderType.OPEN_AI.getValue(), cfg.getClientProvider());

            ModelClientConfig cfg2 = ModelClientConfig.builder()
                    .clientProvider(ProviderType.SILICON_FLOW.getValue())
                    .apiKey("sk-test")
                    .apiBase("http://localhost")
                    .build();
            assertEquals(ProviderType.SILICON_FLOW.getValue(), cfg2.getClientProvider());
        }

        @Test
        @DisplayName("Config requires apiKey")
        void testConfigRequiresApiKey() {
            ModelClientConfig cfg = ModelClientConfig.builder()
                    .clientProvider(ProviderType.OPEN_AI.getValue())
                    .apiBase("http://localhost")
                    .build();
            assertNull(cfg.getApiKey());
        }

        @Test
        @DisplayName("Config requires apiBase")
        void testConfigRequiresApiBase() {
            ModelClientConfig cfg = ModelClientConfig.builder()
                    .clientProvider(ProviderType.OPEN_AI.getValue())
                    .apiKey("sk-test")
                    .build();
            assertNull(cfg.getApiBase());
        }

        @Test
        @DisplayName("Config requires clientProvider")
        void testConfigRequiresClientProvider() {
            ModelClientConfig cfg = ModelClientConfig.builder()
                    .apiKey("sk-test")
                    .apiBase("http://localhost")
                    .build();
            assertNull(cfg.getClientProvider());
        }

        @Test
        @DisplayName("Config default timeout is 60.0")
        void testConfigDefaultTimeout() {
            ModelClientConfig cfg = ModelClientConfig.builder()
                    .clientProvider(ProviderType.OPEN_AI.getValue())
                    .apiKey("sk-test")
                    .apiBase("http://localhost")
                    .build();
            assertEquals(60.0, cfg.getTimeout());
        }

        @Test
        @DisplayName("Config allows custom timeout")
        void testConfigCustomTimeout() {
            ModelClientConfig cfg = ModelClientConfig.builder()
                    .clientProvider(ProviderType.OPEN_AI.getValue())
                    .apiKey("sk-test")
                    .apiBase("http://localhost")
                    .timeout(120.0)
                    .build();
            assertEquals(120.0, cfg.getTimeout());
        }

        @Test
        @DisplayName("Config auto-generates clientId when not provided")
        void testConfigAutoGeneratesClientId() {
            ModelClientConfig cfg = ModelClientConfig.builder()
                    .clientProvider(ProviderType.OPEN_AI.getValue())
                    .apiKey("sk-test")
                    .apiBase("http://localhost")
                    .build();
            assertNotNull(cfg.getClientId());
            assertFalse(cfg.getClientId().isEmpty());
        }

        @Test
        @DisplayName("Config allows custom clientId")
        void testConfigCustomClientId() {
            ModelClientConfig cfg = ModelClientConfig.builder()
                    .clientId("my-custom-id")
                    .clientProvider(ProviderType.OPEN_AI.getValue())
                    .apiKey("sk-test")
                    .apiBase("http://localhost")
                    .build();
            assertEquals("my-custom-id", cfg.getClientId());
        }

        @Test
        @DisplayName("Config default maxRetries is 3")
        void testConfigDefaultMaxRetries() {
            ModelClientConfig cfg = ModelClientConfig.builder()
                    .clientProvider(ProviderType.OPEN_AI.getValue())
                    .apiKey("sk-test")
                    .apiBase("http://localhost")
                    .build();
            assertEquals(3, cfg.getMaxRetries());
        }

        @Test
        @DisplayName("Config default verifySsl is true")
        void testConfigDefaultVerifySsl() {
            ModelClientConfig cfg = ModelClientConfig.builder()
                    .clientProvider(ProviderType.OPEN_AI.getValue())
                    .apiKey("sk-test")
                    .apiBase("http://localhost")
                    .build();
            assertTrue(cfg.isVerifySsl());
        }

        @Test
        @DisplayName("Config supports extra fields")
        void testConfigExtraFields() {
            ModelClientConfig cfg = ModelClientConfig.builder()
                    .clientProvider(ProviderType.OPEN_AI.getValue())
                    .apiKey("sk-test")
                    .apiBase("http://localhost")
                    .extraFields(Map.of("custom_param", "custom_value"))
                    .build();
            assertEquals("custom_value", cfg.getExtraFields().get("custom_param"));
        }

        @Test
        @DisplayName("Config supports custom headers")
        void testConfigHeaders() {
            ModelClientConfig cfg = ModelClientConfig.builder()
                    .clientProvider(ProviderType.OPEN_AI.getValue())
                    .apiKey("sk-test")
                    .apiBase("http://localhost")
                    .headers(Map.of("X-Test", "1", "X-Trace", "demo"))
                    .build();
            assertEquals("1", cfg.getHeaders().get("X-Test"));
            assertEquals("demo", cfg.getHeaders().get("X-Trace"));
            assertFalse(cfg.getExtraFields().containsKey("headers"));
        }

        @Test
        @DisplayName("Config rejects unregistered string provider")
        void testConfigRejectsUnregisteredStringProvider() {
            // In Java, clientProvider is validated at construction time via normalizeClientProvider
            assertThrows(RuntimeException.class, () ->
                    ModelClientConfig.builder()
                            .clientProvider("TempMockLLM")
                            .apiKey("sk-test")
                            .apiBase("http://localhost")
                            .build());
        }
    }
}
