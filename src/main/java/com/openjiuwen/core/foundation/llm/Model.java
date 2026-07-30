/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.reactive.ReactiveAdapters;
import com.openjiuwen.core.foundation.llm.model_clients.DefaultModelClientFactories;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;

/**
 * Unified LLM invocation entry point.
 * <p>
 * Creates a {@link BaseModelClient} based on {@link ModelClientConfig#getClientProvider()}
 * and delegates all calls to it.
 * <p>
 * Mirrors Python's {@code Model} class.
 * <p>
 * Usage:
 * 
 * <pre>
 * Model model = new Model(modelClientConfig, modelRequestConfig);
 * AssistantMessage response = model.invoke("Hello", null, null, null, null, null, null, null, null, null);
 * </pre>
 * 
 * @since 0.1.7
 */
public class Model {
    /**
     * ModelClientFactory.
     * 
     * @since 0.1.7
     */
    public interface ModelClientFactory {
        /**
         * providerName.
         * 
         * @return the result
         * @since 0.1.7
         */
        String providerName();

        /**
         * Create a client instance.
         * 
         * @param modelConfig modelConfig
         * @param clientConfig clientConfig
         * @return the result
         * @since 0.1.7
         */
        BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig);
    }

    /**
     * Static registry populated from ServiceLoader + manual registration.
     * 
     * @since 0.1.7
     */
    private static final Map<String, ModelClientFactory> FACTORY_REGISTRY = new LinkedHashMap<>();

    static {
        for (ModelClientFactory f : ServiceLoader.load(ModelClientFactory.class)) {
            FACTORY_REGISTRY.put(f.providerName(), f);
        }
        DefaultModelClientFactories.ensureRegistered();
    }

    /**
     * Register a model client factory programmatically.
     * 
     * @param factory factory
     * @since 0.1.7
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
     * @param modelConfig model request parameters (modelName, temperature, topP, etc.)
     * @since 0.1.7
     */
    public Model(ModelClientConfig modelClientConfig, ModelRequestConfig modelConfig) {
        if (modelClientConfig == null) {
            throw ErrorHelper.buildError(StatusCode.MODEL_SERVICE_CONFIG_ERROR, "error_msg",
                    "model client config is none");
        }
        this.modelClientConfig = modelClientConfig;
        this.modelConfig = modelConfig;
        this.client = createModelClient(modelClientConfig);
    }

    /**
     * Construct a Model wrapper that shares the source model client.
     *
     * @param source source model
     * @since 0.1.13
     */
    protected Model(Model source) {
        this.modelClientConfig = source.modelClientConfig;
        this.modelConfig = source.modelConfig;
        this.client = source.client;
    }

    /**
     * Run a request with tracing scoped to this model client.
     *
     * @param callback request trace callback
     * @param request model request
     * @param <T> request result type
     * @return model request result
     * @throws Exception model request failure
     * @since 0.1.13
     */
    protected final <T> T withRequestTraceCallback(BaseModelClient.ModelRequestTraceCallback callback,
            Callable<T> request) throws Exception {
        return client.withRequestTraceCallback(callback, request);
    }

    /**
     * getModelConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    public ModelRequestConfig getModelConfig() {
        return modelConfig;
    }

    /**
     * getModelClientConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    public ModelClientConfig getModelClientConfig() {
        return modelClientConfig;
    }

    /**
     * createModelClient.
     * 
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    private BaseModelClient createModelClient(ModelClientConfig config) {
        if (config.getClientProvider() == null) {
            throw ErrorHelper.buildError(StatusCode.MODEL_SERVICE_CONFIG_ERROR, "error_msg",
                    "model client config client_provider is none");
        }
        if (config.getClientId() == null) {
            throw ErrorHelper.buildError(StatusCode.MODEL_SERVICE_CONFIG_ERROR, "error_msg",
                    "model client config client_id is none");
        }
        String provider = config.getClientProvider().strip();
        ModelClientFactory factory = FACTORY_REGISTRY.get(provider);
        if (factory == null) {
            // Case-insensitive fallback: match "openai" -> "OpenAI", etc.
            String lowerProvider = provider.toLowerCase(Locale.ROOT);
            for (Map.Entry<String, ModelClientFactory> entry : FACTORY_REGISTRY.entrySet()) {
                if (entry.getKey().toLowerCase(Locale.ROOT).equals(lowerProvider)) {
                    factory = entry.getValue();
                    break;
                }
            }
        }
        if (factory == null) {
            throw ErrorHelper.buildError(StatusCode.MODEL_PROVIDER_INVALID, "error_msg", "unavailable model provider: "
                    + config.getClientProvider() + ",and available providers are: " + FACTORY_REGISTRY.keySet());
        }
        return factory.create(modelConfig, config);
    }

    // ==================== Delegation Methods ====================

    /**
     * invoke.
     * 
     * @param messages messages
     * @param tools tools
     * @param temperature temperature
     * @param topP topP
     * @param model model
     * @param maxTokens maxTokens
     * @param stop stop
     * @param outputParser outputParser
     * @param timeout timeout
     * @param kwargs kwargs
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP, String model,
            Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout, Map<String, Object> kwargs)
            throws Exception {
        return client.invoke(messages, tools, temperature, topP, model, maxTokens, stop, outputParser, timeout, kwargs);
    }

    /**
     * stream.
     * 
     * @param messages messages
     * @param tools tools
     * @param temperature temperature
     * @param topP topP
     * @param model model
     * @param maxTokens maxTokens
     * @param stop stop
     * @param outputParser outputParser
     * @param timeout timeout
     * @param kwargs kwargs
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP,
            String model, Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout,
            Map<String, Object> kwargs) throws Exception {
        return client.stream(messages, tools, temperature, topP, model, maxTokens, stop, outputParser, timeout, kwargs);
    }

    /**
     * generateImage.
     * 
     * @param messages messages
     * @param model model
     * @param size size
     * @param negativePrompt negativePrompt
     * @param n n
     * @param promptExtend promptExtend
     * @param watermark watermark
     * @param seed seed
     * @param kwargs kwargs
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public ImageGenerationResponse generateImage(List<UserMessage> messages, String model, String size,
            String negativePrompt, int n, boolean promptExtend, boolean watermark, int seed, Map<String, Object> kwargs)
            throws Exception {
        return client.generateImage(messages, model, size, negativePrompt, n, promptExtend, watermark, seed, kwargs);
    }

    /**
     * generateSpeech.
     * 
     * @param messages messages
     * @param model model
     * @param voice voice
     * @param languageType languageType
     * @param kwargs kwargs
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model, String voice,
            String languageType, Map<String, Object> kwargs) throws Exception {
        return client.generateSpeech(messages, model, voice, languageType, kwargs);
    }

    /**
     * generateVideo.
     * 
     * @param messages messages
     * @param imgUrl imgUrl
     * @param audioUrl audioUrl
     * @param model model
     * @param size size
     * @param resolution resolution
     * @param duration duration
     * @param promptExtend promptExtend
     * @param watermark watermark
     * @param negativePrompt negativePrompt
     * @param seed seed
     * @param kwargs kwargs
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl, String audioUrl,
            String model, String size, String resolution, int duration, boolean promptExtend, boolean watermark,
            String negativePrompt, Integer seed, Map<String, Object> kwargs) throws Exception {
        return client.generateVideo(messages, imgUrl, audioUrl, model, size, resolution, duration, promptExtend,
                watermark, negativePrompt, seed, kwargs);
    }

    /**
     * Reactive version of {@link #invoke(Object, Object, Float, Float, String, Integer, String, BaseOutputParser,
     * Float, Map)}.
     * 
     * @param messages input messages
     * @param tools available tools
     * @param temperature sampling temperature
     * @param topP nucleus sampling value
     * @param model model override
     * @param maxTokens maximum output tokens
     * @param stop stop sequence
     * @param outputParser output parser
     * @param timeout request timeout
     * @param kwargs extra provider arguments
     * @return Mono emitting the assistant message
     * @since 0.1.7
     */
    public Mono<AssistantMessage> invokeAsync(Object messages, Object tools, Float temperature, Float topP,
            String model, Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout,
            Map<String, Object> kwargs) {
        return ReactiveAdapters.fromCallable(() -> invoke(messages, tools, temperature, topP, model, maxTokens, stop,
                outputParser, timeout, kwargs));
    }

    /**
     * Reactive version of {@link #stream(Object, Object, Float, Float, String, Integer, String, BaseOutputParser,
     * Float, Map)}.
     * 
     * @param messages input messages
     * @param tools available tools
     * @param temperature sampling temperature
     * @param topP nucleus sampling value
     * @param model model override
     * @param maxTokens maximum output tokens
     * @param stop stop sequence
     * @param outputParser output parser
     * @param timeout request timeout
     * @param kwargs extra provider arguments
     * @return Flux emitting assistant message chunks
     * @since 0.1.7
     */
    public Flux<AssistantMessageChunk> streamAsync(Object messages, Object tools, Float temperature, Float topP,
            String model, Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout,
            Map<String, Object> kwargs) {
        return ReactiveAdapters.fromAutoCloseableIterator(() -> stream(messages, tools, temperature, topP, model,
                maxTokens, stop, outputParser, timeout, kwargs));
    }
}
