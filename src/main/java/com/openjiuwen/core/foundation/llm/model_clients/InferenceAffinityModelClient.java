/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Inference Affinity (vLLM) API client with cache release support.
 *
 * <p>Mirrors Python's {@code InferenceAffinityModelClient} in
 * {@code openjiuwen/core/foundation/llm/model_clients/inference_affinity_model_client.py}.</p>
 */
public class InferenceAffinityModelClient extends BaseModelClient {

    public static final String CLIENT_NAME = ProviderType.INFERENCE_AFFINITY.getValue();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";
    private static final String RELEASE_KV_CACHE_PATH = "/release_kv_cache";
    private static final String CONTENT_TYPE = "application/json";

    private final HttpClient httpClient;

    static {
        registerClientClass(InferenceAffinityModelClient.class);
    }

    public InferenceAffinityModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        super(modelConfig, modelClientConfig);
        this.httpClient = createHttpClient(modelClientConfig);
    }

    @SuppressWarnings("unchecked")
    public InferenceAffinityModelClient(Map<String, Object> kwargs) {
        this(
                (ModelRequestConfig) kwargs.get("model_config"),
                (ModelClientConfig) kwargs.get("model_client_config")
        );
    }

    @Override
    protected String getClientName() {
        return "InferenceAffinity client";
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
            String sessionId,
            boolean enableCacheSharing,
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
        if (enableCacheSharing && isPythonTruthy(sessionId)) {
            params.put("cache_sharing", true);
            params.put("cache_salt", sessionId);
        }
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
        effectiveKwargs.remove("tracer_record_data");
        String sessionId = asString(effectiveKwargs.remove("session_id"));
        boolean enableCacheSharing = booleanValue(effectiveKwargs.remove("enable_cache_sharing"));
        Map<String, Object> params = buildAndSanitizeParams(
                messages,
                tools,
                temperature,
                topP,
                model,
                maxTokens,
                stop,
                false,
                sessionId,
                enableCacheSharing,
                effectiveKwargs
        );

        Loggers.LLM.info("LLM request params ready. {}", requestLogMetadata(params, false));
        try {
            Map<String, Object> responseData = makeAsyncRequest(params, timeout);
            Loggers.LLM.info("InferenceAffinity API response received. {}", Map.of("response", responseData));
            return parseResponse(responseData, outputParser);
        } catch (Exception exception) {
            Loggers.LLM.error("InferenceAffinity API async invoke error. {}", exception.getMessage());
            throw ErrorHelper.buildError(
                    StatusCode.MODEL_CALL_FAILED,
                    null,
                    null,
                    exception,
                    Map.of("error_msg", "InferenceAffinity API async invoke error: " + exception.getMessage())
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
        effectiveKwargs.remove("tracer_record_data");
        String sessionId = asString(effectiveKwargs.remove("session_id"));
        boolean enableCacheSharing = booleanValue(effectiveKwargs.remove("enable_cache_sharing"));
        Map<String, Object> params = buildAndSanitizeParams(
                messages,
                tools,
                temperature,
                topP,
                model,
                maxTokens,
                stop,
                true,
                sessionId,
                enableCacheSharing,
                effectiveKwargs
        );

        try {
            if (outputParser != null) {
                return streamWithParser(params, outputParser, timeout);
            }
            return streamResponse(params, timeout);
        } catch (Exception exception) {
            Loggers.LLM.error("InferenceAffinity API async stream error. {}", exception.getMessage());
            throw ErrorHelper.buildError(
                    StatusCode.MODEL_CALL_FAILED,
                    null,
                    null,
                    exception,
                    Map.of("error_msg", "InferenceAffinity API async stream error: " + exception.getMessage())
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
    public AudioGenerationResponse generateSpeech(List<UserMessage> messages,
                                                  String model,
                                                  String voice,
                                                  String languageType,
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

    public Boolean release(String sessionId,
                           Object messages,
                           int messagesReleasedIndex,
                           Object tools,
                           Integer toolsReleasedIndex,
                           String model) throws Exception {
        try {
            List<Map<String, Object>> messagesDict = convertMessagesToDict(messages);
            List<Map<String, Object>> toolsDict = convertToolsToDict(tools);
            List<Map<String, Object>> sanitizedMessages = sanitizeToolCalls(messagesDict);
            String resolvedModel = isPythonTruthy(model) ? model : modelConfig.getModelName();

            Map<String, Object> releaseParams = new LinkedHashMap<>();
            releaseParams.put("model", resolvedModel);
            releaseParams.put("cache_salt", sessionId);
            releaseParams.put("cache_sharing", true);
            releaseParams.put("messages", sanitizedMessages);
            releaseParams.put("messages_released_index", messagesReleasedIndex);
            if (toolsDict != null && !toolsDict.isEmpty()) {
                releaseParams.put("tools", toolsDict);
            }
            if (toolsReleasedIndex != null) {
                releaseParams.put("tools_released_index", toolsReleasedIndex);
            }

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("client_name", getClientName());
            metadata.put("session_id", sessionId);
            metadata.put("messages_released_index", messagesReleasedIndex);
            metadata.put("tools_released_index", toolsReleasedIndex);
            Loggers.LLM.info("Before release KV cache, release request params ready. {}", metadata);
            HttpResult result = postJson(RELEASE_KV_CACHE_PATH, releaseParams, null);
            if (result.statusCode() == 200) {
                logReleaseSuccess(sessionId, result.body(), resolvedModel);
                return true;
            }
            Loggers.LLM.error("KV cache release failed with status {}. {}",
                    result.statusCode(),
                    Map.of(
                            "client_name", getClientName(),
                            "session_id", sessionId,
                            "status_code", result.statusCode(),
                            "response_body", result.body()
                    ));
            return false;
        } catch (BaseError exception) {
            throw exception;
        } catch (Exception exception) {
            Loggers.LLM.error("KV cache release error: {}", exception.getMessage());
            throw ErrorHelper.buildError(
                    StatusCode.MODEL_CALL_FAILED,
                    null,
                    null,
                    exception,
                    Map.of("error_msg", "Release error: " + exception.getMessage())
            );
        }
    }

    public static boolean supportsKvCacheRelease() {
        return true;
    }

    protected Map<String, Object> makeAsyncRequest(Map<String, Object> params, Float timeout) throws Exception {
        Exception lastError = null;
        int maxRetries = Math.max(0, modelClientConfig.getMaxRetries());
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                Loggers.LLM.debug("Non-stream request (attempt {}/{})", attempt + 1, maxRetries);
                HttpResult result = postJson(CHAT_COMPLETIONS_PATH, params, timeout);
                if (result.statusCode() != 200) {
                    throw new IOException("API returned error " + result.statusCode() + ": " + result.body());
                }
                return parseJsonObject(result.body());
            } catch (Exception exception) {
                lastError = exception;
                Loggers.LLM.error("Request failed: {}", exception.getMessage());
                if (attempt < maxRetries - 1) {
                    sleepBeforeRetry(attempt);
                }
            }
        }
        throw new IOException("Request failed after " + maxRetries + " attempts: "
                + (lastError == null ? "null" : lastError.getMessage()), lastError);
    }

    protected AssistantMessage parseResponse(Map<String, Object> response, BaseOutputParser parser) {
        if (!isPythonTruthy(response.get("choices"))) {
            throw new IllegalArgumentException("API did not return a valid response");
        }

        List<?> choices = asList(response.get("choices"));
        Map<String, Object> choice = asObjectMap(choices.get(0));
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

    protected Iterator<AssistantMessageChunk> streamWithParser(
            Map<String, Object> params,
            BaseOutputParser outputParser,
            Float timeout) throws Exception {
        List<AssistantMessageChunk> parsedChunks = new ArrayList<>();
        StringBuilder accumulatedContent = new StringBuilder();
        Iterator<AssistantMessageChunk> iterator = streamResponse(params, timeout);
        while (iterator.hasNext()) {
            AssistantMessageChunk chunkItem = iterator.next();
            if (isPythonTruthy(chunkItem.getContent())) {
                accumulatedContent.append(chunkItem.getContent());
            }

            Object parserContent = null;
            if (!accumulatedContent.isEmpty()) {
                try {
                    Object parsedResult = outputParser.parse(accumulatedContent.toString()).join();
                    if (parsedResult != null) {
                        parserContent = parsedResult;
                        accumulatedContent.setLength(0);
                    }
                } catch (RuntimeException exception) {
                    Loggers.LLM.debug("Stream parser attempt error. {}", exception.getMessage());
                }
            }

            parsedChunks.add(AssistantMessageChunk.builder()
                    .content(chunkItem.getContent())
                    .reasoningContent(chunkItem.getReasoningContent())
                    .toolCalls(chunkItem.getToolCalls())
                    .usageMetadata(chunkItem.getUsageMetadata())
                    .finishReason(chunkItem.getFinishReason())
                    .parserContent(parserContent)
                    .build());
        }
        return parsedChunks.iterator();
    }

    protected Iterator<AssistantMessageChunk> streamResponse(Map<String, Object> params, Float timeout)
            throws Exception {
        HttpStreamResult result = postStream(CHAT_COMPLETIONS_PATH, params, timeout);
        if (result.statusCode() != 200) {
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
        if (line == null || !line.startsWith("data: ")) {
            return null;
        }

        String dataString = line.substring("data: ".length());
        if ("[DONE]".equals(dataString.strip())) {
            return null;
        }

        try {
            Map<String, Object> chunkData = parseJsonObject(dataString);
            if (!chunkData.containsKey("choices") || !isPythonTruthy(chunkData.get("choices"))) {
                return null;
            }
            Map<String, Object> choice = asObjectMap(asList(chunkData.get("choices")).get(0));
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
        } catch (JsonProcessingException | ClassCastException | IndexOutOfBoundsException exception) {
            String preview = line.length() <= 200 ? line : line.substring(0, 200) + "...";
            Loggers.LLM.warning("Error parsing stream chunk. {} {}", preview, exception.getMessage());
            return null;
        }
    }

    protected HttpResult postJson(String path, Map<String, Object> payload, Float timeout) throws Exception {
        HttpRequest request = requestBuilder(path, payload, timeout).build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return new HttpResult(response.statusCode(), response.body());
    }

    protected HttpStreamResult postStream(String path, Map<String, Object> payload, Float timeout) throws Exception {
        HttpResult result = postJson(path, payload, timeout);
        List<String> lines = result.body() == null ? List.of() : result.body().lines().toList();
        return new HttpStreamResult(result.statusCode(), lines, result.body());
    }

    protected void sleepBeforeRetry(int attempt) throws InterruptedException {
        long waitMillis = (long) Math.pow(2, attempt) * 1000L;
        Thread.sleep(waitMillis);
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

    private HttpRequest.Builder requestBuilder(String path, Map<String, Object> payload, Float timeout)
            throws JsonProcessingException {
        Duration requestTimeout = timeoutDuration(timeout);
        return HttpRequest.newBuilder()
                .uri(URI.create(trimTrailingSlash(modelClientConfig.getApiBase()) + path))
                .timeout(requestTimeout)
                .header("Content-Type", CONTENT_TYPE)
                .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(payload)));
    }

    HttpClient httpClientForTesting() {
        return httpClient;
    }

    private HttpClient createHttpClient(ModelClientConfig clientConfig) {
        return ModelHttpClients.builder(clientConfig, clientConfig.getApiBase())
                .connectTimeout(timeoutDuration(null))
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

    private void logReleaseSuccess(String sessionId, String responseBody, String model) {
        try {
            Map<String, Object> response = parseJsonObject(responseBody);
            Loggers.LLM.info("KV cache release successful. {}", Map.of(
                    "client_name", getClientName(),
                    "session_id", sessionId,
                    "model_name", model,
                    "response", response
            ));
        } catch (JsonProcessingException exception) {
            Loggers.LLM.info("KV cache release successful (non-JSON response). {}", Map.of(
                    "client_name", getClientName(),
                    "session_id", sessionId,
                    "model_name", model,
                    "response_text", responseBody
            ));
        }
    }

    private Object parseContent(BaseOutputParser parser, String content) {
        if (parser == null || !isPythonTruthy(content)) {
            return null;
        }
        try {
            return parser.parse(content).join();
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
        return UsageMetadata.builder()
                .modelName(modelConfig.getModelName())
                .inputTokens(intValue(usage.get("prompt_tokens")))
                .outputTokens(intValue(usage.get("completion_tokens")))
                .totalTokens(intValue(usage.get("total_tokens")))
                .cacheTokens(cacheTokens)
                .build();
    }

    private Map<String, Object> parseJsonObject(String json) throws JsonProcessingException {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        return OBJECT_MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
        });
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

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
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
     * HTTP response snapshot used by the translated aiohttp request helpers.
     *
     * <p>Mirrors Python's aiohttp response object usage in
     * {@code openjiuwen/core/foundation/llm/model_clients/inference_affinity_model_client.py}.</p>
     */
    protected record HttpResult(int statusCode, String body) {
    }

    /**
     * HTTP streaming response snapshot used by the translated SSE helpers.
     *
     * <p>Mirrors Python's aiohttp streaming response iteration in
     * {@code openjiuwen/core/foundation/llm/model_clients/inference_affinity_model_client.py}.</p>
     */
    protected record HttpStreamResult(int statusCode, List<String> lines, String body) {
    }
}
