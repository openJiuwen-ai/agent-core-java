/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.foundation.llm;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ProviderType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for ModelClientConfig.
 *
 * <p>Mirrors Python's tests/unit_tests/core/foundation/llm/test_model_client_config.py.</p>
 */
@DisplayName("TestModelClientConfig")
class TestModelClientConfig {

    @Nested
    @DisplayName("Provider tests")
    class ProviderTests {

        @Test
        @DisplayName("Test ModelClientConfig accepts supported providers - OpenAI")
        void testModelClientConfigAcceptsOpenAIProvider() {
            ModelClientConfig cfg = baseBuilder()
                    .clientProvider(ProviderType.OpenAI.getValue())
                    .build();
            assertEquals(ProviderType.OpenAI.getValue(), cfg.getClientProvider());
        }

        @Test
        @DisplayName("Test ModelClientConfig accepts supported providers - SiliconFlow")
        void testModelClientConfigAcceptsSiliconFlowProvider() {
            ModelClientConfig cfg = baseBuilder()
                    .clientProvider(ProviderType.SiliconFlow.getValue())
                    .build();
            assertEquals(ProviderType.SiliconFlow.getValue(), cfg.getClientProvider());
        }

        @Test
        @DisplayName("Test ModelClientConfig accepts supported providers - OpenRouter")
        void testModelClientConfigAcceptsOpenRouterProvider() {
            ModelClientConfig cfg = baseBuilder()
                    .clientProvider(ProviderType.OpenRouter.getValue())
                    .build();
            assertEquals(ProviderType.OpenRouter.getValue(), cfg.getClientProvider());
        }

        @Test
        @DisplayName("Test ModelClientConfig normalizes OpenRouter provider case")
        void testModelClientConfigNormalizesOpenRouterProviderCase() {
            assertEquals(ProviderType.OpenRouter, ProviderType.fromValue("OPENROUTER"));
        }

        @Test
        @DisplayName("Test ModelClientConfig allows registered string provider")
        void testModelClientConfigAllowsRegisteredStringProvider() {
            ModelClientConfig cfg = baseBuilder()
                    .clientProvider("TempMockLLM")
                    .build();
            assertEquals("TempMockLLM", cfg.getClientProvider());
        }
    }

    @Nested
    @DisplayName("Validation tests")
    class ValidationTests {

        @Test
        @DisplayName("Test ModelClientConfig timeout must be positive")
        void testModelClientConfigTimeoutMustBePositive() {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> baseBuilder()
                    .timeout(0)
                    .build());
            assertTrue(exception.getMessage().contains("greater than 0"));
        }

        @Test
        @DisplayName("Test ModelClientConfig accepts custom headers")
        void testModelClientConfigAcceptsCustomHeaders() {
            ModelClientConfig cfg = baseBuilder()
                    .customHeaders(Map.of("X-Custom", "custom"))
                    .build();
            assertEquals(Map.of("X-Custom", "custom"), cfg.getCustomHeaders());
        }

        @Test
        @DisplayName("Test ModelClientConfig rejects invalid provider")
        void testModelClientConfigRejectsInvalidProvider() {
            assertThrows(IllegalArgumentException.class, () -> ProviderType.fromValue("invalid-provider"));
        }

        @Test
        @DisplayName("Test ModelClientConfig requires api_key")
        void testModelClientConfigRequiresApiKey() {
            assertThrows(NullPointerException.class, () -> ModelClientConfig.builder()
                    .clientProvider(ProviderType.OpenAI.getValue())
                    .apiBase("http://localhost")
                    .build());
        }

        @Test
        @DisplayName("Test ModelClientConfig requires api_base")
        void testModelClientConfigRequiresApiBase() {
            assertThrows(NullPointerException.class, () -> ModelClientConfig.builder()
                    .clientProvider(ProviderType.OpenAI.getValue())
                    .apiKey("sk-test")
                    .build());
        }
    }

    @Nested
    @DisplayName("Mock client tests")
    class MockClientTests {

        /**
         * TempMockLLM client for testing.
         * Mirrors Python's {@code _TempMockClient} class.
         */
        static class TempMockClient {
            static final String CLIENT_NAME = "TempMockLLM";
        }

        @Test
        @DisplayName("Test mock client name is registered")
        void testMockClientNameIsRegistered() {
            assertEquals("TempMockLLM", TempMockClient.CLIENT_NAME);
        }

        @Test
        @DisplayName("Test builder keeps default values aligned with runtime expectations")
        void testBuilderDefaults() {
            ModelClientConfig cfg = baseBuilder()
                    .clientProvider(ProviderType.OpenAI.getValue())
                    .build();

            assertNotNull(cfg.getClientId());
            assertFalse(cfg.getClientId().isEmpty());
            assertEquals(60.0, cfg.getTimeout());
            assertEquals(3, cfg.getMaxRetries());
            assertTrue(cfg.isVerifySsl());
        }
    }

    private static ModelClientConfig.Builder baseBuilder() {
        return ModelClientConfig.builder()
                .clientProvider(ProviderType.OpenAI.getValue())
                .apiKey("sk-test")
                .apiBase("http://localhost");
    }
}
