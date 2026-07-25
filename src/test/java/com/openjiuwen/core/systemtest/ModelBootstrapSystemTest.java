/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.systemtest;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Local system tests for Model bootstrap and provider registration.
 */
@Tag("system-test")
class ModelBootstrapSystemTest {

    @Test
    @DisplayName("Built-in OpenAI-compatible providers bootstrap without test-side registration")
    void testBuiltInProviderRegistration() {
        ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                .modelName("bootstrap-model")
                .temperature(0.7)
                .topP(0.9)
                .maxTokens(64)
                .build();

        for (String provider : List.of("OpenAI", "OpenRouter", "SiliconFlow", "DashScope")) {
            assertDoesNotThrow(
                    () -> new Model(newClientConfig(provider), requestConfig),
                    () -> "Provider should bootstrap without test helper registration: " + provider);
        }
    }

    private static ModelClientConfig newClientConfig(String provider) {
        return ModelClientConfig.builder()
                .clientProvider(provider)
                .apiKey("test-key")
                .apiBase("https://example.com/v1")
                .timeout(10.0)
                .maxRetries(1)
                .verifySsl(false)
                .build();
    }
}
