/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Focused parity tests for model configuration schema.
 *
 * <p>Mirrors Python's {@code ProviderType}, {@code ModelClientConfig}, and
 * {@code ModelRequestConfig} in {@code openjiuwen/core/foundation/llm/schema/config.py}.</p>
 */
class LlmSchemaConfigTest {

    @Test
    void preservesProviderTypeValues() {
        assertEquals("OpenAI", ProviderType.OPEN_AI.getValue());
        assertEquals("InferenceAffinity", ProviderType.INFERENCE_AFFINITY.getValue());
        assertEquals("IntelliRouter", ProviderType.INTELLI_ROUTER.getPythonMemberName());
        assertEquals("intelli_router", ProviderType.INTELLI_ROUTER.getValue());
    }

    @Test
    void normalizesProviderStringsLikePythonValidator() {
        assertEquals("OpenAI", ModelClientConfig.normalizeClientProvider("openai"));
        assertEquals("SiliconFlow", ModelClientConfig.normalizeClientProvider(" siliconflow "));
        assertEquals("IntelliRouter", ModelClientConfig.normalizeClientProvider("IntelliRouter"));
        assertEquals("intelli_router", ModelClientConfig.normalizeClientProvider("intelli_router"));

        ModelClientConfig config = ModelClientConfig.builder()
                .clientProvider("deepseek")
                .apiKey("key")
                .apiBase("https://example.test")
                .build();

        assertEquals("DeepSeek", config.getClientProvider());
        assertNotNull(config.getClientId());
    }

    @Test
    void acceptsProviderEnumInBuilderAndFactory() {
        ModelClientConfig built = ModelClientConfig.builder()
                .clientProvider(ProviderType.INTELLI_ROUTER)
                .apiKey("key")
                .apiBase("https://example.test")
                .build();
        ModelClientConfig factory = ModelClientConfig.of(ProviderType.OPEN_AI, "key", "https://example.test");

        assertEquals("intelli_router", built.getClientProvider());
        assertEquals("OpenAI", factory.getClientProvider());
    }

    @Test
    void rejectsUnavailableProviderWithModelProviderInvalidStatus() {
        BaseError error = assertThrows(BaseError.class,
                () -> ModelClientConfig.normalizeClientProvider("missing-provider"));

        assertEquals(StatusCode.MODEL_PROVIDER_INVALID, error.getStatus());
    }

    @Test
    void preservesRequestConfigDefaultsAndAliases() {
        ModelRequestConfig config = new ModelRequestConfig();

        assertEquals("", config.getModelName());
        assertEquals(0.95, config.getTemperature());
        assertEquals(0.1, config.getTopP());
    }
}
