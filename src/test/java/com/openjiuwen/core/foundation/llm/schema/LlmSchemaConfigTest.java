/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import com.openjiuwen.core.common.clients.ClientRegistry;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Focused parity tests for model configuration schema.
 *
 * <p>Mirrors Python's {@code ProviderType}, {@code ModelClientConfig}, and
 * {@code ModelRequestConfig} in {@code openjiuwen/core/foundation/llm/schema/config.py}.</p>
 *
 * <p>Mirrors Python's model-client-config test coverage in
 * {@code tests/unit_tests/core/foundation/llm/test_model_client_config.py}.</p>
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

    @Test
    void modelClientConfigAcceptsSupportedProvidersFromMissingPythonTests() {
        ModelClientConfig openAi = ModelClientConfig.builder()
                .clientProvider(ProviderType.OPEN_AI)
                .apiKey("sk-test")
                .apiBase("http://localhost")
                .build();
        ModelClientConfig siliconFlow = ModelClientConfig.builder()
                .clientProvider(ProviderType.SILICON_FLOW)
                .apiKey("sk-test")
                .apiBase("http://localhost")
                .build();
        ModelClientConfig openRouter = ModelClientConfig.builder()
                .clientProvider(ProviderType.OPEN_ROUTER)
                .apiKey("sk-test")
                .apiBase("http://localhost")
                .build();

        assertEquals(ProviderType.OPEN_AI.getValue(), openAi.getClientProvider());
        assertEquals(ProviderType.SILICON_FLOW.getValue(), siliconFlow.getClientProvider());
        assertEquals(ProviderType.OPEN_ROUTER.getValue(), openRouter.getClientProvider());
    }

    @Test
    void modelClientConfigNormalizesOpenRouterProviderCaseFromMissingPythonTests() {
        ModelClientConfig config = ModelClientConfig.builder()
                .clientProvider("OPENROUTER")
                .apiKey("sk-test")
                .apiBase("http://localhost")
                .build();

        assertEquals(ProviderType.OPEN_ROUTER.getValue(), config.getClientProvider());
    }

    @Test
    void modelClientConfigAllowsRegisteredStringProviderFromMissingPythonTests() {
        String provider = "TempMockLLM";
        ClientRegistry registry = ClientRegistry.getClientRegistry();
        unregisterIfPresent(registry, provider);
        registry.registerClient(provider, "llm", kwargs -> new Object());
        try {
            ModelClientConfig config = ModelClientConfig.builder()
                    .clientProvider(provider)
                    .apiKey("sk-test")
                    .apiBase("http://localhost")
                    .build();

            assertEquals(provider, config.getClientProvider());
        } finally {
            unregisterIfPresent(registry, provider);
        }
    }

    @Test
    void modelClientConfigTimeoutMustBePositiveFromMissingPythonTests() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> ModelClientConfig.builder()
                .clientProvider(ProviderType.OPEN_AI)
                .apiKey("sk-test")
                .apiBase("http://localhost")
                .timeout(0.0D)
                .build());

        assertEquals("timeout must be greater than 0 (greater_than)", error.getMessage());
    }

    @Test
    void modelClientConfigAcceptsCustomHeadersFromMissingPythonTests() {
        Map<String, Object> customHeaders = new LinkedHashMap<>();
        customHeaders.put("X-Custom", "custom");

        ModelClientConfig config = ModelClientConfig.builder()
                .clientProvider(ProviderType.OPEN_AI)
                .apiKey("sk-test")
                .apiBase("http://localhost")
                .customHeaders(customHeaders)
                .build();

        assertEquals(Map.of("X-Custom", "custom"), config.getCustomHeaders());
    }

    @Test
    void modelClientConfigPreservesHttpVersion() {
        ModelClientConfig configured = ModelClientConfig.builder()
                .clientProvider(ProviderType.OPEN_AI)
                .apiKey("sk-test")
                .apiBase("http://localhost")
                .httpVersion(ModelHttpVersion.HTTP_1_1)
                .build();
        ModelClientConfig unset = ModelClientConfig.builder()
                .clientProvider(ProviderType.OPEN_AI)
                .apiKey("sk-test")
                .apiBase("http://localhost")
                .build();

        assertEquals(ModelHttpVersion.HTTP_1_1, configured.getHttpVersion());
        assertEquals(null, unset.getHttpVersion());
    }

    @Test
    void jacksonReadsHttpVersionAliases() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        ModelClientConfig clientConfig = mapper.readValue("""
                {"client_provider":"OpenAI","api_key":"sk-test","api_base":"http://localhost","http_version":"HTTP/1.1"}
                """, ModelClientConfig.class);
        BaseModelInfo modelInfo = mapper.readValue("""
                {"model_name":"gpt-test","http_version":"2"}
                """, BaseModelInfo.class);

        assertEquals(ModelHttpVersion.HTTP_1_1, clientConfig.getHttpVersion());
        assertEquals(ModelHttpVersion.HTTP_2, modelInfo.getHttpVersion());
        assertEquals(false, modelInfo.getExtraFields().containsKey("http_version"));
    }

    @Test
    void baseModelInfoKeepsOldAllArgsConstructorSignature() {
        Map<String, Object> customHeaders = new LinkedHashMap<>();
        customHeaders.put("X-Trace", "trace-1");
        Map<String, Object> extraFields = new LinkedHashMap<>();
        extraFields.put("extra", "value");

        BaseModelInfo modelInfo = new BaseModelInfo(
                "sk-test",
                "http://localhost",
                "gpt-test",
                0.2d,
                0.8d,
                true,
                30,
                customHeaders,
                extraFields);

        assertEquals("sk-test", modelInfo.getApiKey());
        assertEquals("http://localhost", modelInfo.getApiBase());
        assertEquals("gpt-test", modelInfo.getModelName());
        assertEquals(0.2d, modelInfo.getTemperature());
        assertEquals(0.8d, modelInfo.getTopP());
        assertEquals(true, modelInfo.isStreaming());
        assertEquals(30, modelInfo.getTimeout());
        assertEquals(customHeaders, modelInfo.getCustomHeaders());
        assertEquals(null, modelInfo.getHttpVersion());
        assertEquals(extraFields, modelInfo.getExtraFields());
    }

    private static void unregisterIfPresent(ClientRegistry registry, String provider) {
        if (registry.listClients().contains("llm_" + provider)) {
            registry.unregister(provider, "llm");
        }
    }
}
