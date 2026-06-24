/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.clients.ClientRegistry;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ProviderType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Focused parity tests for the model clients package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.foundation.llm.model_clients} module in
 * {@code openjiuwen/core/foundation/llm/model_clients/__init__.py}.</p>
 */
class ModelClientsTest {

    @Test
    void exposesPythonModuleLedger() {
        assertThat(ModelClients.PYTHON_MODULE)
                .isEqualTo("openjiuwen/core/foundation/llm/model_clients/__init__.py");
        assertThat(ModelClients.moduleSymbols()).containsExactly(
                "BaseModelClient",
                "_builtin_model_client",
                "create_model_client"
        );
        assertThat(ModelClients.publicExports()).containsExactly(
                "BaseModelClient",
                "create_model_client"
        );
        assertThat(ModelClients.builtinProviderNames()).containsExactly(
                "OpenAI",
                "OpenRouter",
                "SiliconFlow",
                "DashScope",
                "InferenceAffinity",
                "DeepSeek",
                "intelli_router"
        );
    }

    @Test
    void builtInProviderFactoryTakesPrecedenceOverRegistryFallback() {
        Object sentinel = new Object();
        ModelRequestConfig modelConfig = ModelRequestConfig.builder().modelName("test-model").build();
        ModelClientConfig clientConfig = ModelClientConfig.of(ProviderType.INTELLI_ROUTER, "api-key", "api-base");

        ModelClients.registerBuiltinProvider(ProviderType.INTELLI_ROUTER, (model, client) -> {
            assertSame(modelConfig, model);
            assertSame(clientConfig, client);
            return sentinel;
        });
        try {
            assertSame(sentinel, ModelClients.createModelClient(clientConfig, modelConfig));
        } finally {
            ModelClients.unregisterBuiltinProvider(ProviderType.INTELLI_ROUTER.getValue());
        }
    }

    @Test
    void openAiProviderCreatesRawOpenAiClient() {
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider("OpenAI")
                .apiKey("test-key")
                .apiBase("http://localhost:1/v1")
                .build();
        ModelRequestConfig modelConfig = ModelRequestConfig.builder().modelName("gpt-test").build();

        Object client = ModelClients.createModelClient(clientConfig, modelConfig);

        assertThat(client).isInstanceOf(OpenAIModelClient.class);
    }

    @Test
    void openAiRawProviderIsRejectedByBuilderAndJsonParsing() {
        assertThatThrownBy(() -> ModelClientConfig.builder()
                .clientProvider("OpenAIRaw")
                .apiKey("test-key")
                .apiBase("http://localhost:1/v1")
                .build())
                .hasMessageContaining("unavailable model provider: OpenAIRaw");

        assertThatThrownBy(() -> new ObjectMapper().readValue(
                "{\"client_provider\":\"OpenAIRaw\",\"api_key\":\"test-key\",\"api_base\":\"http://localhost:1/v1\"}",
                ModelClientConfig.class))
                .hasMessageContaining("unavailable model provider: OpenAIRaw");
    }

    @Test
    void openRouterStillUsesRawClient() {
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider("OpenRouter")
                .apiKey("test-key")
                .apiBase("http://localhost:1/v1")
                .build();
        ModelRequestConfig modelConfig = ModelRequestConfig.builder().modelName("openrouter-test").build();

        Object client = ModelClients.createModelClient(clientConfig, modelConfig);

        assertThat(client).isInstanceOf(OpenAIModelClient.class);
    }

    @Test
    void openAiRawRejectsUnsupportedAliases() {
        assertThatThrownBy(() -> ModelClientConfig.builder().clientProvider("openai-raw").build())
                .hasMessageContaining("unavailable model provider: openai-raw");
        assertThatThrownBy(() -> ModelClientConfig.builder().clientProvider("OpenAICompatible").build())
                .hasMessageContaining("unavailable model provider: OpenAICompatible");
    }

    @Test
    void rejectsMissingClientProviderAndClientIdLikePythonFacade() {
        ModelRequestConfig modelConfig = ModelRequestConfig.builder().build();
        ModelClientConfig missingProvider = new ModelClientConfig();
        missingProvider.setClientId("client-1");
        ModelClientConfig missingClientId = configWithoutProviderValidation(null, ProviderType.OPEN_AI.getValue());

        assertThatThrownBy(() -> ModelClients.createModelClient(missingProvider, modelConfig))
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.MODEL_SERVICE_CONFIG_ERROR);
        assertThatThrownBy(() -> ModelClients.createModelClient(missingClientId, modelConfig))
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.MODEL_SERVICE_CONFIG_ERROR);
    }

    @Test
    void fallsBackToLlmRegistryWithPythonKwargs() {
        Object sentinel = new Object();
        String provider = "t00959_custom_provider";
        ModelRequestConfig modelConfig = ModelRequestConfig.builder().modelName("custom-model").build();
        ClientRegistry registry = ClientRegistry.getClientRegistry();
        ModelClientConfig[] capturedConfig = new ModelClientConfig[1];

        registry.registerClient(provider, "llm", kwargs -> {
            assertThat(kwargs).containsEntry("model_config", modelConfig);
            assertThat(kwargs).containsEntry("model_client_config", capturedConfig[0]);
            return sentinel;
        });
        try {
            ModelClientConfig clientConfig = ModelClientConfig.builder()
                    .clientId("client-1")
                    .clientProvider(provider)
                    .build();
            capturedConfig[0] = clientConfig;
            assertSame(sentinel, ModelClients.createModelClient(clientConfig, modelConfig));
        } finally {
            registry.unregister(provider, "llm");
        }
    }

    @Test
    void wrapsUnknownRegistryProviderAsModelProviderInvalid() {
        ModelRequestConfig modelConfig = ModelRequestConfig.builder().build();
        ModelClientConfig clientConfig = configWithoutProviderValidation("client-1", "t00959_missing_provider");

        assertThatThrownBy(() -> ModelClients.createModelClient(clientConfig, modelConfig))
                .isInstanceOf(BaseError.class)
                .extracting(error -> (BaseError) error)
                .satisfies(error -> {
                    assertThat(error.getStatus()).isEqualTo(StatusCode.MODEL_PROVIDER_INVALID);
                    assertThat(error.getMessage()).contains("Unsupported client_provider: 't00959_missing_provider'");
                });
    }

    @Test
    void exposesExactPythonFallbackKwargs() {
        ModelRequestConfig modelConfig = ModelRequestConfig.builder().modelName("m").build();
        ModelClientConfig clientConfig = configWithoutProviderValidation("c", "p");

        Map<String, Object> kwargs = ModelClients.modelClientKwargs(modelConfig, clientConfig);

        assertThat(kwargs).containsOnlyKeys("model_config", "model_client_config");
        assertSame(modelConfig, kwargs.get("model_config"));
        assertSame(clientConfig, kwargs.get("model_client_config"));
    }

    private static ModelClientConfig configWithoutProviderValidation(String clientId, String clientProvider) {
        return new ModelClientConfig(
                clientId,
                clientProvider,
                "api-key",
                "api-base",
                60.0,
                3,
                true,
                null,
                Map.of(),
                Map.of());
    }
}
