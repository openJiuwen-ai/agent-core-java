/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.core.foundation.llm;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelFactoryRegistrationTest {

    @Test
    @DisplayName("Built-in OpenAI-compatible providers are available without manual registration")
    void testBuiltInProvidersAreRegistered() {
        ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                .modelName("demo-model")
                .build();

        for (String provider : List.of("OpenAI", "OpenRouter", "SiliconFlow", "DashScope")) {
            assertDoesNotThrow(
                    () -> new Model(newClientConfig(provider), requestConfig),
                    () -> "Provider should be available by default: " + provider);
        }
    }

    @Test
    @DisplayName("Unsupported provider still reports the registered provider set")
    void testUnsupportedProviderErrorListsBuiltInProviders() {
        BaseError error = assertThrows(
                BaseError.class,
                () -> new Model(newClientConfig("UnknownProvider"), ModelRequestConfig.builder().build()));

        assertTrue(error.getMessage().contains("OpenAI"));
        assertTrue(error.getMessage().contains("OpenRouter"));
        assertTrue(error.getMessage().contains("SiliconFlow"));
        assertTrue(error.getMessage().contains("DashScope"));
    }

    private static ModelClientConfig newClientConfig(String provider) {
        return ModelClientConfig.builder()
                .clientProvider(provider)
                .apiKey("test-key")
                .apiBase("https://example.com/v1")
                .verifySsl(false)
                .build();
    }
}
