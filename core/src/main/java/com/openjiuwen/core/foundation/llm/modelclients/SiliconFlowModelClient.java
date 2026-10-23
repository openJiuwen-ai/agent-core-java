/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.modelclients;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.security.UrlUtils;
import com.openjiuwen.core.foundation.llm.classification.ClassifiedModelErrors;
import com.openjiuwen.core.foundation.llm.classification.ErrorBodyParser;
import com.openjiuwen.core.foundation.llm.classification.FirstSegmentRetryIterator;
import com.openjiuwen.core.foundation.llm.classification.ModelCallClassification;
import com.openjiuwen.core.foundation.llm.classification.ModelCallRetrier;
import com.openjiuwen.core.foundation.llm.classification.ModelCallRetryPolicy;
import com.openjiuwen.core.foundation.llm.classification.ModelErrorCategory;
import com.openjiuwen.core.foundation.llm.classification.ModelErrorClassifiers;
import com.openjiuwen.core.foundation.llm.classification.ModelErrorFacts;
import com.openjiuwen.core.foundation.llm.classification.RetryContext;
import com.openjiuwen.core.foundation.llm.modelclients.errors.ErrorResponseBodySanitizer;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ProviderType;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * SiliconFlow API client supporting GPT models and OpenAI-compatible services.
 *
 * <p>Mirrors Python's {@code SiliconFlowModelClient} in
 * {@code openjiuwen/core/foundation/llm/model_clients/siliconflow_model_client.py}.</p>
 */
public class SiliconFlowModelClient extends BaseModelClient {
    public static final String __client_name__ = ProviderType.SILICON_FLOW.getValue();
    public static final String CLIENT_NAME = __client_name__;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String CHAT_COMPLETIONS_SUFFIX = "/chat/completions";
    private static final String CONTENT_TYPE = "application/json";

    static {
        registerClientClass(SiliconFlowModelClient.class);
    }

    private final ModelCallRetrier retrier;

    public SiliconFlowModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        this(modelConfig, modelClientConfig, new ModelCallRetrier());
    }

    @SuppressWarnings("unchecked")
    public SiliconFlowModelClient(Map<String, Object> kwargs) {
        this(
                (ModelRequestConfig) kwargs.get("model_config"),
                (ModelClientConfig) kwargs.get("model_client_config")
        );
    }

    SiliconFlowModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig,
            ModelCallRetrier retrier) {
        super(modelConfig, modelClientConfig);
        this.retrier = retrier;
    }

    @Override
    protected String getClientName() {
        return "SiliconFlow client";
    }

    Map<String, Object> buildAndSanitizeParams(
            Object messages,
            Object tools,
            Number temperature,
            Number topP,
            String model,
            Integer maxTokens,
            String stop,
            boolean stream,
            Map<String, Object> kwargs) {
        Map<String, Object> params = buildRequestParams(
                messages,
                tools,
                temperature,
                topP,
                model,
                stop,
                maxTokens,
                stream,
                kwargs
        );
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messageParams = (List<Map<String, Object>>) params.get("messages");
        params.put("messages", sanitizeToolCalls(messageParams));
        return params;
    }

    @Override
    public AssistantMessage invoke(Object messages,
                                   Object tools,
                                   Float temperature,
                                   Float topP,
                                   String model,
                                   Integer maxTokens,
                                   String stop,
                                   BaseOutputParser outputParser,
                                   Float timeout,
                                   Map<String, Object> kwargs) {
        Map<String, Object> effectiveKwargs = copyMap(kwargs);
        Object tracerRecordData = effectiveKwargs.remove("tracer_record_data");
        Map<String, Object> params = buildAndSanitizeParams(
                messages,
                tools,
                temperature,
                topP,
                model,
                maxTokens,
                stop,
                false,
                effectiveKwargs
        );

        try {
            callTracer(tracerRecordData, singlePayload("llm_params", params));
            Loggers.LLM.info("LLM request params ready. {}", requestLogMetadata(params, false));
            RetryContext retryContext = new RetryContext(ModelCallRetryPolicy.from(modelClientConfig));
            Map<String, Object> data = retrier.execute(retryContext, () -> {
                HttpResult result = postJson(params, timeout);
                ensureClassifiedSuccess(result.statusCode(), result.body(), false);
                ensureNoEmbeddedError(result.body(), false);
                Map<String, Object> parsed = parseJsonObject(result.body());
                Loggers.LLM.info("SiliconFlow API response received. {}", Map.of("response", parsed));
                return parsed;
            }, null);
            AssistantMessage assistantMessage = parseResponse(data, outputParser);
            callTracer(tracerRecordData, singlePayload("llm_response", assistantMessage));
            return assistantMessage;
        } catch (BaseError exception) {
            throw exception;
        } catch (CancellationException cancellation) {
            throw cancellation;
        } catch (IOException | IllegalStateException | IllegalArgumentException
                | ClassCastException exception) {
            Loggers.LLM.error("SiliconFlow API async invoke error. {}", exception.getMessage());
            throw classifyTransportFailure(exception, false);
        }
    }

    @Override
    public Iterator<AssistantMessageChunk> stream(Object messages,
                                                  Object tools,
                                                  Float temperature,
                                                  Float topP,
                                                  String model,
                                                  Integer maxTokens,
                                                  String stop,
                                                  BaseOutputParser outputParser,
                                                  Float timeout,
                                                  Map<String, Object> kwargs) {
        Map<String, Object> effectiveKwargs = copyMap(kwargs);
        Object tracerRecordData = effectiveKwargs.remove("tracer_record_data");
        Map<String, Object> params = buildAndSanitizeParams(
                messages,
                tools,
                temperature,
                topP,
                model,
                maxTokens,
                stop,
                true,
                effectiveKwargs
        );

        try {
            callTracer(tracerRecordData, singlePayload("llm_params", params));
            Loggers.LLM.info("LLM request params ready. {}", requestLogMetadata(params, true));
            Iterator<AssistantMessageChunk> raw = outputParser != null
                    ? streamWithParser(params, outputParser, timeout)
                    : streamResponse(params, timeout);
            return new TracingCollectingIterator(raw, tracerRecordData);
        } catch (BaseError exception) {
            throw exception;
        } catch (CancellationException cancellation) {
            throw cancellation;
        } catch (IOException | IllegalStateException | IllegalArgumentException
                | ClassCastException exception) {
            Loggers.LLM.error("SiliconFlow API async stream error. {}", exception.getMessage());
            throw classifyTransportFailure(exception, true);
        }
    }

    @Override
    public ImageGenerationResponse generateImage(List<UserMessage> messages,
                                                 String model,
                                                 String size,
                                                 String negativePrompt,
                                                 int n,
                                                 boolean promptExtend,
                                                 boolean watermark,
                                                 int seed,
                                                 Map<String, Object> kwargs) {
        return null;
    }

    @Override
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
                                                 Map<String, Object> kwargs) {
        return null;
    }

    @Override
    public AudioGenerationResponse generateSpeech(List<UserMessage> messages,
                                                  String model,
                                                  String voice,
                                                  String languageType,
                                                  Map<String, Object> kwargs) {
        return null;
    }

    private SiliconFlowSseIterator openSseIterator(Map<String, Object> params, Float timeout)
            throws IOException, InterruptedException {
        double idleSeconds = ModelTimeoutResolver.resolvePerCallIdleSeconds(timeout, modelClientConfig);
        HttpResponse<InputStream> response = postStream(params, idleSeconds);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String body = IdleGuardedBodyReader.readFully(response.body(), idleSeconds, true);
            throw classifiedHttpError(response.statusCode(), body, true);
        }
        return new SiliconFlowSseIterator(response.body(), idleSeconds);
    }

    private void ensureClassifiedSuccess(int statusCode, String body, boolean isStream) {
        if (statusCode >= 200 && statusCode < 300) {
            return;
        }
        throw classifiedHttpError(statusCode, body, isStream);
    }

    private void ensureNoEmbeddedError(String body, boolean isStream) {
        if (!ErrorBodyParser.hasErrorObject(body)) {
            return;
        }
        throw classifiedHttpError(null, body, isStream);
    }

    private BaseError classifiedHttpError(Integer statusCode, String body, boolean isStream) {
        ErrorResponseBodySanitizer.SanitizedBody sanitized =
                ErrorResponseBodySanitizer.sanitize(body == null ? "" : body);
        ModelErrorFacts.Builder factsBuilder = ModelErrorFacts.Builder.create()
                .httpStatus(statusCode)
                .errorBody(sanitized.body())
                .stream(isStream);
        Optional<ErrorBodyParser.ParsedError> parsed = ErrorBodyParser.parse(sanitized.body());
        if (parsed.isPresent()) {
            ErrorBodyParser.ParsedError error = parsed.get();
            factsBuilder.errorCode(error.code()).errorType(error.type()).errorMessage(error.message());
        }
        ModelErrorFacts facts = factsBuilder.build();
        ModelCallClassification classification =
                ModelErrorClassifiers.defaultClassifier().classify(facts);
        String message = statusCode == null
                ? "embedded error in success response: " + sanitized.body()
                : "API returned error " + statusCode + ": " + sanitized.body();
        return ClassifiedModelErrors.toError(classification, facts, message, null);
    }

    private BaseError classifyTransportFailure(Exception exception, boolean isStream) {
        ModelErrorFacts facts = ModelErrorFacts.ofThrowable(exception, isStream);
        ModelCallClassification classification =
                ModelErrorClassifiers.defaultClassifier().classify(facts);
        if (classification.category() == ModelErrorCategory.USER_CANCELLED) {
            throw ClassifiedModelErrors.toCancellation("model call cancelled");
        }
        Optional<BaseError> timeoutError = ModelTimeoutErrorFactory.fromSendException(
                exception, isStream, modelClientConfig);
        if (timeoutError.isPresent()) {
            return timeoutError.get();
        }
        return ClassifiedModelErrors.toError(classification, facts,
                "siliconFlow API transport failure: " + exception.getMessage(), exception);
    }

    private final class SiliconFlowStreamHandle
            implements ModelCallRetrier.StreamHandle<AssistantMessageChunk> {
        private final SiliconFlowSseIterator delegate;

        private SiliconFlowStreamHandle(SiliconFlowSseIterator delegate) {
            this.delegate = delegate;
        }

        @Override
        public Iterator<AssistantMessageChunk> iterator() {
            return delegate;
        }

        @Override
        public void closeQuietly() {
            delegate.close();
        }
    }

    protected Iterator<AssistantMessageChunk> streamWithParser(
            Map<String, Object> params,
            BaseOutputParser outputParser,
            Float timeout) throws IOException {
        return new ParserApplyingIterator(streamResponse(params, timeout), outputParser);
    }

    protected AssistantMessage parseResponse(Map<String, Object> response, BaseOutputParser parser) {
        List<?> choices = asList(response.get("choices"));
        Map<String, Object> choice = choices.isEmpty() ? new LinkedHashMap<>() : asObjectMap(choices.get(0));
        Map<String, Object> message = asObjectMap(choice.getOrDefault("message", Map.of()));
        String content = message.get("content") == null ? "" : String.valueOf(message.get("content"));
        String reasoningContent = asString(message.get("reasoning_content"));
        List<ToolCall> toolCalls = parseToolCalls(message.get("tool_calls"));
        UsageMetadata usageMetadata = parseUsageMetadata(response.get("usage"), true);
        Object parserContent = parseContent(parser, content);

        return AssistantMessage.builder()
                .content(content)
                .toolCalls(toolCalls.isEmpty() ? null : toolCalls)
                .usageMetadata(usageMetadata)
                .finishReason(toolCalls.isEmpty() ? "stop" : "tool_calls")
                .reasoningContent(reasoningContent)
                .parserContent(parserContent)
                .build();
    }

    protected Iterator<AssistantMessageChunk> streamResponse(Map<String, Object> params, Float timeout)
            throws IOException {
        RetryContext retryContext = new RetryContext(ModelCallRetryPolicy.from(modelClientConfig));
        ModelCallRetrier.StreamAttempt<AssistantMessageChunk> streamAttempt =
                () -> new SiliconFlowStreamHandle(openSseIterator(params, timeout));
        return new FirstSegmentRetryIterator<>(retryContext, streamAttempt,
                retrier.openStream(retryContext, streamAttempt, null), retrier, null);
    }

    protected AssistantMessageChunk parseStreamChunk(String line) {
        if (line == null) {
            return null;
        }
        String dataString = line;
        if (dataString.startsWith("data: ")) {
            dataString = dataString.substring("data: ".length());
        }
        if ("[DONE]".equals(dataString.strip())) {
            return null;
        }

        try {
            Map<String, Object> chunkData = parseJsonObject(dataString);
            List<?> choices = asList(chunkData.get("choices"));
            Map<String, Object> choice = choices.isEmpty() ? new LinkedHashMap<>() : asObjectMap(choices.get(0));
            Map<String, Object> delta = asObjectMap(choice.getOrDefault("delta", Map.of()));
            String content = delta.get("content") == null ? "" : String.valueOf(delta.get("content"));
            String reasoningContent = asString(delta.get("reasoning_content"));
            List<ToolCall> toolCalls = parseToolCalls(delta.get("tool_calls"));
            UsageMetadata usageMetadata = parseUsageMetadata(chunkData.get("usage"), false);
            if (!isPythonTruthy(content) && !isPythonTruthy(reasoningContent) && toolCalls.isEmpty()
                    && usageMetadata == null) {
                return null;
            }

            return AssistantMessageChunk.builder()
                    .content(content)
                    .reasoningContent(reasoningContent)
                    .toolCalls(toolCalls.isEmpty() ? null : toolCalls)
                    .usageMetadata(usageMetadata)
                    .finishReason(isPythonTruthy(choice.get("finish_reason"))
                            ? String.valueOf(choice.get("finish_reason"))
                            : "null")
                    .build();
        } catch (JsonProcessingException exception) {
            return null;
        } catch (ClassCastException | IllegalStateException | IndexOutOfBoundsException exception) {
            Loggers.LLM.warning("Error parsing stream chunk. {}", exception.getMessage());
            return null;
        }
    }

    /**
     * Sends a non-streaming POST to the chat completions endpoint with idle-bounded body read.
     *
     * @param payload request payload
     * @param timeout per-call idle override in seconds, null for the configured default
     * @return HTTP result envelope
     * @throws IOException on transport failure
     * @throws InterruptedException when interrupted while sending
     */
    protected HttpResult postJson(Map<String, Object> payload, Float timeout)
            throws IOException, InterruptedException {
        validateApiBaseUrl();
        String apiUrl = resolveApiUrl();
        HttpRequest request = requestBuilder(apiUrl, payload).build();
        double idleSeconds = ModelTimeoutResolver.resolvePerCallIdleSeconds(timeout, modelClientConfig);
        HttpResponse<InputStream> response = createHttpClient(apiUrl)
                .send(request, IdleBoundInputStream.bodyHandler(idleSeconds));
        String body = IdleGuardedBodyReader.readFully(response.body(), idleSeconds, false);
        return new HttpResult(response.statusCode(), body);
    }

    /**
     * Sends a streaming request and returns the raw response with the body still open. The
     * request-level timeout only covers the response-header wait; segment idleness is guarded
     * by the consuming iterator.
     *
     * @param payload request payload
     * @param idleSeconds idle-layer timeout in seconds for the body stream
     * @return the streaming HTTP response
     * @throws IOException on transport failures
     * @throws InterruptedException when interrupted while sending
     */
    protected HttpResponse<InputStream> postStream(Map<String, Object> payload,
            double idleSeconds) throws IOException, InterruptedException {
        validateApiBaseUrl();
        String apiUrl = resolveApiUrl();
        HttpRequest request = requestBuilder(apiUrl, payload).build();
        return createHttpClient(apiUrl).send(request, IdleBoundInputStream.bodyHandler(idleSeconds));
    }

    protected void validateApiBaseUrl() {
        String apiBase = modelClientConfig.getApiBase();
        if (modelClientConfig.isVerifySsl() || !isLoopbackApiBase(apiBase)) {
            UrlUtils.checkUrlIsValid(apiBase);
        }
    }

    private static boolean isLoopbackApiBase(String apiBase) {
        try {
            URI uri = URI.create(apiBase);
            String host = uri.getHost();
            return host != null && InetAddress.getByName(host).isLoopbackAddress();
        } catch (RuntimeException | java.net.UnknownHostException exception) {
            return false;
        }
    }

    String resolveApiUrl() {
        String apiUrl = trimTrailingSlash(modelClientConfig.getApiBase());
        if (!apiUrl.endsWith(CHAT_COMPLETIONS_SUFFIX)) {
            apiUrl = apiUrl + CHAT_COMPLETIONS_SUFFIX;
        }
        return apiUrl;
    }

    protected static List<Map<String, Object>> sanitizeToolCalls(List<Map<String, Object>> messages) {
        if (messages == null) {
            return List.of();
        }
        for (Map<String, Object> message : messages) {
            if (!"assistant".equals(message.get("role"))) {
                continue;
            }
            Object toolCallsValue = message.get("tool_calls");
            if (!(toolCallsValue instanceof List<?> toolCalls)) {
                continue;
            }

            List<Map<String, Object>> cleaned = new ArrayList<>();
            for (Object toolCallValue : toolCalls) {
                if (!(toolCallValue instanceof Map<?, ?> rawToolCall)) {
                    continue;
                }
                Map<String, Object> toolCall = toObjectMap(rawToolCall);
                Map<String, Object> function = asObjectMap(toolCall.getOrDefault("function", Map.of()));

                Map<String, Object> cleanedFunction = new LinkedHashMap<>();
                cleanedFunction.put("name", stringOrEmpty(function.get("name")));
                cleanedFunction.put("arguments", stringOrEmpty(function.get("arguments")));

                Map<String, Object> cleanedToolCall = new LinkedHashMap<>();
                cleanedToolCall.put("id", stringOrEmpty(toolCall.get("id")));
                cleanedToolCall.put("type", "function");
                cleanedToolCall.put("index", toolCall.get("index"));
                cleanedToolCall.put("function", cleanedFunction);
                cleaned.add(cleanedToolCall);
            }
            message.put("tool_calls", cleaned);
        }
        return messages;
    }

    private HttpRequest.Builder requestBuilder(String apiUrl, Map<String, Object> payload)
            throws JsonProcessingException {
        return HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(ModelTimeoutResolver.toDuration(
                        ModelTimeoutResolver.resolveHeaderWaitSeconds(modelClientConfig)))
                .header("Content-Type", CONTENT_TYPE)
                .header("Authorization", "Bearer " + modelClientConfig.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(payload),
                        StandardCharsets.UTF_8));
    }

    HttpClient createHttpClient(String apiUrl) {
        return ModelHttpClients.builder(modelClientConfig, apiUrl)
                .withSsl()
                .withProxy()
                .build();
    }

    private Map<String, Object> requestLogMetadata(Map<String, Object> params, boolean stream) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("model_name", params.get("model"));
        metadata.put("model_provider", modelClientConfig.getClientProvider());
        metadata.put("messages", params.get("messages"));
        metadata.put("tools", params.get("tools"));
        metadata.put("temperature", params.get("temperature"));
        metadata.put("top_p", params.get("top_p"));
        metadata.put("max_tokens", params.get("max_tokens"));
        metadata.put("is_stream", stream);
        return metadata;
    }

    private Object parseContent(BaseOutputParser parser, String content) {
        Loggers.LLM.info("Before parse content with parser. {}", Map.of(
                "model_name", modelConfig.getModelName(),
                "model_provider", modelClientConfig.getClientProvider(),
                "response_content", content,
                "is_stream", false
        ));
        if (parser == null || !isPythonTruthy(content)) {
            return null;
        }
        try {
            Object parserContent = parser.<CompletableFuture<Object>>parse(content).join();
            Loggers.LLM.info("Parser parse success. {}", Map.of("parser_content", parserContent));
            return parserContent;
        } catch (RuntimeException exception) {
            Loggers.LLM.warning("Parser parse error. {}", exception.getMessage());
            return null;
        }
    }

    private List<ToolCall> parseToolCalls(Object rawToolCalls) {
        if (!(rawToolCalls instanceof List<?> rawList)) {
            return List.of();
        }
        List<ToolCall> toolCalls = new ArrayList<>();
        for (int index = 0; index < rawList.size(); index++) {
            Object item = rawList.get(index);
            if (!(item instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, Object> toolCallMap = toObjectMap(rawMap);
            Map<String, Object> function = asObjectMap(toolCallMap.getOrDefault("function", Map.of()));
            Object indexValue = toolCallMap.get("index");
            toolCalls.add(ToolCall.builder()
                    .id(stringOrEmpty(toolCallMap.get("id")))
                    .type("function")
                    .name(stringOrEmpty(function.get("name")))
                    .arguments(stringOrEmpty(function.get("arguments")))
                    .index(indexValue instanceof Number number ? number.intValue() : index)
                    .build());
        }
        return toolCalls;
    }

    private UsageMetadata parseUsageMetadata(Object usageValue, boolean includeCacheTokens) {
        if (!(usageValue instanceof Map<?, ?> usageMap)) {
            return null;
        }
        Map<String, Object> usage = toObjectMap(usageMap);
        int cacheTokens = 0;
        if (includeCacheTokens && usage.get("prompt_tokens_details") instanceof Map<?, ?> promptDetails) {
            cacheTokens = intValue(toObjectMap(promptDetails).get("cached_tokens"));
        }
        CostInfo costInfo = extractCostInfo(usage);
        return UsageMetadata.builder()
                .modelName(modelConfig.getModelName())
                .inputTokens(intValue(usage.get("prompt_tokens")))
                .outputTokens(intValue(usage.get("completion_tokens")))
                .totalTokens(intValue(usage.get("total_tokens")))
                .cacheTokens(cacheTokens)
                .inputCost(costInfo.inputCost())
                .outputCost(costInfo.outputCost())
                .totalCost(costInfo.totalCost())
                .build();
    }

    private Map<String, Object> parseJsonObject(String json) throws JsonProcessingException {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        return OBJECT_MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
        });
    }

    @SuppressWarnings("unchecked")
    private void callTracer(Object tracer, Map<String, Object> payload) {
        if (tracer == null) {
            return;
        }
        if (tracer instanceof Consumer<?> consumer) {
            ((Consumer<Map<String, Object>>) consumer).accept(payload);
            return;
        }
        Object result = invokeTracerMethod(tracer, "accept", payload);
        if (result == TracerMethodMissing.INSTANCE) {
            result = invokeTracerMethod(tracer, "apply", payload);
        }
        if (result == TracerMethodMissing.INSTANCE) {
            result = invokeTracerMethod(tracer, "call", payload);
        }
        if (result == TracerMethodMissing.INSTANCE) {
            throw new IllegalArgumentException("tracer_record_data must accept a Map payload");
        }
        if (result instanceof CompletionStage<?> stage) {
            stage.toCompletableFuture().join();
        }
    }

    private Object invokeTracerMethod(Object tracer, String methodName, Map<String, Object> payload) {
        try {
            Method method = tracer.getClass().getMethod(methodName, Map.class);
            return method.invoke(tracer, payload);
        } catch (NoSuchMethodException ignored) {
            return TracerMethodMissing.INSTANCE;
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException(exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            if (cause == null) {
                throw new IllegalStateException(exception);
            }
            throw new IllegalStateException(cause);
        }
    }

    private static Map<String, Object> singlePayload(String key, Object value) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(key, value);
        return payload;
    }

    private static List<?> asList(Object value) {
        return value instanceof List<?> list ? list : Collections.emptyList();
    }

    private static Map<String, Object> asObjectMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return toObjectMap(map);
        }
        return new LinkedHashMap<>();
    }

    private static Map<String, Object> toObjectMap(Map<?, ?> rawMap) {
        Map<String, Object> result = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static Map<String, Object> copyMap(Map<String, Object> values) {
        return values == null ? new LinkedHashMap<>() : new LinkedHashMap<>(values);
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }

    private static String stringOrEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static boolean isPythonTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0D;
        }
        if (value instanceof CharSequence sequence) {
            return !sequence.isEmpty();
        }
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return true;
    }

    /**
     * Mirrors Python's yielded {@code aiohttp.ClientResponse} boundary in
     * {@code openjiuwen/core/foundation/llm/model_clients/siliconflow_model_client.py}.
     *
     * @param statusCode HTTP status code
     * @param body response body text
     */
    protected record HttpResult(int statusCode, String body) {
    }

    /**
     * Incremental SSE chunk iterator over an idle-bounded body stream, replacing the former
     * whole-body buffered stream consumption. Chunks are delivered as they arrive; peer
     * silence beyond the idle window raises a tiered timeout error instead of hanging
     * forever.
     *
     * @since 2026-10-09
     */
    private final class SiliconFlowSseIterator implements Iterator<AssistantMessageChunk>, AutoCloseable {
        private final BufferedReader reader;
        private final double idleSeconds;
        private AssistantMessageChunk nextChunk;
        private boolean isFirstSegmentReceived;
        private boolean isEffectiveFirstSegmentReceived;
        private boolean isDone;
        private boolean isClosed;

        SiliconFlowSseIterator(InputStream inputStream, double idleSeconds) {
            this.reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            this.idleSeconds = idleSeconds;
        }

        @Override
        public boolean hasNext() {
            if (nextChunk != null) {
                return true;
            }
            if (isDone) {
                return false;
            }
            nextChunk = readNextChunk().orElse(null);
            return nextChunk != null;
        }

        @Override
        public AssistantMessageChunk next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            AssistantMessageChunk current = nextChunk;
            nextChunk = null;
            return current;
        }

        @Override
        public void close() {
            if (isClosed) {
                return;
            }
            isClosed = true;
            isDone = true;
            closeReaderQuietly();
        }

        private Optional<AssistantMessageChunk> readNextChunk() {
            boolean shouldKeepOpen = false;
            try {
                String rawLine;
                while ((rawLine = reader.readLine()) != null) {
                    String line = rawLine.strip();
                    if (line.isEmpty() || !line.startsWith("data:")) {
                        continue;
                    }
                    isFirstSegmentReceived = true;
                    String data = line.substring("data:".length()).strip();
                    if (!data.contains("choices") && ErrorBodyParser.hasErrorObject(data)) {
                        throw errorFrameException(data);
                    }
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    AssistantMessageChunk chunk = parseStreamChunk(line);
                    if (chunk != null) {
                        shouldKeepOpen = true;
                        isEffectiveFirstSegmentReceived = true;
                        return Optional.of(chunk);
                    }
                }
                finish();
                return Optional.empty();
            } catch (IOException exception) {
                finish();
                throwIfIdleTimeout(exception);
                if (exception instanceof InterruptedIOException
                        && !(exception instanceof SocketTimeoutException)) {
                    // SocketTimeoutException is a read timeout, not a cancellation.
                    throw ClassifiedModelErrors.toCancellation("model stream read cancelled");
                }
                throw classifiedStreamFailure(exception);
            } finally {
                if (!shouldKeepOpen) {
                    close();
                }
            }
        }

        private void finish() {
            isDone = true;
            close();
        }

        private void throwIfIdleTimeout(IOException exception) {
            if (exception instanceof IdleTimeoutIOException idleTimeout) {
                throw ModelTimeoutErrorFactory.idleTimeoutError(
                        isFirstSegmentReceived
                                ? TimeoutPhase.AFTER_FIRST_SEGMENT
                                : TimeoutPhase.BEFORE_FIRST_SEGMENT,
                        idleTimeout.idleSeconds(), true, exception);
            }
        }

        private BaseError classifiedStreamFailure(IOException exception) {
            ModelErrorFacts facts = ModelErrorFacts.Builder.create()
                    .throwable(exception)
                    .stream(true)
                    .firstSegmentReceived(isEffectiveFirstSegmentReceived)
                    .build();
            ModelCallClassification classification =
                    ModelErrorClassifiers.defaultClassifier().classify(facts);
            return ClassifiedModelErrors.toError(classification, facts,
                    "siliconFlow API stream read failure: " + exception.getMessage(), exception);
        }

        private BaseError errorFrameException(String data) {
            ErrorResponseBodySanitizer.SanitizedBody sanitized =
                    ErrorResponseBodySanitizer.sanitize(data);
            ModelErrorFacts.Builder factsBuilder = ModelErrorFacts.Builder.create()
                    .errorBody(sanitized.body())
                    .stream(true)
                    .firstSegmentReceived(isEffectiveFirstSegmentReceived);
            Optional<ErrorBodyParser.ParsedError> parsed = ErrorBodyParser.parse(sanitized.body());
            if (parsed.isPresent()) {
                ErrorBodyParser.ParsedError error = parsed.get();
                factsBuilder.errorCode(error.code()).errorType(error.type()).errorMessage(error.message());
            }
            ModelErrorFacts facts = factsBuilder.build();
            ModelCallClassification classification =
                    ModelErrorClassifiers.defaultClassifier().classify(facts);
            return ClassifiedModelErrors.toError(classification, facts,
                    "stream error frame: " + sanitized.body(), null);
        }

        private void closeReaderQuietly() {
            try {
                reader.close();
            } catch (IOException exception) {
                Loggers.LLM.debug("Failed to close SiliconFlow stream reader. {}",
                        exception.getMessage());
            }
        }
    }

    /**
     * Lazily applies the output parser on top of the incremental SSE iterator, preserving
     * streaming delivery instead of aggregating the whole response up front.
     *
     * @since 2026-10-09
     */
    private final class ParserApplyingIterator implements Iterator<AssistantMessageChunk>, AutoCloseable {
        private final Iterator<AssistantMessageChunk> delegate;
        private final BaseOutputParser parser;
        private final StringBuilder accumulatedContent = new StringBuilder();

        ParserApplyingIterator(Iterator<AssistantMessageChunk> delegate, BaseOutputParser parser) {
            this.delegate = delegate;
            this.parser = parser;
        }

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public AssistantMessageChunk next() {
            AssistantMessageChunk chunkItem = delegate.next();
            if (isPythonTruthy(chunkItem.getContent())) {
                accumulatedContent.append(chunkItem.getContent());
            }
            Object parserContent = null;
            if (!accumulatedContent.isEmpty()) {
                try {
                    Object parsedResult = parser.<CompletableFuture<Object>>parse(
                            accumulatedContent.toString()).join();
                    if (parsedResult != null) {
                        parserContent = parsedResult;
                        accumulatedContent.setLength(0);
                    }
                } catch (IllegalStateException | IllegalArgumentException | CompletionException exception) {
                    Loggers.LLM.debug("Stream parser attempt error. {}", exception.getMessage());
                }
            }
            return AssistantMessageChunk.builder()
                    .content(chunkItem.getContent())
                    .reasoningContent(chunkItem.getReasoningContent())
                    .toolCalls(chunkItem.getToolCalls())
                    .usageMetadata(chunkItem.getUsageMetadata())
                    .finishReason(chunkItem.getFinishReason())
                    .parserContent(parserContent)
                    .build();
        }

        @Override
        public void close() throws Exception {
            if (delegate instanceof AutoCloseable closeable) {
                closeable.close();
            }
        }
    }

    /**
     * Lazily merges chunks for the tracer record while streaming them through; the merged
     * llm_response record is emitted once iteration completes or the stream is closed,
     * replacing the former collect-all-then-return buffering.
     *
     * @since 2026-10-09
     */
    private final class TracingCollectingIterator implements Iterator<AssistantMessageChunk>, AutoCloseable {
        private final Iterator<AssistantMessageChunk> delegate;
        private final Object tracerRecordData;
        private AssistantMessageChunk mergedMessage;
        private boolean isResponseRecorded;

        TracingCollectingIterator(Iterator<AssistantMessageChunk> delegate, Object tracerRecordData) {
            this.delegate = delegate;
            this.tracerRecordData = tracerRecordData;
        }

        @Override
        public boolean hasNext() {
            boolean hasNext = delegate.hasNext();
            if (!hasNext) {
                recordMergedResponse();
            }
            return hasNext;
        }

        @Override
        public AssistantMessageChunk next() {
            AssistantMessageChunk chunk = delegate.next();
            if (mergedMessage == null) {
                mergedMessage = chunk;
                return chunk;
            }
            Object merged = mergedMessage.merge(chunk);
            if (!(merged instanceof AssistantMessageChunk mergedChunk)) {
                throw new IllegalStateException("merged stream chunk is not an AssistantMessageChunk");
            }
            mergedMessage = mergedChunk;
            return chunk;
        }

        @Override
        public void close() throws Exception {
            recordMergedResponse();
            if (delegate instanceof AutoCloseable closeable) {
                closeable.close();
            }
        }

        private void recordMergedResponse() {
            if (!isResponseRecorded) {
                isResponseRecorded = true;
                callTracer(tracerRecordData, singlePayload("llm_response", mergedMessage));
            }
        }
    }

    /**
     * Mirrors Python's dynamic callable lookup for {@code tracer_record_data} in
     * {@code openjiuwen/core/foundation/llm/model_clients/siliconflow_model_client.py}.
     */
    private enum TracerMethodMissing {
        INSTANCE
    }
}
