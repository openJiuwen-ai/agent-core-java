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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Raw HTTP/SSE OpenAI-compatible API client.
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
        String authorization = extractAuthorizationOverride(requestCustomHeaders);
        applyExtraHeadersParam(params, requestCustomHeaders);
        recordTracerData(tracerRecordData, "llm_params", params);

        try {
            Loggers.LLM.info("Before create openai client, model client config params ready. {}",
                    Map.of(
                            "timeout", timeout != null ? timeout : modelClientConfig.getTimeout(),
                            "max_retries", modelClientConfig.getMaxRetries()
                    ));
            Map<String, Object> responseData = postJson(params, timeout, authorization);
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
        String authorization = extractAuthorizationOverride(requestCustomHeaders);
        applyExtraHeadersParam(params, requestCustomHeaders);
        recordTracerData(tracerRecordData, "llm_params", params);

        try {
            return tracingIterator(
                    streamChunks(params, outputParser, timeout, authorization),
                    tracerRecordData
            );
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
        return parseStreamChunk(chunk, null);
    }

    private AssistantMessageChunk parseStreamChunk(
            Map<String, Object> chunk,
            Map<Integer, ToolCallState> toolCallStates) {
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
        List<ToolCall> toolCalls = parseToolCalls(delta.get("tool_calls"), false, toolCallStates);
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

    private Map<String, Object> postJson(
            Map<String, Object> params,
            Float timeout,
            String authorization) throws Exception {
        HttpResponse<String> response = httpClient.send(
                buildRequest(params, timeout, authorization),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        ensureSuccess(response.statusCode(), response.body());
        return parseJsonObject(response.body());
    }

    private Iterator<AssistantMessageChunk> streamChunks(
            Map<String, Object> params,
            BaseOutputParser outputParser,
            Float timeout,
            String authorization) throws Exception {
        HttpResponse<InputStream> response = httpClient.send(
                buildRequest(params, timeout, authorization),
                HttpResponse.BodyHandlers.ofInputStream()
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String body = readBody(response.body());
            ensureSuccess(response.statusCode(), body);
        }
        return new SseChunkIterator(response.body(), outputParser);
    }

    private Iterator<AssistantMessageChunk> tracingIterator(
            Iterator<AssistantMessageChunk> chunks,
            Object tracerRecordData) {
        if (tracerRecordData == null) {
            return chunks;
        }
        return new TracingChunkIterator(chunks, tracerRecordData);
    }

    private HttpRequest buildRequest(
            Map<String, Object> params,
            Float timeout,
            String authorization) throws JsonProcessingException {
        Map<String, Object> body = requestBodyParams(params);
        String bodyJson = OBJECT_MAPPER.writeValueAsString(body);
        String effectiveAuthorization = authorization != null
                ? authorization
                : "Bearer " + modelClientConfig.getApiKey();

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(trimTrailingSlash(modelClientConfig.getApiBase()) + CHAT_COMPLETIONS_PATH))
                .timeout(timeoutDuration(timeout))
                .header("Content-Type", CONTENT_TYPE)
                .header("Authorization", effectiveAuthorization)
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

    private static String extractAuthorizationOverride(Map<String, ?> requestCustomHeaders) {
        if (requestCustomHeaders == null || requestCustomHeaders.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, ?> entry : requestCustomHeaders.entrySet()) {
            String key = entry.getKey();
            if (key == null || !"Authorization".equalsIgnoreCase(key.trim()) || entry.getValue() == null) {
                continue;
            }
            String authorization = String.valueOf(entry.getValue());
            if (!authorization.trim().isEmpty()) {
                return authorization;
            }
        }
        return null;
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
        return parseToolCalls(rawToolCalls, defaultIndex, null);
    }

    private List<ToolCall> parseToolCalls(
            Object rawToolCalls,
            boolean defaultIndex,
            Map<Integer, ToolCallState> toolCallStates) {
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
            Integer resolvedIndex = indexValue instanceof Number number
                    ? number.intValue()
                    : defaultIndex ? index : null;
            if (toolCallStates != null) {
                toolCalls.add(parseStreamingToolCall(toolCallStates, toolCallMap, function, resolvedIndex, index));
                continue;
            }
            toolCalls.add(ToolCall.builder()
                    .id(stringOrEmpty(toolCallMap.get("id")))
                    .type(nonEmptyString(toolCallMap.get("type"), "function"))
                    .name(stringOrEmpty(function.get("name")))
                    .arguments(stringOrEmpty(function.get("arguments")))
                    .index(resolvedIndex)
                    .build());
        }
        return toolCalls;
    }

    private ToolCall parseStreamingToolCall(
            Map<Integer, ToolCallState> toolCallStates,
            Map<String, Object> toolCallMap,
            Map<String, Object> function,
            Integer resolvedIndex,
            int fallbackIndex) {
        int stateIndex = resolvedIndex != null ? resolvedIndex : fallbackIndex;
        ToolCallState state = toolCallStates.get(stateIndex);
        if (state == null) {
            state = new ToolCallState();
            toolCallStates.put(stateIndex, state);
        }
        String id = nonEmptyString(toolCallMap.get("id"), null);
        if (id != null) {
            state.id = id;
        }
        String type = nonEmptyString(toolCallMap.get("type"), null);
        if (type != null) {
            state.type = type;
        }
        String name = nonEmptyString(function.get("name"), null);
        if (name != null) {
            state.name = name;
        }
        state.index = resolvedIndex != null ? resolvedIndex : stateIndex;
        String argumentsDelta = asString(function.get("arguments"));
        if (argumentsDelta == null) {
            argumentsDelta = "";
        }
        state.arguments.append(argumentsDelta);
        return ToolCall.builder()
                .id(state.id)
                .type(nonEmptyString(state.type, "function"))
                .name(nonEmptyString(state.name, ""))
                .arguments(argumentsDelta)
                .index(resolvedIndex)
                .build();
    }

    private final class SseChunkIterator implements Iterator<AssistantMessageChunk>, AutoCloseable {
        private final BufferedReader reader;
        private final BaseOutputParser outputParser;
        private final StringBuilder accumulatedContent = new StringBuilder();
        private final Map<Integer, ToolCallState> toolCallStates = new LinkedHashMap<>();
        private AssistantMessageChunk nextChunk;
        private String pendingToolCallFinishReason = "null";
        private boolean closed;

        private SseChunkIterator(InputStream inputStream, BaseOutputParser outputParser) {
            this.reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            this.outputParser = outputParser;
        }

        @Override
        public boolean hasNext() {
            if (nextChunk != null) {
                return true;
            }
            if (closed) {
                return false;
            }
            nextChunk = readNextChunk();
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

        private AssistantMessageChunk readNextChunk() {
            try {
                String rawLine;
                while ((rawLine = reader.readLine()) != null) {
                    String line = rawLine.strip();
                    if (line.isEmpty() || !line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring("data:".length()).strip();
                    if ("[DONE]".equals(data)) {
                        close();
                        return finalToolCallChunk();
                    }
                    AssistantMessageChunk chunk = parseStreamChunk(parseJsonObject(data), toolCallStates);
                    if (chunk == null) {
                        continue;
                    }
                    AssistantMessageChunk adaptedChunk = adaptToolCallChunk(chunk);
                    if (adaptedChunk == null) {
                        continue;
                    }
                    return applyStreamingParser(adaptedChunk);
                }
                close();
                return finalToolCallChunk();
            } catch (Exception exception) {
                close();
                String detail = errorDetail(exception);
                Loggers.LLM.error("OpenAI API async stream read error. {}", detail);
                throw ErrorHelper.buildError(
                        StatusCode.MODEL_CALL_FAILED,
                        null,
                        null,
                        exception,
                        Map.of("error_msg", "openAI API async stream error: " + detail)
                );
            }
        }

        private AssistantMessageChunk adaptToolCallChunk(AssistantMessageChunk chunk) {
            if (chunk.getToolCalls() == null || chunk.getToolCalls().isEmpty()) {
                return chunk;
            }
            if (!"null".equals(chunk.getFinishReason())) {
                pendingToolCallFinishReason = chunk.getFinishReason();
            }
            if (!hasNonToolOutput(chunk)) {
                return null;
            }
            return AssistantMessageChunk.builder()
                    .content(chunk.getContent())
                    .reasoningContent(chunk.getReasoningContent())
                    .usageMetadata(chunk.getUsageMetadata())
                    .finishReason("null")
                    .promptTokenIds(chunk.getPromptTokenIds())
                    .completionTokenIds(chunk.getCompletionTokenIds())
                    .logprobs(chunk.getLogprobs())
                    .build();
        }

        private boolean hasNonToolOutput(AssistantMessageChunk chunk) {
            return pythonTruthy(chunk.getContent())
                    || pythonTruthy(chunk.getReasoningContent())
                    || chunk.getUsageMetadata() != null
                    || chunk.getPromptTokenIds() != null
                    || chunk.getCompletionTokenIds() != null
                    || chunk.getLogprobs() != null;
        }

        private AssistantMessageChunk finalToolCallChunk() {
            if (toolCallStates.isEmpty()) {
                return null;
            }
            List<ToolCall> toolCalls = new ArrayList<>();
            for (ToolCallState state : toolCallStates.values()) {
                toolCalls.add(ToolCall.builder()
                        .id(state.id)
                        .type(nonEmptyString(state.type, "function"))
                        .name(nonEmptyString(state.name, ""))
                        .arguments(state.arguments.toString())
                        .index(state.index)
                        .build());
            }
            toolCallStates.clear();
            return AssistantMessageChunk.builder()
                    .content("")
                    .toolCalls(toolCalls)
                    .finishReason(pendingToolCallFinishReason)
                    .build();
        }

        private AssistantMessageChunk applyStreamingParser(AssistantMessageChunk chunk) {
            if (outputParser == null) {
                return chunk;
            }
            Object parserContent = parseStreamingContent(chunk.getContent(), outputParser, accumulatedContent);
            return AssistantMessageChunk.builder()
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

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                reader.close();
            } catch (IOException exception) {
                Loggers.LLM.debug("Failed to close OpenAI stream reader. {}", exception.getMessage());
            }
        }
    }

    private final class TracingChunkIterator implements Iterator<AssistantMessageChunk>, AutoCloseable {
        private final Iterator<AssistantMessageChunk> delegate;
        private final Object tracerRecordData;
        private final List<AssistantMessageChunk> consumedChunks = new ArrayList<>();
        private int lastRecordedChunkCount;
        private boolean finalRecorded;

        private TracingChunkIterator(Iterator<AssistantMessageChunk> delegate, Object tracerRecordData) {
            this.delegate = delegate;
            this.tracerRecordData = tracerRecordData;
        }

        @Override
        public boolean hasNext() {
            boolean hasNext = delegate.hasNext();
            if (!hasNext) {
                recordFinalOnce();
            }
            return hasNext;
        }

        @Override
        public AssistantMessageChunk next() {
            try {
                AssistantMessageChunk chunk = delegate.next();
                consumedChunks.add(chunk);
                return chunk;
            } catch (NoSuchElementException exception) {
                recordFinalOnce();
                throw exception;
            }
        }

        @Override
        public void close() throws Exception {
            try {
                if (delegate instanceof AutoCloseable closeable) {
                    closeable.close();
                }
            } finally {
                recordFinalOnce();
            }
        }

        private void recordFinalOnce() {
            if (finalRecorded) {
                return;
            }
            finalRecorded = true;
            if (consumedChunks.isEmpty() || consumedChunks.size() == lastRecordedChunkCount) {
                return;
            }
            recordResponse();
        }

        private void recordResponse() {
            if (consumedChunks.isEmpty()) {
                return;
            }
            lastRecordedChunkCount = consumedChunks.size();
            recordTracerData(tracerRecordData, "llm_response", mergeChunks(consumedChunks));
        }
    }

    private static final class ToolCallState {
        private String id;
        private String type = "function";
        private String name = "";
        private Integer index;
        private final StringBuilder arguments = new StringBuilder();
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

    private static String readBody(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }
        try (inputStream) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    HttpClient httpClientForTesting() {
        return httpClient;
    }

    private HttpClient createHttpClient(ModelClientConfig clientConfig) {
        return ModelHttpClients.builder(clientConfig, clientConfig.getApiBase())
                .connectTimeout(timeoutDuration(null))
                .withSsl()
                .withExplicitPortProxy()
                .build();
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

    private static String nonEmptyString(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value);
        return text.isEmpty() ? defaultValue : text;
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
