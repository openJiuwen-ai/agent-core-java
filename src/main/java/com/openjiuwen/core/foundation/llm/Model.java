/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.model_clients.DefaultModelClientFactories;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.model_clients.InferenceAffinityModelClient;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Unified LLM invocation entry point.
 * <p>
 * Creates a {@link BaseModelClient} based on {@link ModelClientConfig#getClientProvider()}
 * and delegates all calls to it.
 * <p>
 * Mirrors Python's {@code Model} class.
 *
 * <p>Usage:
 * <pre>
 *   Model model = new Model(modelClientConfig, modelRequestConfig);
 *   AssistantMessage response = model.invoke("Hello", null, null, null, null, null, null, null, null, null);
 * </pre>
 */
public class Model {

    /**
     * SPI-based registry for model client factories.
     * <p>
     * Implementations of {@link ModelClientFactory} are discovered via
     * {@link ServiceLoader}. Each factory declares which {@code clientProvider}
     * name it supports.
     */
    public interface ModelClientFactory {
        /** The provider name this factory handles (e.g., "OpenAI", "DashScope"). */
        String providerName();

        /** Create a client instance. */
        BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig);
    }

    /** Static registry populated from ServiceLoader + manual registration. */
    private static final Map<String, ModelClientFactory> FACTORY_REGISTRY = new LinkedHashMap<>();

    static {
        for (ModelClientFactory f : ServiceLoader.load(ModelClientFactory.class)) {
            FACTORY_REGISTRY.put(f.providerName(), f);
        }
        DefaultModelClientFactories.ensureRegistered();
    }

    /**
     * Register a model client factory programmatically.
     */
    public static void registerFactory(ModelClientFactory factory) {
        FACTORY_REGISTRY.put(factory.providerName(), factory);
    }

    private final ModelRequestConfig modelConfig;
    private final ModelClientConfig modelClientConfig;
    private final BaseModelClient client;

    /**
     * Construct a Model.
     *
     * @param modelClientConfig client configuration (apiKey, apiBase, clientProvider, etc.)
     * @param modelConfig       model request parameters (modelName, temperature, topP, etc.)
     */
    public Model(ModelClientConfig modelClientConfig, ModelRequestConfig modelConfig) {
        if (modelClientConfig == null) {
            throw ErrorHelper.buildError(StatusCode.MODEL_SERVICE_CONFIG_ERROR,
                    "error_msg", "model client config is none");
        }
        this.modelClientConfig = modelClientConfig;
        this.modelConfig = modelConfig;
        this.client = createModelClient(modelClientConfig);
    }

    private BaseModelClient createModelClient(ModelClientConfig config) {
        if (config.getClientProvider() == null) {
            throw ErrorHelper.buildError(StatusCode.MODEL_SERVICE_CONFIG_ERROR,
                    "error_msg", "model client config client_provider is none");
        }
        if (config.getClientId() == null) {
            throw ErrorHelper.buildError(StatusCode.MODEL_SERVICE_CONFIG_ERROR,
                    "error_msg", "model client config client_id is none");
        }
        String provider = config.getClientProvider().strip();
        ModelClientFactory factory = FACTORY_REGISTRY.get(provider);
        if (factory == null) {
            // Case-insensitive fallback: match "openai" -> "OpenAI", etc.
            String lowerProvider = provider.toLowerCase();
            for (Map.Entry<String, ModelClientFactory> entry : FACTORY_REGISTRY.entrySet()) {
                if (entry.getKey().toLowerCase().equals(lowerProvider)) {
                    factory = entry.getValue();
                    break;
                }
            }
        }
        if (factory == null) {
            throw ErrorHelper.buildError(StatusCode.MODEL_PROVIDER_INVALID,
                    "error_msg", "unavailable model provider: " + config.getClientProvider()
                            + ",and available providers are: " + FACTORY_REGISTRY.keySet());
        }
        return factory.create(modelConfig, config);
    }

    // ==================== Delegation Methods ====================

    public AssistantMessage invoke(Object messages,
                                  Object tools,
                                  Float temperature,
                                  Float topP,
                                  String model,
                                  Integer maxTokens,
                                  String stop,
                                  BaseOutputParser outputParser,
                                  Float timeout,
                                  Map<String, Object> kwargs) throws Exception {
        return client.invoke(messages, tools, temperature, topP, model, maxTokens,
                stop, outputParser, timeout, kwargs);
    }

    public Iterator<AssistantMessageChunk> stream(Object messages,
                                                  Object tools,
                                                  Float temperature,
                                                  Float topP,
                                                  String model,
                                                  Integer maxTokens,
                                                  String stop,
                                                  BaseOutputParser outputParser,
                                                  Float timeout,
                                                  Map<String, Object> kwargs) throws Exception {
        return client.stream(messages, tools, temperature, topP, model, maxTokens,
                stop, outputParser, timeout, kwargs);
    }

    /**
     * Whether the underlying client supports inference-affinity KV cache release.
     */
    public boolean supportsKvCacheRelease() {
        return client instanceof InferenceAffinityModelClient;
    }

    /**
     * Release stale KV cache for clients that support cache sharing.
     */
    public boolean release(String sessionId,
                           List<?> messages,
                           int messagesReleasedIndex,
                           List<?> tools,
                           Integer toolsReleasedIndex,
                           String model) throws Exception {
        if (client instanceof InferenceAffinityModelClient inferenceAffinityClient) {
            return inferenceAffinityClient.release(sessionId, messages, messagesReleasedIndex,
                    tools, toolsReleasedIndex, model);
        }
        throw new UnsupportedOperationException("Model client does not support KV cache release");
    }

    public ImageGenerationResponse generateImage(List<UserMessage> messages,
                                                 String model,
                                                 String size,
                                                 String negativePrompt,
                                                 int n,
                                                 boolean promptExtend,
                                                 boolean watermark,
                                                 int seed,
                                                 Map<String, Object> kwargs) throws Exception {
        return client.generateImage(messages, model, size, negativePrompt, n,
                promptExtend, watermark, seed, kwargs);
    }

    public AudioGenerationResponse generateSpeech(List<UserMessage> messages,
                                                  String model,
                                                  String voice,
                                                  String languageType,
                                                  Map<String, Object> kwargs) throws Exception {
        return client.generateSpeech(messages, model, voice, languageType, kwargs);
    }

    public VideoGenerationResponse generateVideo(List<UserMessage> messages,
                                                 String imgUrl,
                                                 String audioUrl,
                                                 String model,
                                                 String size,
                                                 String resolution,
                                                 int duration,
                                                 boolean promptExtend,
                                                 boolean watermark,
                                                 String negativePrompt,
                                                 Integer seed,
                                                 Map<String, Object> kwargs) throws Exception {
        return client.generateVideo(messages, imgUrl, audioUrl, model, size, resolution,
                duration, promptExtend, watermark, negativePrompt, seed, kwargs);
    }
}
