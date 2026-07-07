/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.security.UrlUtils;
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

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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

    public SiliconFlowModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        super(modelConfig, modelClientConfig);
    }

    @SuppressWarnings("unchecked")
    public SiliconFlowModelClient(Map<String, Object> kwargs) {
        this(
                (ModelRequestConfig) kwargs.get("model_config"),
                (ModelClientConfig) kwargs.get("model_client_config")
        );
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
                                   Map<String, Object> kwargs) throws Exception {
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
        callTracer(tracerRecordData, singlePayload("llm_params", params));
        Loggers.LLM.info("LLM request params ready. {}", requestLogMetadata(params, false));

        try {
            HttpResult result = postJson(params, timeout);
            if (result.statusCode() >= 400) {
                throw new IOException("API returned error " + result.statusCode() + ": " + result.body());
            }
            Map<String, Object> data = parseJsonObject(result.body());
            Loggers.LLM.info("SiliconFlow API response received. {}", Map.of("response", data));
            AssistantMessage assistantMessage = parseResponse(data, outputParser);
            callTracer(tracerRecordData, singlePayload("llm_response", assistantMessage));
            return assistantMessage;
        } catch (Exception exception) {
            Loggers.LLM.error("SiliconFlow API async invoke error. {}", exception.getMessage());
            throw ErrorHelper.buildError(
                    StatusCode.MODEL_CALL_FAILED,
                    null,
                    null,
                    exception,
                    Map.of("error_msg", "siliconFlow API async invoke error: " + exception.getMessage())
            );
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
                                                  Map<String, Object> kwargs) throws Exception {
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
        callTracer(tracerRecordData, singlePayload("llm_params", params));

        try {
            Iterator<AssistantMessageChunk> iterator = outputParser != null
                    ? streamWithParser(params, outputParser, timeout)
                    : streamResponse(params, timeout);
            List<AssistantMessageChunk> chunks = iteratorToList(iterator);
            AssistantMessageChunk finalMessage = null;
            for (AssistantMessageChunk chunk : chunks) {
                finalMessage = finalMessage == null ? chunk : (AssistantMessageChunk) finalMessage.merge(chunk);
            }
            callTracer(tracerRecordData, singlePayload("llm_response", finalMessage));
            return chunks.iterator();
        } catch (Exception exception) {
            Loggers.LLM.error("SiliconFlow API async stream error. {}", exception.getMessage());
            throw ErrorHelper.buildError(
                    StatusCode.MODEL_CALL_FAILED,
                    null,
                    null,
                    exception,
                    Map.of("error_msg", "siliconFlow API async stream error: " + exception.getMessage())
            );
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

    protected Iterator<AssistantMessageChunk> streamWithParser(
            Map<String, Object> params,
            BaseOutputParser outputParser,
            Float timeout) throws Exception {
        List<AssistantMessageChunk> parsedChunks = new ArrayList<>();
        StringBuilder accumulatedContent = new StringBuilder();
        Iterator<AssistantMessageChunk> iterator = streamResponse(params, timeout);
        while (iterator.hasNext()) {
            AssistantMessageChunk parsedChunk = iterator.next();
            if (isPythonTruthy(parsedChunk.getContent())) {
                accumulatedContent.append(parsedChunk.getContent());
            }

            Object parserContent = null;
            if (!accumulatedContent.isEmpty()) {
                try {
                    Object parsedResult = outputParser.<CompletableFuture<Object>>parse(
                            accumulatedContent.toString()).join();
                    if (parsedResult != null) {
                        parserContent = parsedResult;
                        accumulatedContent.setLength(0);
                    }
                } catch (RuntimeException exception) {
                    Loggers.LLM.debug("Stream parser attempt error. {}", exception.getMessage());
                }
            }

            parsedChunks.add(AssistantMessageChunk.builder()
                    .content(parsedChunk.getContent())
                    .reasoningContent(parsedChunk.getReasoningContent())
                    .toolCalls(parsedChunk.getToolCalls())
                    .usageMetadata(parsedChunk.getUsageMetadata())
                    .finishReason(parsedChunk.getFinishReason())
                    .parserContent(parserContent)
                    .build());
        }
        return parsedChunks.iterator();
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
            throws Exception {
        HttpStreamResult result = postStream(params, timeout);
        if (result.statusCode() >= 400) {
            throw new IOException("API returned error " + result.statusCode() + ": " + result.body());
        }

        List<AssistantMessageChunk> chunks = new ArrayList<>();
        for (String line : result.lines()) {
            if (line == null || line.strip().isEmpty()) {
                continue;
            }
            AssistantMessageChunk chunk = parseStreamChunk(line.strip());
            if (chunk != null) {
                chunks.add(chunk);
            }
        }
        return chunks.iterator();
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
            if (!isPythonTruthy(content) && !isPythonTruthy(reasoningContent) && toolCalls.isEmpty()) {
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
        } catch (Exception exception) {
            Loggers.LLM.warning("Error parsing stream chunk. {}", exception.getMessage());
            return null;
        }
    }

    protected HttpResult postJson(Map<String, Object> payload, Float timeout) throws Exception {
        validateApiBaseUrl();
        String apiUrl = resolveApiUrl();
        HttpRequest request = requestBuilder(apiUrl, payload, timeout).build();
        HttpResponse<String> response = createHttpClient(apiUrl)
                .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new HttpResult(response.statusCode(), response.body());
    }

    protected HttpStreamResult postStream(Map<String, Object> payload, Float timeout) throws Exception {
        HttpResult result = postJson(payload, timeout);
        List<String> lines = result.body() == null ? List.of() : result.body().lines().toList();
        return new HttpStreamResult(result.statusCode(), lines, result.body());
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

    private HttpRequest.Builder requestBuilder(String apiUrl, Map<String, Object> payload, Float timeout)
            throws JsonProcessingException {
        return HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(timeoutDuration(timeout))
                .header("Content-Type", CONTENT_TYPE)
                .header("Authorization", "Bearer " + modelClientConfig.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(payload),
                        StandardCharsets.UTF_8));
    }

    HttpClient createHttpClient(String apiUrl) {
        return ModelHttpClients.builder(modelClientConfig, apiUrl)
                .connectTimeout(timeoutDuration(null))
                .withSsl()
                .withProxy()
                .build();
    }

    private Duration timeoutDuration(Float overrideTimeout) {
        double seconds = overrideTimeout != null ? overrideTimeout.doubleValue() : modelClientConfig.getTimeout();
        long millis = Math.max(1L, Math.round(seconds * 1000.0D));
        return Duration.ofMillis(millis);
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
    private void callTracer(Object tracer, Map<String, Object> payload) throws Exception {
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

    private Object invokeTracerMethod(Object tracer, String methodName, Map<String, Object> payload) throws Exception {
        try {
            Method method = tracer.getClass().getMethod(methodName, Map.class);
            return method.invoke(tracer, payload);
        } catch (NoSuchMethodException ignored) {
            return TracerMethodMissing.INSTANCE;
        } catch (IllegalAccessException exception) {
            throw exception;
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception innerException) {
                throw innerException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw exception;
        }
    }

    private static Map<String, Object> singlePayload(String key, Object value) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(key, value);
        return payload;
    }

    private static List<AssistantMessageChunk> iteratorToList(Iterator<AssistantMessageChunk> iterator) {
        List<AssistantMessageChunk> result = new ArrayList<>();
        while (iterator.hasNext()) {
            result.add(iterator.next());
        }
        return result;
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
     * Mirrors Python's streaming {@code aiohttp.ClientResponse.content} boundary in
     * {@code openjiuwen/core/foundation/llm/model_clients/siliconflow_model_client.py}.
     *
     * @param statusCode HTTP status code
     * @param lines response body lines
     * @param body complete response body text
     */
    protected record HttpStreamResult(int statusCode, List<String> lines, String body) {
    }

    /**
     * Mirrors Python's dynamic callable lookup for {@code tracer_record_data} in
     * {@code openjiuwen/core/foundation/llm/model_clients/siliconflow_model_client.py}.
     */
    private enum TracerMethodMissing {
        INSTANCE
    }
}
