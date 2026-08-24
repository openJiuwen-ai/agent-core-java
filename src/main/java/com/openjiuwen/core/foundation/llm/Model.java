/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.clients.ClientRegistry;
import com.openjiuwen.core.common.reactive.ReactiveAdapters;
import com.openjiuwen.core.context.context.KVCacheManager;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.model_clients.ModelClients;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.runner.callback.CallbackDecorators;
import com.openjiuwen.core.runner.callback.DecoratorFramework;
import com.openjiuwen.core.runner.callback.LLMCallEvents;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified LLM invocation entry point.
 *
 * <p>Mirrors Python's {@code Model} in
 * {@code openjiuwen/core/foundation/llm/model.py}.</p>
 */
public class Model implements KVCacheManager.ReleaseCapableModel {

    /**
     * Invoke-only delegate used by existing Java callers.
     *
     * <p>Mirrors Python's wrapped {@code self._client.invoke} in
     * {@code openjiuwen/core/foundation/llm/model.py}.</p>
     */
    @FunctionalInterface
    public interface ModelInvoker {
        CompletionStage<AssistantMessage> invoke(
                List<BaseMessage> messages,
                ModelRequestConfig modelConfig,
                ModelClientConfig modelClientConfig,
                ModelInvokeOptions options
        );
    }

    /**
     * Factory for provider-specific clients.
     *
     * <p>Mirrors Python's {@code create_model_client(...)} call in
     * {@code openjiuwen/core/foundation/llm/model.py}.</p>
     */
    @FunctionalInterface
    public interface ModelClientFactory {
        ModelClient create(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig);

        default String providerName() {
            return null;
        }

        default ModelClient create(ModelClientConfig modelClientConfig, ModelRequestConfig modelConfig) {
            return create(modelConfig, modelClientConfig);
        }
    }

    /**
     * Delegate used by this facade.
     *
     * <p>Mirrors Python's {@code BaseModelClient} collaborator in
     * {@code openjiuwen/core/foundation/llm/model.py}.</p>
     */
    public interface ModelClient {
        CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options);

        default Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages, ModelInvokeOptions options) {
            throw new IllegalStateException("Model client lacks stream support");
        }

        default CompletionStage<Boolean> release(String sessionId, List<BaseMessage> messages,
                                                 Integer messagesReleasedIndex, List<ToolInfo> tools,
                                                 Integer toolsReleasedIndex) {
            return CompletableFuture.completedFuture(false);
        }

        default boolean supportsKvCacheRelease() {
            return false;
        }

        default CompletionStage<ImageGenerationResponse> generateImage(List<UserMessage> messages,
                                                                       ImageGenerationOptions options) {
            return failedUnsupported("generate_image");
        }

        default CompletionStage<AudioGenerationResponse> generateSpeech(List<UserMessage> messages,
                                                                        SpeechGenerationOptions options) {
            return failedUnsupported("generate_speech");
        }

        default CompletionStage<VideoGenerationResponse> generateVideo(List<UserMessage> messages,
                                                                       VideoGenerationOptions options) {
            return failedUnsupported("generate_video");
        }

        private static <T> CompletionStage<T> failedUnsupported(String methodName) {
            CompletableFuture<T> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("Model client lacks " + methodName + " support"));
            return failed;
        }
    }

    /**
     * Image generation keyword arguments.
     *
     * <p>Mirrors Python's {@code Model.generate_image(...)} parameters in
     * {@code openjiuwen/core/foundation/llm/model.py}.</p>
     */
    public record ImageGenerationOptions(String model, String size, String negativePrompt, int n,
                                         boolean promptExtend, boolean watermark, int seed,
                                         Map<String, Object> extraFields) {
    }

    /**
     * Speech generation keyword arguments.
     *
     * <p>Mirrors Python's {@code Model.generate_speech(...)} parameters in
     * {@code openjiuwen/core/foundation/llm/model.py}.</p>
     */
    public record SpeechGenerationOptions(String model, String voice, String languageType,
                                          Map<String, Object> extraFields) {
    }

    /**
     * Video generation keyword arguments.
     *
     * <p>Mirrors Python's {@code Model.generate_video(...)} parameters in
     * {@code openjiuwen/core/foundation/llm/model.py}.</p>
     */
    public record VideoGenerationOptions(String imgUrl, String audioUrl, String model, String size,
                                         String resolution, int duration, boolean promptExtend, boolean watermark,
                                         String negativePrompt, Integer seed, Map<String, Object> extraFields) {
    }

    private static final Map<String, ModelInvoker> INVOKERS = new ConcurrentHashMap<>();
    private static final Map<String, ModelClientFactory> CLIENT_FACTORIES = new ConcurrentHashMap<>();
    @SuppressWarnings("unused")
    private static final Map<String, ModelClientFactory> FACTORY_REGISTRY = CLIENT_FACTORIES;

    private static DecoratorFramework callbackFramework;

    private final ModelRequestConfig modelConfig;
    private final ModelClientConfig modelClientConfig;
    private final ModelClient client;

    public Model(ModelClientConfig modelClientConfig, ModelRequestConfig modelConfig) {
        if (modelClientConfig == null) {
            throw ErrorHelper.buildError(
                    StatusCode.MODEL_SERVICE_CONFIG_ERROR,
                    "error_msg",
                    "model client config is none"
            );
        }
        this.modelClientConfig = modelClientConfig;
        this.modelConfig = modelConfig;
        this.client = createModelClient(modelClientConfig, modelConfig);
    }

    public Model(ModelInvoker invoker) {
        this(new InvokerBackedModelClient(invoker), null, null);
    }

    public Model(ModelClient client) {
        this(client, null, null);
    }

    public Model(ModelClient client, ModelClientConfig modelClientConfig, ModelRequestConfig modelConfig) {
        this.client = Objects.requireNonNull(client, "client");
        this.modelClientConfig = modelClientConfig;
        this.modelConfig = modelConfig;
    }

    public static void registerInvoker(String provider, ModelInvoker invoker) {
        if (provider == null || provider.isBlank() || invoker == null) {
            throw ErrorHelper.buildError(
                    StatusCode.MODEL_SERVICE_CONFIG_ERROR,
                    "error_msg",
                    "provider and invoker are required"
            );
        }
        removeProviderRegistration(CLIENT_FACTORIES, provider);
        INVOKERS.put(provider, invoker);
        exposeProviderToClientRegistry(provider);
    }

    /**
     * Remove a previously registered invoke-only provider so builtin HTTP clients can be used again.
     */
    public static void unregisterInvoker(String provider) {
        if (provider == null || provider.isBlank()) {
            return;
        }
        removeProviderRegistration(INVOKERS, provider);
    }

    public static void registerClientFactory(String provider, ModelClientFactory factory) {
        if (provider == null || provider.isBlank() || factory == null) {
            throw ErrorHelper.buildError(
                    StatusCode.MODEL_SERVICE_CONFIG_ERROR,
                    "error_msg",
                    "provider and client factory are required"
            );
        }
        removeProviderRegistration(INVOKERS, provider);
        CLIENT_FACTORIES.put(provider, factory);
        exposeProviderToClientRegistry(provider);
    }

    public static void registerFactory(ModelClientFactory factory) {
        if (factory == null || factory.providerName() == null || factory.providerName().isBlank()) {
            throw ErrorHelper.buildError(
                    StatusCode.MODEL_SERVICE_CONFIG_ERROR,
                    "error_msg",
                    "model client factory provider name is required"
            );
        }
        registerClientFactory(factory.providerName(), factory);
    }

    public static void setCallbackFramework(DecoratorFramework framework) {
        callbackFramework = framework;
    }

    public static void clearCallbackFramework() {
        callbackFramework = null;
    }

    public static Model initModel(String provider, String modelName, String apiKey, String apiBase) {
        return initModel(provider, modelName, apiKey, apiBase, 0.95f, 0.1f, null, 60.0f, 3,
                false, null);
    }

    public static Model init_model(String provider, String modelName, String apiKey, String apiBase) {
        return initModel(provider, modelName, apiKey, apiBase);
    }

    public static Model initModel(String provider, String modelName, String apiKey, String apiBase,
                                  float temperature, float topP, Integer maxTokens, float timeout,
                                  int maxRetries, boolean verifySsl, Map<String, String> customHeaders) {
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(provider)
                .apiKey(apiKey)
                .apiBase(apiBase)
                .timeout(timeout)
                .maxRetries(maxRetries)
                .verifySsl(verifySsl)
                .customHeaders(toObjectMap(customHeaders))
                .build();
        ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                .modelName(modelName)
                .temperature(temperature)
                .topP(topP)
                .maxTokens(maxTokens)
                .build();
        return new Model(clientConfig, requestConfig);
    }

    public static Model init_model(String provider, String modelName, String apiKey, String apiBase,
                                   float temperature, float topP, Integer maxTokens, float timeout,
                                   int maxRetries, boolean verifySsl, Map<String, String> customHeaders) {
        return initModel(provider, modelName, apiKey, apiBase, temperature, topP, maxTokens, timeout,
                maxRetries, verifySsl, customHeaders);
    }

    public CompletionStage<AssistantMessage> invoke(String message) {
        return invoke(message, ModelInvokeOptions.builder().build());
    }

    public CompletionStage<AssistantMessage> invoke(String message, ModelInvokeOptions options) {
        return invoke(List.of(new UserMessage(message)), options);
    }

    public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages) {
        return invoke(messages, ModelInvokeOptions.builder().build());
    }

    public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
        InvocationRequest request = prepareInvokeRequest(messages, options, LLMCallEvents.LLM_INVOKE_INPUT);
        CompletionStage<AssistantMessage> result = client.invoke(request.messages(), request.options());
        return result.thenApply(message -> transformOutput(LLMCallEvents.LLM_INVOKE_OUTPUT, request, message,
                AssistantMessage.class));
    }

    public AssistantMessage invoke(List<? extends BaseMessage> messages, List<?> tools, Float temperature, Float topP,
                                   Integer maxTokens, String stop, String model, BaseOutputParser outputParser,
                                   Float timeout, Map<String, Object> kwargs) {
        ModelInvokeOptions options = ModelInvokeOptions.builder()
                .tools(tools)
                .temperature(temperature)
                .topP(topP)
                .maxTokens(maxTokens)
                .stop(stop)
                .model(model)
                .outputParser(outputParser)
                .timeout(timeout)
                .extraFields(copyMap(kwargs))
                .build();
        return invoke(toBaseMessages(messages), options).toCompletableFuture().join();
    }

    public Iterator<AssistantMessageChunk> stream(String message) {
        return stream(message, ModelInvokeOptions.builder().build());
    }

    public Iterator<AssistantMessageChunk> stream(String message, ModelInvokeOptions options) {
        return stream(List.of(new UserMessage(message)), options);
    }

    public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages) {
        return stream(messages, ModelInvokeOptions.builder().build());
    }

    public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages, ModelInvokeOptions options) {
        InvocationRequest request = prepareInvokeRequest(messages, options, LLMCallEvents.LLM_STREAM_INPUT);
        Iterator<AssistantMessageChunk> iterator = client.stream(request.messages(), request.options());
        DecoratorFramework framework = callbackFramework;
        if (framework == null) {
            return iterator;
        }
        return new CallbackIterator(iterator, framework, request, modelConfig, modelClientConfig);
    }

    /**
     * Reactive version of {@link #invoke(List, ModelInvokeOptions)}.
     *
     * @param messages input messages
     * @param options invoke options
     * @return Mono emitting the assistant message
     */
    public Mono<AssistantMessage> invokeAsync(List<BaseMessage> messages, ModelInvokeOptions options) {
        return ReactiveAdapters.fromCompletionStage(invoke(messages, options));
    }

    /**
     * Reactive version of {@link #invoke(List)}.
     *
     * @param messages input messages
     * @return Mono emitting the assistant message
     */
    public Mono<AssistantMessage> invokeAsync(List<BaseMessage> messages) {
        return invokeAsync(messages, ModelInvokeOptions.builder().build());
    }

    /**
     * Reactive version of {@link #stream(List, ModelInvokeOptions)}.
     *
     * @param messages input messages
     * @param options stream options
     * @return Flux emitting assistant message chunks
     */
    public Flux<AssistantMessageChunk> streamAsync(List<BaseMessage> messages, ModelInvokeOptions options) {
        return ReactiveAdapters.fromAutoCloseableIterator(() -> stream(messages, options));
    }

    /**
     * Reactive version of {@link #stream(List)}.
     *
     * @param messages input messages
     * @return Flux emitting assistant message chunks
     */
    public Flux<AssistantMessageChunk> streamAsync(List<BaseMessage> messages) {
        return streamAsync(messages, ModelInvokeOptions.builder().build());
    }

    @Override
    public CompletionStage<Boolean> release(String sessionId, List<BaseMessage> messages,
                                            Integer messagesReleasedIndex, List<ToolInfo> tools,
                                            Integer toolsReleasedIndex) {
        return client.release(sessionId, safeMessages(messages), messagesReleasedIndex, tools,
                toolsReleasedIndex);
    }

    public CompletionStage<Boolean> release(String sessionId, List<BaseMessage> messages,
                                            Integer messagesReleasedIndex, String model,
                                            List<ToolInfo> tools, Integer toolsReleasedIndex) {
        return release(sessionId, messages, messagesReleasedIndex, tools, toolsReleasedIndex);
    }

    public boolean supportsKvCacheRelease() {
        return client.supportsKvCacheRelease();
    }

    public Map<String, Object> buildKvCacheInvokeKwargs(Object session, boolean enableKvCacheRelease) {
        if (!supportsKvCacheRelease()) {
            return Map.of();
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        Object sessionId = getSessionId(session);
        if (sessionId != null) {
            extra.put("session_id", sessionId);
        }
        if (enableKvCacheRelease) {
            extra.put("enable_cache_sharing", true);
        }
        return extra;
    }

    public CompletionStage<ImageGenerationResponse> generateImage(List<UserMessage> messages) {
        return generateImage(messages, null, "1664*928", null, 1, true, false, 0, null);
    }

    public CompletionStage<ImageGenerationResponse> generateImage(List<UserMessage> messages, String model,
                                                                  String size, String negativePrompt, int n,
                                                                  boolean promptExtend, boolean watermark, int seed,
                                                                  Map<String, Object> kwargs) {
        return client.generateImage(
                safeUserMessages(messages),
                new ImageGenerationOptions(model, size, negativePrompt, n, promptExtend, watermark, seed,
                        copyMap(kwargs))
        );
    }

    public CompletionStage<AudioGenerationResponse> generateSpeech(List<UserMessage> messages) {
        return generateSpeech(messages, null, "Cherry", "Auto", null);
    }

    public CompletionStage<AudioGenerationResponse> generateSpeech(List<UserMessage> messages, String model,
                                                                   String voice, String languageType,
                                                                   Map<String, Object> kwargs) {
        return client.generateSpeech(
                safeUserMessages(messages),
                new SpeechGenerationOptions(model, voice, languageType, copyMap(kwargs))
        );
    }

    public CompletionStage<VideoGenerationResponse> generateVideo(List<UserMessage> messages) {
        return generateVideo(messages, null, null, null, null, null, 5, true, false, null, null, null);
    }

    public CompletionStage<VideoGenerationResponse> generateVideo(List<UserMessage> messages, String imgUrl,
                                                                  String audioUrl, String model, String size,
                                                                  String resolution, int duration,
                                                                  boolean promptExtend, boolean watermark,
                                                                  String negativePrompt, Integer seed,
                                                                  Map<String, Object> kwargs) {
        return client.generateVideo(
                safeUserMessages(messages),
                new VideoGenerationOptions(imgUrl, audioUrl, model, size, resolution, duration,
                        promptExtend, watermark, negativePrompt, seed, copyMap(kwargs))
        );
    }

    public ModelRequestConfig getModelConfig() {
        return modelConfig;
    }

    public ModelClientConfig getModelClientConfig() {
        return modelClientConfig;
    }

    private static ModelClient createModelClient(ModelClientConfig clientConfig, ModelRequestConfig modelConfig) {
        String provider = clientConfig.getClientProvider();
        if (provider == null) {
            throw ErrorHelper.buildError(
                    StatusCode.MODEL_SERVICE_CONFIG_ERROR,
                    "error_msg",
                    "model client config client_provider is none"
            );
        }
        if (provider.isBlank()) {
            throw ErrorHelper.buildError(
                    StatusCode.MODEL_PROVIDER_INVALID,
                    "error_msg",
                    "unavailable model provider: " + provider + ",and available providers are: " + availableProviders()
            );
        }
        ModelClientFactory factory = resolveFactory(provider);
        if (factory != null) {
            return factory.create(clientConfig, modelConfig);
        }
        ModelInvoker invoker = resolveInvoker(provider);
        if (invoker != null) {
            return new InvokerBackedModelClient(invoker, modelConfig, clientConfig);
        }
        Object fallbackClient = ModelClients.createModelClient(clientConfig, modelConfig);
        return normalizeModelClient(fallbackClient);
    }

    private static ModelClient normalizeModelClient(Object clientCandidate) {
        Object builtinClient = clientCandidate;
        if (builtinClient instanceof ModelClient modelClient) {
            return modelClient;
        }
        if (builtinClient instanceof BaseModelClient baseModelClient) {
            return new BaseModelClientAdapter(baseModelClient);
        }
        throw ErrorHelper.buildError(
                StatusCode.MODEL_PROVIDER_INVALID,
                "error_msg",
                "model client factory returned unsupported client type: "
                        + (builtinClient == null ? "null" : builtinClient.getClass().getName())
        );
    }

    private static ModelClientFactory resolveFactory(String provider) {
        ModelClientFactory exact = CLIENT_FACTORIES.get(provider);
        if (exact != null) {
            return exact;
        }
        String normalized = provider.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, ModelClientFactory> entry : CLIENT_FACTORIES.entrySet()) {
            if (entry.getKey().toLowerCase(Locale.ROOT).equals(normalized)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static ModelInvoker resolveInvoker(String provider) {
        ModelInvoker exact = INVOKERS.get(provider);
        if (exact != null) {
            return exact;
        }
        String normalized = provider.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, ModelInvoker> entry : INVOKERS.entrySet()) {
            if (entry.getKey().toLowerCase(Locale.ROOT).equals(normalized)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static <T> void removeProviderRegistration(Map<String, T> registrations, String provider) {
        String normalized = provider.toLowerCase(Locale.ROOT);
        registrations.keySet().removeIf(key -> key.toLowerCase(Locale.ROOT).equals(normalized));
    }

    private static List<String> availableProviders() {
        List<String> providers = new ArrayList<>(CLIENT_FACTORIES.keySet());
        providers.addAll(INVOKERS.keySet());
        providers.addAll(ModelClients.builtinProviderNames());
        return providers;
    }

    private static void exposeProviderToClientRegistry(String provider) {
        ClientRegistry registry = ClientRegistry.getClientRegistry();
        if (registry.isRegistered(provider, "llm")) {
            return;
        }
        registry.registerClient(provider, "llm", kwargs -> {
            ModelRequestConfig modelRequestConfig = (ModelRequestConfig) kwargs.get("model_config");
            ModelClientConfig clientConfig = (ModelClientConfig) kwargs.get("model_client_config");
            ModelClientFactory factory = resolveFactory(provider);
            if (factory != null) {
                return factory.create(modelRequestConfig, clientConfig);
            }
            ModelInvoker invoker = resolveInvoker(provider);
            if (invoker != null) {
                return new InvokerBackedModelClient(invoker, modelRequestConfig, clientConfig);
            }
            throw new IllegalArgumentException("unavailable model provider: " + provider);
        });
    }

    private InvocationRequest prepareInvokeRequest(List<BaseMessage> messages, ModelInvokeOptions options,
                                                   String inputEvent) {
        List<BaseMessage> resolvedMessages = safeMessages(messages);
        ModelInvokeOptions resolvedOptions = options == null ? ModelInvokeOptions.builder().build() : options;
        DecoratorFramework framework = callbackFramework;
        if (framework == null) {
            return new InvocationRequest(resolvedMessages, resolvedOptions);
        }

        Map<String, Object> kwargs = invocationKwargs(resolvedMessages, resolvedOptions);
        framework.trigger(inputEvent, new Object[]{resolvedMessages}, withModelConfig(kwargs));
        Object transformed = framework.triggerTransform(inputEvent, new Object[]{resolvedMessages}, kwargs);
        if (transformed instanceof CallbackDecorators.BoundArgs boundArgs) {
            return requestFromKwargs(boundArgs.getKwargs(), resolvedMessages, resolvedOptions);
        }
        if (transformed instanceof Map<?, ?> transformedMap) {
            return requestFromMap(transformedMap, resolvedMessages, resolvedOptions);
        }
        return new InvocationRequest(resolvedMessages, resolvedOptions);
    }

    private AssistantMessage transformOutput(String outputEvent, InvocationRequest request, AssistantMessage message,
                                             Class<AssistantMessage> responseType) {
        DecoratorFramework framework = callbackFramework;
        if (framework == null) {
            return message;
        }
        Map<String, Object> outKwargs = new LinkedHashMap<>();
        outKwargs.put("result", message);
        Object transformed = framework.triggerTransform(outputEvent, new Object[0], outKwargs);
        AssistantMessage effectiveMessage = responseType.isInstance(transformed) ? responseType.cast(transformed) : message;
        Map<String, Object> afterKwargs = withModelConfig(invocationKwargs(request.messages(), request.options()));
        afterKwargs.put("result", effectiveMessage);
        framework.trigger(outputEvent, new Object[]{request.messages()}, afterKwargs);
        return effectiveMessage;
    }

    @SuppressWarnings("unchecked")
    private InvocationRequest requestFromMap(Map<?, ?> map, List<BaseMessage> fallbackMessages,
                                             ModelInvokeOptions fallbackOptions) {
        Map<String, Object> kwargs = new LinkedHashMap<>();
        map.forEach((key, value) -> kwargs.put(String.valueOf(key), value));
        return requestFromKwargs(kwargs, fallbackMessages, fallbackOptions);
    }

    @SuppressWarnings("unchecked")
    private InvocationRequest requestFromKwargs(Map<String, Object> kwargs, List<BaseMessage> fallbackMessages,
                                                ModelInvokeOptions fallbackOptions) {
        List<BaseMessage> transformedMessages = fallbackMessages;
        Object messagesValue = kwargs.get("messages");
        if (messagesValue instanceof List<?> list && list.stream().allMatch(BaseMessage.class::isInstance)) {
            transformedMessages = (List<BaseMessage>) list;
        }
        ModelInvokeOptions transformedOptions = fallbackOptions.toBuilder()
                .tools((List<?>) kwargs.getOrDefault("tools", fallbackOptions.getTools()))
                .temperature(numberAsFloat(kwargs.get("temperature"), fallbackOptions.getTemperature()))
                .topP(numberAsFloat(kwargs.get("top_p"), fallbackOptions.getTopP()))
                .maxTokens(numberAsInteger(kwargs.get("max_tokens"), fallbackOptions.getMaxTokens()))
                .stop((String) kwargs.getOrDefault("stop", fallbackOptions.getStop()))
                .model((String) kwargs.getOrDefault("model", fallbackOptions.getModel()))
                .outputParser((BaseOutputParser) kwargs.getOrDefault("output_parser",
                        fallbackOptions.getOutputParser()))
                .timeout(numberAsFloat(kwargs.get("timeout"), fallbackOptions.getTimeout()))
                .build();
        return new InvocationRequest(safeMessages(transformedMessages), transformedOptions);
    }

    private Map<String, Object> invocationKwargs(List<BaseMessage> messages, ModelInvokeOptions options) {
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("messages", messages);
        kwargs.put("tools", options.getTools());
        kwargs.put("temperature", options.getTemperature());
        kwargs.put("top_p", options.getTopP());
        kwargs.put("max_tokens", options.getMaxTokens());
        kwargs.put("stop", options.getStop());
        kwargs.put("model", options.getModel());
        kwargs.put("output_parser", options.getOutputParser());
        kwargs.put("timeout", options.getTimeout());
        kwargs.putAll(options.getExtraFields());
        return kwargs;
    }

    private Map<String, Object> withModelConfig(Map<String, Object> kwargs) {
        Map<String, Object> merged = new LinkedHashMap<>(kwargs);
        merged.put("model_config", modelConfig);
        merged.put("model_client_config", modelClientConfig);
        return merged;
    }

    private static List<BaseMessage> safeMessages(List<BaseMessage> messages) {
        return messages == null ? List.of() : List.copyOf(messages);
    }

    private static List<BaseMessage> toBaseMessages(List<? extends BaseMessage> messages) {
        return messages == null ? List.of() : new ArrayList<>(messages);
    }

    private static List<UserMessage> safeUserMessages(List<UserMessage> messages) {
        return messages == null ? List.of() : List.copyOf(messages);
    }

    private static Map<String, Object> copyMap(Map<String, Object> values) {
        return values == null ? new LinkedHashMap<>() : new LinkedHashMap<>(values);
    }

    private static Map<String, Object> toObjectMap(Map<String, String> values) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (values != null) {
            result.putAll(values);
        }
        return result;
    }

    private static Float numberAsFloat(Object value, Float fallback) {
        return value instanceof Number number ? number.floatValue() : fallback;
    }

    private static Integer numberAsInteger(Object value, Integer fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static Object getSessionId(Object session) {
        if (session == null) {
            return null;
        }
        Object value = invokeNoArg(session, "getSessionId");
        return value != null ? value : invokeNoArg(session, "get_session_id");
    }

    private static Object invokeNoArg(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    /**
     * Immutable invocation payload after callback input transforms.
     *
     * <p>Mirrors Python's callback-wrapped invoke/stream kwargs in
     * {@code openjiuwen/core/foundation/llm/model.py}.</p>
     */
    private record InvocationRequest(List<BaseMessage> messages, ModelInvokeOptions options) {
    }

    /**
     * Adapter for legacy invoke-only registrations.
     *
     * <p>Mirrors Python's created {@code BaseModelClient} invoke collaborator in
     * {@code openjiuwen/core/foundation/llm/model.py}.</p>
     */
    private static final class InvokerBackedModelClient implements ModelClient {
        private final ModelInvoker invoker;
        private final ModelRequestConfig modelConfig;
        private final ModelClientConfig modelClientConfig;

        private InvokerBackedModelClient(ModelInvoker invoker) {
            this(invoker, null, null);
        }

        private InvokerBackedModelClient(ModelInvoker invoker, ModelRequestConfig modelConfig,
                                         ModelClientConfig modelClientConfig) {
            this.invoker = Objects.requireNonNull(invoker, "invoker");
            this.modelConfig = modelConfig;
            this.modelClientConfig = modelClientConfig;
        }

        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            return invoker.invoke(messages, modelConfig, modelClientConfig, options);
        }
    }

    /**
     * Adapter for 0.1.14 built-in {@link BaseModelClient} implementations.
     */
    private static final class BaseModelClientAdapter implements ModelClient {
        private final BaseModelClient delegate;

        private BaseModelClientAdapter(BaseModelClient delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            return delegate.invoke(messages, options);
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages, ModelInvokeOptions options) {
            return delegate.stream(messages, options);
        }

        @Override
        public CompletionStage<ImageGenerationResponse> generateImage(List<UserMessage> messages,
                                                                      ImageGenerationOptions options) {
            try {
                return CompletableFuture.completedFuture(delegate.generateImage(
                        messages,
                        options.model(),
                        options.size(),
                        options.negativePrompt(),
                        options.n(),
                        options.promptExtend(),
                        options.watermark(),
                        options.seed(),
                        options.extraFields()
                ));
            } catch (Exception exception) {
                return CompletableFuture.failedFuture(exception);
            }
        }

        @Override
        public CompletionStage<AudioGenerationResponse> generateSpeech(List<UserMessage> messages,
                                                                       SpeechGenerationOptions options) {
            try {
                return CompletableFuture.completedFuture(delegate.generateSpeech(
                        messages,
                        options.model(),
                        options.voice(),
                        options.languageType(),
                        options.extraFields()
                ));
            } catch (Exception exception) {
                return CompletableFuture.failedFuture(exception);
            }
        }

        @Override
        public CompletionStage<VideoGenerationResponse> generateVideo(List<UserMessage> messages,
                                                                      VideoGenerationOptions options) {
            try {
                return CompletableFuture.completedFuture(delegate.generateVideo(
                        messages,
                        options.imgUrl(),
                        options.audioUrl(),
                        options.model(),
                        options.size(),
                        options.resolution(),
                        options.duration(),
                        options.promptExtend(),
                        options.watermark(),
                        options.negativePrompt(),
                        options.seed(),
                        options.extraFields()
                ));
            } catch (Exception exception) {
                return CompletableFuture.failedFuture(exception);
            }
        }

        @Override
        public boolean supportsKvCacheRelease() {
            return delegate.supportsKvCacheRelease();
        }
    }

    /**
     * Iterator wrapper that emits callback events for each streamed chunk.
     *
     * <p>Mirrors Python's stream {@code emit_after(..., item_key="result")} wrapper in
     * {@code openjiuwen/core/foundation/llm/model.py}.</p>
     */
    private static final class CallbackIterator implements Iterator<AssistantMessageChunk>, AutoCloseable {
        private final Iterator<AssistantMessageChunk> delegate;
        private final DecoratorFramework framework;
        private final InvocationRequest request;
        private final ModelRequestConfig modelConfig;
        private final ModelClientConfig modelClientConfig;

        private CallbackIterator(Iterator<AssistantMessageChunk> delegate, DecoratorFramework framework,
                                 InvocationRequest request, ModelRequestConfig modelConfig,
                                 ModelClientConfig modelClientConfig) {
            this.delegate = delegate;
            this.framework = framework;
            this.request = request;
            this.modelConfig = modelConfig;
            this.modelClientConfig = modelClientConfig;
        }

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public AssistantMessageChunk next() {
            AssistantMessageChunk chunk = delegate.next();
            Map<String, Object> outKwargs = new LinkedHashMap<>();
            outKwargs.put("result", chunk);
            outKwargs.put("model_config", modelConfig);
            outKwargs.put("model_client_config", modelClientConfig);
            Object transformed = framework.triggerTransform(LLMCallEvents.LLM_STREAM_OUTPUT, new Object[0],
                    outKwargs);
            AssistantMessageChunk effectiveChunk = transformed instanceof AssistantMessageChunk messageChunk
                    ? messageChunk
                    : chunk;
            Map<String, Object> afterKwargs = new LinkedHashMap<>();
            afterKwargs.put("messages", request.messages());
            afterKwargs.put("result", effectiveChunk);
            afterKwargs.put("model_config", modelConfig);
            afterKwargs.put("model_client_config", modelClientConfig);
            framework.trigger(LLMCallEvents.LLM_STREAM_OUTPUT, new Object[]{request.messages()}, afterKwargs);
            return effectiveChunk;
        }

        @Override
        public void close() throws Exception {
            if (delegate instanceof AutoCloseable closeable) {
                closeable.close();
            }
        }
    }
}
