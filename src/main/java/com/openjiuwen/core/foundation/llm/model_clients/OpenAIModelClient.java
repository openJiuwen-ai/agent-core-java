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
import com.openjiuwen.core.common.security.SslUtils;
import com.openjiuwen.core.common.security.UrlUtils;
import com.openjiuwen.core.foundation.llm.HeadersHelper;
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
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * OpenAI API client supporting GPT models and OpenAI-compatible services.
 *
 * <p>Mirrors Python's {@code OpenAIModelClient} in
 * {@code openjiuwen/core/foundation/llm/model_clients/openai_model_client.py}.</p>
 */
public class OpenAIModelClient extends BaseModelClient {

    public static final List<String> __client_name__ = List.of(
            ProviderType.OPEN_AI.getValue(),
            ProviderType.OPEN_ROUTER.getValue()
    );
    public static final List<String> CLIENT_NAMES = __client_name__;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final String CONTENT_TYPE = "application/json";

    private final HttpClient httpClient;
    private final Map<String, String> baseHeaders;

    static {
        registerClientClass(OpenAIModelClient.class);
    }

    /**
     * Initialize OpenAI model client.
     *
     * @param modelConfig model request configuration
     * @param modelClientConfig model client connection configuration
     */
    public OpenAIModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        super(modelConfig, modelClientConfig);
        this.httpClient = createHttpClient(modelClientConfig);
        this.baseHeaders = HeadersHelper.buildBaseHeaders(modelClientConfig.getCustomHeaders());
    }

    /**
     * Initialize from Python-style registry kwargs.
     *
     * <p>Mirrors Python's constructor inputs in
     * {@code openjiuwen/core/foundation/llm/model_clients/openai_model_client.py}.</p>
     *
     * @param kwargs registry kwargs
     */
    public OpenAIModelClient(Map<String, Object> kwargs) {
        this(
                (ModelRequestConfig) kwargs.get("model_config"),
                (ModelClientConfig) kwargs.get("model_client_config")
        );
    }

    @Override
    protected String getClientName() {
        return "OpenAI client";
    }

    static Map<String, String> buildRequestHeaders(
            Map<String, ?> baseHeaders,
            Map<String, ?> requestHeaders) {
        return HeadersHelper.mergeRequestHeaders(baseHeaders, requestHeaders);
    }

    @Override
    protected Map<String, Object> buildRequestParams(
            Object messages,
            Object tools,
            Number temperature,
            Number topP,
            String model,
            String stop,
            Integer maxTokens,
            boolean stream,
            Map<String, Object> kwargs) {
        Map<String, Object> params = super.buildRequestParams(
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

        String apiBase = modelClientConfig.getApiBase() == null
                ? ""
                : modelClientConfig.getApiBase().toLowerCase();
        if (apiBase.contains("openai.com")) {
            boolean hasTemperature = params.containsKey("temperature") && params.get("temperature") != null;
            boolean hasTopP = params.containsKey("top_p") && params.get("top_p") != null;
            if (hasTemperature && hasTopP) {
                params.remove("top_p");
            }
        }
        return params;
    }

    Map<String, Object> buildPreparedParams(
            Object messages,
            Object tools,
            Float temperature,
            Float topP,
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
        if (stream) {
            ensureStreamUsageOption(params);
        }
        moveReturnTokenIdsToExtraBody(params);
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
        Object tracerRecordData = popTracerRecordData(effectiveKwargs);
        Map<String, ?> requestCustomHeaders = popRequestCustomHeaders(effectiveKwargs);
        Map<String, Object> params = buildPreparedParams(
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
        applyExtraHeadersParam(params, requestCustomHeaders);
        recordTracerData(tracerRecordData, "llm_params", params);

        try {
            Loggers.LLM.info("Before create openai client, model client config params ready. {}",
                    Map.of(
                            "timeout", timeout != null ? timeout : modelClientConfig.getTimeout(),
                            "max_retries", modelClientConfig.getMaxRetries()
                    ));
            Map<String, Object> responseData = postJson(params, timeout);
            Loggers.LLM.info("OpenAI API response received. {}", Map.of("response", responseData));
            AssistantMessage assistantMessage = parseResponse(responseData, outputParser);
            recordTracerData(tracerRecordData, "llm_response", assistantMessage);
            return assistantMessage;
        } catch (Exception exception) {
            Loggers.LLM.error("OpenAI API async invoke error. {}", exception.getMessage());
            throw ErrorHelper.buildError(
                    StatusCode.MODEL_CALL_FAILED,
                    null,
                    null,
                    exception,
                    Map.of("error_msg", "openAI API async invoke error: " + exception.getMessage())
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
        Object tracerRecordData = popTracerRecordData(effectiveKwargs);
        Map<String, ?> requestCustomHeaders = popRequestCustomHeaders(effectiveKwargs);
        Map<String, Object> params = buildPreparedParams(
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
        applyExtraHeadersParam(params, requestCustomHeaders);
        recordTracerData(tracerRecordData, "llm_params", params);

        try {
            List<AssistantMessageChunk> chunks = streamChunks(params, outputParser, timeout);
            recordTracerData(tracerRecordData, "llm_response", mergeChunks(chunks));
            return chunks.iterator();
        } catch (Exception exception) {
            String detail = errorDetail(exception);
            Loggers.LLM.error("OpenAI API async stream error. {}", detail);
            throw ErrorHelper.buildError(
                    StatusCode.MODEL_CALL_FAILED,
                    null,
                    null,
                    exception,
                    Map.of("error_msg", "openAI API async stream error: " + detail)
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

    protected AssistantMessage parseResponse(Map<String, Object> response, BaseOutputParser parser) {
        List<Map<String, Object>> choices = asListOfObjectMaps(response.get("choices"));
        if (choices.isEmpty()) {
            throw new IllegalArgumentException("No choices in response: " + response);
        }
        Map<String, Object> choice = choices.get(0);
        Map<String, Object> message = asObjectMap(choice.get("message"));

        List<ToolCall> toolCalls = parseToolCalls(message.get("tool_calls"), true);
        UsageMetadata usageMetadata = parseUsageMetadata(response.get("usage"), true);
        Object content = pythonTruthy(message.get("content")) ? message.get("content") : "";
        Object parserContent = parseContent(parser, content);

        return AssistantMessage.builder()
                .content(content)
                .toolCalls(toolCalls.isEmpty() ? null : toolCalls)
                .usageMetadata(usageMetadata)
                .finishReason(toolCalls.isEmpty() ? "stop" : "tool_calls")
                .reasoningContent(asString(message.get("reasoning_content")))
                .parserContent(parserContent)
                .promptTokenIds(integerList(response.get("prompt_token_ids")))
                .completionTokenIds(integerList(choice.get("token_ids")))
                .logprobs(normalizeLogprobs(choice.get("logprobs")))
                .build();
    }

    protected AssistantMessageChunk parseStreamChunk(Map<String, Object> chunk) {
        UsageMetadata usageMetadata = parseUsageMetadata(chunk.get("usage"), false);
        List<Integer> promptTokenIds = integerList(chunk.get("prompt_token_ids"));
        List<Map<String, Object>> choices = asListOfObjectMaps(chunk.get("choices"));

        if (choices.isEmpty()) {
            if (usageMetadata != null || promptTokenIds != null) {
                return AssistantMessageChunk.builder()
                        .content("")
                        .usageMetadata(usageMetadata)
                        .finishReason("null")
                        .promptTokenIds(promptTokenIds)
                        .build();
            }
            return null;
        }

        Map<String, Object> choice = choices.get(0);
        Map<String, Object> delta = asObjectMap(choice.get("delta"));
        List<ToolCall> toolCalls = parseToolCalls(delta.get("tool_calls"), false);
        List<Integer> completionTokenIds = firstNonNull(
                integerList(choice.get("token_ids")),
                integerList(delta.get("token_ids"))
        );

        return AssistantMessageChunk.builder()
                .content(pythonTruthy(delta.get("content")) ? delta.get("content") : "")
                .reasoningContent(asString(delta.get("reasoning_content")))
                .toolCalls(toolCalls.isEmpty() ? null : toolCalls)
                .usageMetadata(usageMetadata)
                .finishReason(pythonTruthy(choice.get("finish_reason"))
                        ? String.valueOf(choice.get("finish_reason"))
                        : "null")
                .promptTokenIds(promptTokenIds)
                .completionTokenIds(completionTokenIds)
                .logprobs(normalizeLogprobs(choice.get("logprobs")))
                .build();
    }

    private Map<String, Object> postJson(Map<String, Object> params, Float timeout) throws Exception {
        HttpResponse<String> response = httpClient.send(
                buildRequest(params, timeout),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        ensureSuccess(response.statusCode(), response.body());
        return parseJsonObject(response.body());
    }

    private List<AssistantMessageChunk> streamChunks(
            Map<String, Object> params,
            BaseOutputParser outputParser,
            Float timeout) throws Exception {
        HttpResponse<String> response = httpClient.send(
                buildRequest(params, timeout),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        ensureSuccess(response.statusCode(), response.body());

        List<AssistantMessageChunk> chunks = new ArrayList<>();
        StringBuilder accumulatedContent = new StringBuilder();
        for (String rawLine : response.body().lines().toList()) {
            AssistantMessageChunk chunk = parseStreamLine(rawLine);
            if (chunk == null) {
                continue;
            }
            if (outputParser != null) {
                Object parserContent = parseStreamingContent(chunk.getContent(), outputParser, accumulatedContent);
                chunk = AssistantMessageChunk.builder()
                        .content(chunk.getContent())
                        .reasoningContent(chunk.getReasoningContent())
                        .toolCalls(chunk.getToolCalls())
                        .usageMetadata(chunk.getUsageMetadata())
                        .finishReason(chunk.getFinishReason())
                        .parserContent(parserContent)
                        .promptTokenIds(chunk.getPromptTokenIds())
                        .completionTokenIds(chunk.getCompletionTokenIds())
                        .logprobs(chunk.getLogprobs())
                        .build();
            }
            chunks.add(chunk);
        }
        return chunks;
    }

    private AssistantMessageChunk parseStreamLine(String rawLine) throws JsonProcessingException {
        if (rawLine == null) {
            return null;
        }
        String line = rawLine.strip();
        if (line.isEmpty() || !line.startsWith("data:")) {
            return null;
        }
        String data = line.substring("data:".length()).strip();
        if ("[DONE]".equals(data)) {
            return null;
        }
        return parseStreamChunk(parseJsonObject(data));
    }

    private HttpRequest buildRequest(Map<String, Object> params, Float timeout) throws JsonProcessingException {
        Map<String, Object> body = requestBodyParams(params);
        String bodyJson = OBJECT_MAPPER.writeValueAsString(body);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(trimTrailingSlash(modelClientConfig.getApiBase()) + CHAT_COMPLETIONS_PATH))
                .timeout(timeoutDuration(timeout))
                .header("Content-Type", CONTENT_TYPE)
                .header("Authorization", "Bearer " + modelClientConfig.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8));

        Map<String, String> extraHeaders = extractExtraHeaders(params.get("extra_headers"));
        for (Map.Entry<String, String> entry : extraHeaders.entrySet()) {
            builder.setHeader(entry.getKey(), entry.getValue());
        }
        return builder.build();
    }

    private Map<String, Object> requestBodyParams(Map<String, Object> params) {
        Map<String, Object> body = new LinkedHashMap<>(params);
        body.remove("extra_headers");

        Object extraBody = body.remove("extra_body");
        if (extraBody instanceof Map<?, ?> rawMap) {
            body.putAll(toObjectMap(rawMap));
        }
        return body;
    }

    private void applyExtraHeadersParam(Map<String, Object> params, Map<String, ?> requestCustomHeaders) {
        Map<String, String> effectiveHeaders = buildRequestHeaders(baseHeaders, requestCustomHeaders);
        if (!effectiveHeaders.isEmpty()) {
            params.put("extra_headers", effectiveHeaders);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, ?> popRequestCustomHeaders(Map<String, Object> kwargs) {
        Object requestHeaders = kwargs.remove("custom_headers");
        if (requestHeaders == null) {
            requestHeaders = kwargs.remove("customHeaders");
        }
        if (requestHeaders instanceof Map<?, ?> map) {
            return (Map<String, ?>) map;
        }
        return null;
    }

    private Object popTracerRecordData(Map<String, Object> kwargs) {
        Object tracer = kwargs.remove("tracer_record_data");
        if (tracer == null) {
            tracer = kwargs.remove("tracerRecordData");
        }
        return tracer;
    }

    @SuppressWarnings("unchecked")
    private void recordTracerData(Object tracerRecordData, String key, Object value) {
        if (tracerRecordData == null) {
            return;
        }
        Map<String, Object> payload = Map.of(key, value);
        try {
            if (tracerRecordData instanceof Consumer<?> consumer) {
                ((Consumer<Map<String, Object>>) consumer).accept(payload);
                return;
            }
            if (tracerRecordData instanceof Function<?, ?> function) {
                ((Function<Map<String, Object>, Object>) function).apply(payload);
            }
        } catch (Exception exception) {
            Loggers.LLM.warning("Failed to record LLM tracer data. {}", exception.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void moveReturnTokenIdsToExtraBody(Map<String, Object> params) {
        if (!params.containsKey("return_token_ids")) {
            return;
        }
        Map<String, Object> extraBody = new LinkedHashMap<>();
        Object currentExtraBody = params.get("extra_body");
        if (currentExtraBody instanceof Map<?, ?> currentMap) {
            extraBody.putAll(toObjectMap(currentMap));
        }
        extraBody.put("return_token_ids", params.remove("return_token_ids"));
        params.put("extra_body", extraBody);
    }

    @SuppressWarnings("unchecked")
    private void ensureStreamUsageOption(Map<String, Object> params) {
        Object streamOptions = params.get("stream_options");
        if (streamOptions instanceof Map<?, ?> rawMap) {
            Map<String, Object> options = new LinkedHashMap<>(toObjectMap(rawMap));
            options.putIfAbsent("include_usage", true);
            params.put("stream_options", options);
        } else if (streamOptions == null) {
            params.put("stream_options", new LinkedHashMap<>(Map.of("include_usage", true)));
        }
    }

    private Object parseContent(BaseOutputParser parser, Object content) {
        if (parser == null || !pythonTruthy(content)) {
            return null;
        }
        try {
            return parser.parse(content).join();
        } catch (RuntimeException exception) {
            Loggers.LLM.warning("Parser parse error. {}", exception.getMessage());
            return null;
        }
    }

    private Object parseStreamingContent(Object content,
                                         BaseOutputParser parser,
                                         StringBuilder accumulatedContent) {
        if (parser == null || !pythonTruthy(content)) {
            return null;
        }
        accumulatedContent.append(stringify(content));
        try {
            Object parsed = parser.parse(accumulatedContent.toString()).join();
            if (parsed != null) {
                accumulatedContent.setLength(0);
            }
            return parsed;
        } catch (RuntimeException exception) {
            Loggers.LLM.debug("Stream parser attempt error. {}", exception.getMessage());
            return null;
        }
    }

    private List<ToolCall> parseToolCalls(Object rawToolCalls, boolean defaultIndex) {
        if (!(rawToolCalls instanceof List<?> rawList)) {
            return List.of();
        }
        List<ToolCall> toolCalls = new ArrayList<>();
        for (int index = 0; index < rawList.size(); index++) {
            Object rawItem = rawList.get(index);
            if (!(rawItem instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, Object> toolCallMap = toObjectMap(rawMap);
            Map<String, Object> function = asObjectMap(toolCallMap.get("function"));
            Object indexValue = toolCallMap.get("index");
            toolCalls.add(ToolCall.builder()
                    .id(stringOrEmpty(toolCallMap.get("id")))
                    .type("function")
                    .name(stringOrEmpty(function.get("name")))
                    .arguments(stringOrEmpty(function.get("arguments")))
                    .index(indexValue instanceof Number number
                            ? number.intValue()
                            : defaultIndex ? index : null)
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

    private AssistantMessageChunk mergeChunks(List<AssistantMessageChunk> chunks) {
        AssistantMessageChunk merged = null;
        for (AssistantMessageChunk chunk : chunks) {
            merged = merged == null ? chunk : (AssistantMessageChunk) merged.merge(chunk);
        }
        return merged;
    }

    private Map<String, Object> parseJsonObject(String json) throws JsonProcessingException {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        return OBJECT_MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
        });
    }

    private static void ensureSuccess(int statusCode, String body) throws IOException {
        if (statusCode >= 200 && statusCode < 300) {
            return;
        }
        throw new IOException("HTTP " + statusCode + ": " + (body == null ? "" : body));
    }

    private HttpClient createHttpClient(ModelClientConfig clientConfig) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(timeoutDuration(null));
        SslUtils.configureHttpClientSsl(
                builder,
                clientConfig.getApiBase(),
                clientConfig.isVerifySsl(),
                clientConfig.getSslCert());
        String proxyUrl = UrlUtils.getGlobalProxyUrl(clientConfig.getApiBase());
        if (proxyUrl != null && !proxyUrl.isBlank()) {
            URI proxyUri = URI.create(proxyUrl);
            if (proxyUri.getHost() != null && proxyUri.getPort() > 0) {
                builder.proxy(ProxySelector.of(new InetSocketAddress(proxyUri.getHost(), proxyUri.getPort())));
            }
        }
        return builder.build();
    }

    private Duration timeoutDuration(Float timeout) {
        double seconds = timeout != null ? timeout.doubleValue() : modelClientConfig.getTimeout();
        long millis = Math.max(1L, Math.round(seconds * 1000.0D));
        return Duration.ofMillis(millis);
    }

    private static Map<String, String> extractExtraHeaders(Object extraHeaders) {
        if (!(extraHeaders instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                result.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }
        return result;
    }

    private static List<Map<String, Object>> asListOfObjectMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add(toObjectMap(map));
            }
        }
        return result;
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

    private static List<Integer> integerList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return null;
        }
        List<Integer> result = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Number number) {
                result.add(number.intValue());
            }
        }
        return result.isEmpty() ? null : result;
    }

    private static Object normalizeLogprobs(Object logprobs) {
        return pythonTruthy(logprobs) ? logprobs : null;
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

    private static String stringify(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String text) {
            return text;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ignored) {
            return String.valueOf(value);
        }
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

    private static String errorDetail(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getClass().getSimpleName() + ": " + message;
    }

    private static <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }

    private static boolean pythonTruthy(Object value) {
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
}
