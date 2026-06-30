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
            assertEquals(ProviderType.OpenAI, ProviderType.fromValue("OpenAI"));
            assertEquals(ProviderType.SiliconFlow, ProviderType.fromValue("SiliconFlow"));
            assertEquals(ProviderType.DashScope, ProviderType.fromValue("DashScope"));
        }

        @Test
        @DisplayName("ProviderType.fromValue is case-insensitive")
        void testFromValueCaseInsensitive() {
            assertEquals(ProviderType.OpenAI, ProviderType.fromValue("openai"));
            assertEquals(ProviderType.SiliconFlow, ProviderType.fromValue("siliconflow"));
        }

        @Test
        @DisplayName("ProviderType.fromValue throws for invalid provider")
        void testFromValueInvalidProvider() {
            assertThrows(IllegalArgumentException.class,
                    () -> ProviderType.fromValue("mock-LLM"));
        }
    }

    @Nested
    @DisplayName("ModelClientConfig construction")
    class ConfigConstruction {

        @Test
        @DisplayName("Config accepts supported provider string")
        void testConfigAcceptsSupportedProviders() {
            ModelClientConfig cfg = ModelClientConfig.builder()
                    .clientProvider(ProviderType.OpenAI.getValue())
                    .apiKey("sk-test")
                    .apiBase("http://localhost")
                    .build();
            assertEquals(ProviderType.OpenAI.getValue(), cfg.getClientProvider());

            ModelClientConfig cfg2 = ModelClientConfig.builder()
                    .clientProvider(ProviderType.SiliconFlow.getValue())
                    .apiKey("sk-test")
                    .apiBase("http://localhost")
                    .build();
            assertEquals(ProviderType.SiliconFlow.getValue(), cfg2.getClientProvider());
        }

        @Test
        @DisplayName("Config requires apiKey")
        void testConfigRequiresApiKey() {
            assertThrows(NullPointerException.class, () ->
                    ModelClientConfig.builder()
                            .clientProvider(ProviderType.OpenAI.getValue())
                            .apiBase("http://localhost")
                            .build());
        }

        @Test
        @DisplayName("Config requires apiBase")
        void testConfigRequiresApiBase() {
            assertThrows(NullPointerException.class, () ->
                    ModelClientConfig.builder()
                            .clientProvider(ProviderType.OpenAI.getValue())
                            .apiKey("sk-test")
                            .build());
        }

        @Test
        @DisplayName("Config requires clientProvider")
        void testConfigRequiresClientProvider() {
            assertThrows(NullPointerException.class, () ->
                    ModelClientConfig.builder()
                            .apiKey("sk-test")
                            .apiBase("http://localhost")
                            .build());
        }

        @Test
        @DisplayName("Config default timeout is 60.0")
        void testConfigDefaultTimeout() {
            ModelClientConfig cfg = ModelClientConfig.builder()
                    .clientProvider(ProviderType.OpenAI.getValue())
                    .apiKey("sk-test")
                    .apiBase("http://localhost")
                    .build();
            assertEquals(60.0, cfg.getTimeout());
        }

        @Test
        @DisplayName("Config allows custom timeout")
        void testConfigCustomTimeout() {
            ModelClientConfig cfg = ModelClientConfig.builder()
                    .clientProvider(ProviderType.OpenAI.getValue())
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
                    .clientProvider(ProviderType.OpenAI.getValue())
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
                    .clientProvider(ProviderType.OpenAI.getValue())
                    .apiKey("sk-test")
                    .apiBase("http://localhost")
                    .build();
            assertEquals("my-custom-id", cfg.getClientId());
        }

        @Test
        @DisplayName("Config default maxRetries is 3")
        void testConfigDefaultMaxRetries() {
            ModelClientConfig cfg = ModelClientConfig.builder()
                    .clientProvider(ProviderType.OpenAI.getValue())
                    .apiKey("sk-test")
                    .apiBase("http://localhost")
                    .build();
            assertEquals(3, cfg.getMaxRetries());
        }

        @Test
        @DisplayName("Config default verifySsl is true")
        void testConfigDefaultVerifySsl() {
            ModelClientConfig cfg = ModelClientConfig.builder()
                    .clientProvider(ProviderType.OpenAI.getValue())
                    .apiKey("sk-test")
                    .apiBase("http://localhost")
                    .build();
            assertTrue(cfg.isVerifySsl());
        }

        @Test
        @DisplayName("Config supports extra fields")
        void testConfigExtraFields() {
            ModelClientConfig cfg = ModelClientConfig.builder()
                    .clientProvider(ProviderType.OpenAI.getValue())
                    .apiKey("sk-test")
                    .apiBase("http://localhost")
                    .extraField("custom_param", "custom_value")
                    .build();
            assertEquals("custom_value", cfg.getExtraFields().get("custom_param"));
        }

        @Test
        @DisplayName("Config supports custom headers")
        void testConfigHeaders() {
            ModelClientConfig cfg = ModelClientConfig.builder()
                    .clientProvider(ProviderType.OpenAI.getValue())
                    .apiKey("sk-test")
                    .apiBase("http://localhost")
                    .headers(Map.of("X-Test", "1", "X-Trace", "demo"))
                    .build();
            assertEquals("1", cfg.getHeaders().get("X-Test"));
            assertEquals("demo", cfg.getHeaders().get("X-Trace"));
            assertFalse(cfg.getExtraFields().containsKey("headers"));
        }

        @Test
        @DisplayName("Config allows registered string provider (any string accepted)")
        void testConfigAllowsStringProvider() {
            // In Java, clientProvider is just a String — unlike Python it doesn't validate
            // against a registry at construction time. Validation happens at Model creation.
            ModelClientConfig cfg = ModelClientConfig.builder()
                    .clientProvider("TempMockLLM")
                    .apiKey("sk-test")
                    .apiBase("http://localhost")
                    .build();
            assertEquals("TempMockLLM", cfg.getClientProvider());
        }
    }
}
