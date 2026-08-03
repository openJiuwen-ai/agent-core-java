/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.security.OkHttpProxySupport;
import com.openjiuwen.core.common.security.SslUtils;
import com.openjiuwen.core.foundation.llm.ModelCircuitBreaker;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;

import okhttp3.Call;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

/**
 * Basic OpenAI-compatible HTTP client used by the built-in providers.
 * 
 * @since 0.1.7
 */
public class OpenAiCompatibleModelClient extends BaseModelClient {
    private static final Logger LOG = LoggerFactory.getLogger(OpenAiCompatibleModelClient.class);

    /**
     * ObjectMapper.
     * 
     * @since 0.1.7
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * MediaType.get.
     * 
     * @param charset=utf-8" charset=utf-8"
     * @since 0.1.7
     */
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    /** Default per-call cap (seconds) when caller does not specify one. */
    private static final float DEFAULT_CALL_TIMEOUT_SECONDS = 180f;

    /** Override OkHttp Dispatcher.maxRequests (default OkHttp=64). */
    private static final String MAX_REQUESTS_PROPERTY = "openjiuwen.llm.http.max-requests";

    /**
     * Override OkHttp Dispatcher.maxRequestsPerHost. OkHttp default is 5, which
     * serializes DeepAgent multi-session load against one LLM host.
     */
    private static final String MAX_REQUESTS_PER_HOST_PROPERTY = "openjiuwen.llm.http.max-requests-per-host";

    /** Override OkHttp ConnectionPool max idle connections (default OkHttp=5). */
    private static final String MAX_IDLE_CONNECTIONS_PROPERTY = "openjiuwen.llm.http.max-idle-connections";

    /** Override OkHttp ConnectionPool keep-alive seconds (default OkHttp=300). */
    private static final String KEEP_ALIVE_SECONDS_PROPERTY = "openjiuwen.llm.http.keep-alive-seconds";

    private static final int DEFAULT_MAX_REQUESTS = 64;
    private static final int DEFAULT_MAX_REQUESTS_PER_HOST = 32;
    private static final int DEFAULT_MAX_IDLE_CONNECTIONS = 32;
    private static final long DEFAULT_KEEP_ALIVE_SECONDS = 30L;

    /** Cap connect timeout so refused backends fail fast without waiting full read timeout. */
    private static final String CONNECT_TIMEOUT_SECONDS_PROPERTY = "openjiuwen.llm.http.connect-timeout-seconds";
    private static final long DEFAULT_CONNECT_TIMEOUT_SECONDS = 10L;

    private final OkHttpClient httpClient;
    private final ModelCircuitBreaker circuitBreaker = new ModelCircuitBreaker();

    /**
     * OpenAiCompatibleModelClient.
     * 
     * @param modelConfig modelConfig
     * @param modelClientConfig modelClientConfig
     * @since 0.1.7
     */
    public OpenAiCompatibleModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        super(modelConfig, modelClientConfig);
        this.httpClient = buildOkHttpClient(modelClientConfig.getTimeout());
    }

    /**
     * getClientName.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    protected String getClientName() {
        return "OpenAI-compatible client";
    }

    /**
     * validateConfig.
     * 
     * @since 0.1.7
     */
    @Override
    protected void validateConfig() {
        if (modelClientConfig.getApiKey() == null || modelClientConfig.getApiKey().isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.MODEL_SERVICE_CONFIG_ERROR, "error_msg",
                    "model client config api_key is required for OpenAI-compatible client.");
        }
        if (modelClientConfig.getApiBase() == null || modelClientConfig.getApiBase().isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.MODEL_SERVICE_CONFIG_ERROR, "error_msg",
                    "model client config api_base is required for OpenAI-compatible client.");
        }
    }

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
    @Override
    public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP, String model,
            Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout, Map<String, Object> kwargs)
            throws Exception {
        Map<String, Object> params =
            buildRequestParams(messages, tools, temperature != null ? temperature.doubleValue() : null,
                    topP != null ? topP.doubleValue() : null, model, stop, maxTokens, false, kwargs);
        recordRequestTrace(params);

        Call call = httpClient.newCall(buildRequest(params, timeout));
        applyCallTimeout(call, timeout);
        try (Response response = executeCall(call)) {
            String responseBody = responseBody(response);
            ensureSuccess(response.code(), responseBody);

            @SuppressWarnings("unchecked")
            Map<String, Object> responseMap = MAPPER.readValue(responseBody, Map.class);
            AssistantMessage result =
                parseAssistantMessage(responseMap, resolveModelName(model, responseMap), outputParser);
            circuitBreaker.onSuccess();
            return result;
        }
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
    @Override
    public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP,
            String model, Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout,
            Map<String, Object> kwargs) throws Exception {
        Map<String, Object> params =
            buildRequestParams(messages, tools, temperature != null ? temperature.doubleValue() : null,
                    topP != null ? topP.doubleValue() : null, model, stop, maxTokens, true, kwargs);
        recordRequestTrace(params);

        Call call = httpClient.newCall(buildRequest(params, timeout));
        applyCallTimeout(call, timeout);
        Response response = executeCall(call);
        try {
            ensureSuccess(response.code(), responseBodyOrNull(response));
            ResponseBody body = response.body();
            if (body == null) {
                response.close();
                throw ErrorHelper.buildError(StatusCode.MODEL_CALL_FAILED, "error_msg", "No response body");
            }
            circuitBreaker.onSuccess();
            return new StreamingChunkIterator(body.byteStream(), resolveModelName(model, null), outputParser);
        } catch (RuntimeException | Error e) {
            response.close();
            throw e;
        }
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
    @Override
    public ImageGenerationResponse generateImage(List<UserMessage> messages, String model, String size,
            String negativePrompt, int n, boolean promptExtend, boolean watermark, int seed, Map<String, Object> kwargs)
            throws Exception {
        throw new UnsupportedOperationException("Image generation is not supported by the built-in HTTP client");
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
    @Override
    public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model, String voice,
            String languageType, Map<String, Object> kwargs) throws Exception {
        throw new UnsupportedOperationException("Speech generation is not supported by the built-in HTTP client");
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
    @Override
    public VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl, String audioUrl,
            String model, String size, String resolution, int duration, boolean promptExtend, boolean watermark,
            String negativePrompt, Integer seed, Map<String, Object> kwargs) throws Exception {
        throw new UnsupportedOperationException("Video generation is not supported by the built-in HTTP client");
    }

    /**
     * buildRequest.
     * 
     * @param params params
     * @param timeoutOverride timeoutOverride
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    private Request buildRequest(Map<String, Object> params, Float timeoutOverride) throws Exception {
        String body = MAPPER.writeValueAsString(params);
        Request.Builder builder = new Request.Builder().url(normalizedApiBase() + "/chat/completions");
        applyConfiguredHeaders(builder, true);
        builder.post(RequestBody.create(body, JSON));
        return builder.build();
    }

    /**
     * Builds an OkHttp client with connect/read/write timeouts, optional proxy, and SSL from model client config.
     * <p>
     * Explicitly raises per-host concurrency and shortens keep-alive so multi-session DeepAgent
     * load is not capped by OkHttp defaults (maxRequestsPerHost=5, keepAlive=5min).
     *
     * @param timeoutSeconds timeout applied to connect/read/write
     * @return configured {@link OkHttpClient}
     * @since 0.1.7
     */
    private OkHttpClient buildOkHttpClient(double timeoutSeconds) {
        Duration readWriteTimeout = resolveTimeout(timeoutSeconds);
        Duration connectTimeout = resolveConnectTimeout(timeoutSeconds);
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(resolvePositiveInt(MAX_REQUESTS_PROPERTY, DEFAULT_MAX_REQUESTS));
        dispatcher.setMaxRequestsPerHost(
                resolvePositiveInt(MAX_REQUESTS_PER_HOST_PROPERTY, DEFAULT_MAX_REQUESTS_PER_HOST));
        ConnectionPool connectionPool = new ConnectionPool(
                resolvePositiveInt(MAX_IDLE_CONNECTIONS_PROPERTY, DEFAULT_MAX_IDLE_CONNECTIONS),
                resolvePositiveLong(KEEP_ALIVE_SECONDS_PROPERTY, DEFAULT_KEEP_ALIVE_SECONDS), TimeUnit.SECONDS);
        OkHttpClient.Builder builder = new OkHttpClient.Builder().connectTimeout(connectTimeout)
                .readTimeout(readWriteTimeout).writeTimeout(readWriteTimeout).dispatcher(dispatcher)
                .connectionPool(connectionPool);
        OkHttpProxySupport.configureFromEnvironment(builder, modelClientConfig.getApiBase());
        SslUtils.configureOkHttpClientSsl(builder, modelClientConfig.getApiBase(), modelClientConfig.isVerifySsl(),
                modelClientConfig.getSslCert());
        return builder.build();
    }

    /**
     * Execute an OkHttp call under the model circuit breaker. On connect-level failure,
     * evict the idle pool so subsequent requests do not reuse a poisoned keep-alive connection.
     *
     * @param call call
     * @return response
     * @throws IOException IOException
     * @since 0.1.14
     */
    private Response executeCall(Call call) throws IOException {
        circuitBreaker.beforeCall();
        try {
            return call.execute();
        } catch (IOException e) {
            circuitBreaker.onFailure(e);
            if (ModelCircuitBreaker.isConnectFailure(e)) {
                httpClient.connectionPool().evictAll();
                LOG.warn("Evicted OkHttp connection pool after connect failure: {}", e.toString());
            }
            throw e;
        }
    }

    /**
     * Connect timeout is capped so a refused LLM host fails fast; read/write keep the full model timeout
     * for long generations / SSE stalls (OkHttp interrupts {@code readLine()} via socket readTimeout).
     *
     * @param timeoutSeconds configured model timeout
     * @return connect timeout duration
     * @since 0.1.14
     */
    private static Duration resolveConnectTimeout(double timeoutSeconds) {
        long configuredCap =
            resolvePositiveLong(CONNECT_TIMEOUT_SECONDS_PROPERTY, DEFAULT_CONNECT_TIMEOUT_SECONDS);
        long seconds = Math.max(1L, Math.min(configuredCap, Math.round(Math.max(1.0, timeoutSeconds))));
        return Duration.ofSeconds(seconds);
    }

    /**
     * Resolve a positive int from a system property, falling back to {@code defaultValue}.
     *
     * @param propertyName system property key
     * @param defaultValue fallback when missing or invalid
     * @return parsed positive int, or {@code defaultValue}
     * @since 0.1.14
     */
    private static int resolvePositiveInt(String propertyName, int defaultValue) {
        String raw = System.getProperty(propertyName);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Math.max(1, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException ex) {
            // Invalid override — keep the hard-coded default.
            return defaultValue;
        }
    }

    /**
     * Resolve a positive long from a system property, falling back to {@code defaultValue}.
     *
     * @param propertyName system property key
     * @param defaultValue fallback when missing or invalid
     * @return parsed positive long, or {@code defaultValue}
     * @since 0.1.14
     */
    private static long resolvePositiveLong(String propertyName, long defaultValue) {
        String raw = System.getProperty(propertyName);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Math.max(1L, Long.parseLong(raw.trim()));
        } catch (NumberFormatException ex) {
            // Invalid override — keep the hard-coded default.
            return defaultValue;
        }
    }

    /**
     * applyConfiguredHeaders.
     * 
     * @param builder builder
     * @param includeJsonContentType includeJsonContentType
     * @since 0.1.7
     */
    private void applyConfiguredHeaders(Request.Builder builder, boolean includeJsonContentType) {
        if (includeJsonContentType) {
            builder.header("Content-Type", "application/json");
        }
        if (modelClientConfig.getApiKey() != null && !modelClientConfig.getApiKey().isBlank()) {
            builder.header("Authorization", "Bearer " + modelClientConfig.getApiKey().strip());
        }
        for (Map.Entry<String, String> entry : modelClientConfig.getHeaders().entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                continue;
            }
            builder.header(entry.getKey(), entry.getValue());
        }
    }

    /**
     * responseBody.
     *
     * @param response response
     * @return the result
     * @throws IOException IOException
     * @since 0.1.7
     */
    private static String responseBody(Response response) throws IOException {
        ResponseBody body = response.body();
        return body == null ? "" : body.string();
    }

    /**
     * responseBodyOrNull.
     *
     * @param response response
     * @return the result
     * @throws IOException IOException
     * @since 0.1.7
     */
    private static String responseBodyOrNull(Response response) throws IOException {
        if (response.isSuccessful()) {
            return null;
        }
        return responseBody(response);
    }

    /**
     * applyCallTimeout.
     *
     * @param call call
     * @param timeoutOverride timeoutOverride
     * @since 0.1.7
     */
    private static void applyCallTimeout(Call call, Float timeoutOverride) {
        float effective = timeoutOverride != null ? timeoutOverride.floatValue() : DEFAULT_CALL_TIMEOUT_SECONDS;
        call.timeout().timeout(resolveTimeout(effective).toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * normalizedApiBase.
     * 
     * @return the result
     * @since 0.1.7
     */
    private String normalizedApiBase() {
        return modelClientConfig.getApiBase().strip().replaceAll("/+$", "");
    }

    /**
     * resolveTimeout.
     * 
     * @param seconds seconds
     * @return the result
     * @since 0.1.7
     */
    private static Duration resolveTimeout(double seconds) {
        long millis = Math.max(1_000L, Math.round(seconds * 1_000));
        return Duration.ofMillis(millis);
    }

    /**
     * ensureSuccess.
     * 
     * @param statusCode statusCode
     * @param body body
     * @since 0.1.7
     */
    private static void ensureSuccess(int statusCode, String body) {
        if (statusCode >= 200 && statusCode < 300) {
            return;
        }
        String responseBody = body == null ? "" : body;
        throw new RuntimeException("HTTP " + statusCode + ": " + responseBody);
    }

    /**
     * parseAssistantMessage.
     * 
     * @param responseMap responseMap
     * @param resolvedModel resolvedModel
     * @param outputParser outputParser
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    private AssistantMessage parseAssistantMessage(Map<String, Object> responseMap, String resolvedModel,
            BaseOutputParser outputParser) throws Exception {
        List<Map<String, Object>> choices = asListOfMaps(responseMap.get("choices"));
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("No choices in response: " + responseMap);
        }

        Map<String, Object> choice = choices.get(0);
        Map<String, Object> message = asMap(choice.get("message"));
        if (message == null) {
            throw new RuntimeException("No message in response choice: " + choice);
        }

        Object content = message.get("content");
        if (content == null) {
            content = "";
        }
        Object parserContent = parseWithOutputParser(content, outputParser);
        List<ToolCall> toolCalls = AssistantMessage.convertOpenAiToolCalls(asListOfMaps(message.get("tool_calls")));

        return AssistantMessage.builder().content(content).toolCalls(toolCalls)
                .usageMetadata(buildUsageMetadata(responseMap.get("usage"), resolvedModel))
                .finishReason(resolveFinishReason(choice.get("finish_reason"), toolCalls)).parserContent(parserContent)
                .reasoningContent(asString(message.get("reasoning_content"))).build();
    }

    /**
     * parseStreamChunk.
     * 
     * @param event event
     * @param resolvedModel resolvedModel
     * @param outputParser outputParser
     * @param parserBuffer parserBuffer
     * @return the result
     * @since 0.1.7
     */
    private AssistantMessageChunk parseStreamChunk(Map<String, Object> event, String resolvedModel,
            BaseOutputParser outputParser, StringBuilder parserBuffer) {
        List<Map<String, Object>> choices = asListOfMaps(event.get("choices"));
        UsageMetadata usageMetadata = buildUsageMetadata(event.get("usage"), resolvedModel);

        if (choices == null || choices.isEmpty()) {
            if (usageMetadata == null) {
                return null;
            }
            return AssistantMessageChunk.builder().content("").usageMetadata(usageMetadata).finishReason("null")
                    .build();
        }

        Map<String, Object> choice = choices.get(0);
        Map<String, Object> delta = asMap(choice.get("delta"));
        Object content = delta != null ? delta.get("content") : "";
        List<ToolCall> toolCalls =
            delta == null ? null : AssistantMessage.convertOpenAiToolCalls(asListOfMaps(delta.get("tool_calls")));
        String reasoningContent = delta == null ? null : asString(delta.get("reasoning_content"));
        String finishReason = asString(choice.get("finish_reason"));
        String normalizedFinishReason = finishReason == null || finishReason.isBlank() ? "null" : finishReason;
        Object parserContent = parseStreamingContent(content, outputParser, parserBuffer);

        if (isEmptyContent(content) && (toolCalls == null || toolCalls.isEmpty()) && reasoningContent == null
                && usageMetadata == null && "null".equals(normalizedFinishReason)) {
            return null;
        }

        return AssistantMessageChunk.builder().content(content == null ? "" : content).toolCalls(toolCalls)
                .usageMetadata(usageMetadata).finishReason(normalizedFinishReason).parserContent(parserContent)
                .reasoningContent(reasoningContent).build();
    }

    /**
     * parseWithOutputParser.
     * 
     * @param content content
     * @param outputParser outputParser
     * @return the result
     * @since 0.1.7
     */
    private Object parseWithOutputParser(Object content, BaseOutputParser outputParser) {
        if (outputParser == null || isEmptyContent(content)) {
            return null;
        }
        try {
            return outputParser.parse(content instanceof String ? content : stringifyContent(content));
        } catch (Exception e) {
            LOG.warn("Failed to parse assistant response with output parser", e);
            return null;
        }
    }

    /**
     * parseStreamingContent.
     * 
     * @param content content
     * @param outputParser outputParser
     * @param parserBuffer parserBuffer
     * @return the result
     * @since 0.1.7
     */
    private Object parseStreamingContent(Object content, BaseOutputParser outputParser, StringBuilder parserBuffer) {
        if (outputParser == null || isEmptyContent(content)) {
            return null;
        }
        parserBuffer.append(stringifyContent(content));
        try {
            Object parsed = outputParser.parse(parserBuffer.toString());
            if (parsed != null) {
                parserBuffer.setLength(0);
            }
            return parsed;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * resolveModelName.
     * 
     * @param explicitModel explicitModel
     * @param responseMap responseMap
     * @return the result
     * @since 0.1.7
     */
    private String resolveModelName(String explicitModel, Map<String, Object> responseMap) {
        if (explicitModel != null && !explicitModel.isBlank()) {
            return explicitModel;
        }
        if (modelConfig != null && modelConfig.getModelName() != null && !modelConfig.getModelName().isBlank()) {
            return modelConfig.getModelName();
        }
        if (responseMap != null) {
            return asString(responseMap.get("model"));
        }
        return "";
    }

    /**
     * buildUsageMetadata.
     * 
     * @param usageObject usageObject
     * @param modelName modelName
     * @return the result
     * @since 0.1.7
     */
    private UsageMetadata buildUsageMetadata(Object usageObject, String modelName) {
        Map<String, Object> usage = asMap(usageObject);
        if (usage == null && (modelName == null || modelName.isBlank())) {
            return null;
        }

        int cacheTokens = 0;
        Map<String, Object> promptTokenDetails = usage == null ? null : asMap(usage.get("prompt_tokens_details"));
        if (promptTokenDetails != null) {
            cacheTokens = toInt(promptTokenDetails.get("cached_tokens"));
        }

        return UsageMetadata.builder().modelName(modelName == null ? "" : modelName)
                .inputTokens(usage == null ? 0 : toInt(usage.get("prompt_tokens")))
                .outputTokens(usage == null ? 0 : toInt(usage.get("completion_tokens")))
                .totalTokens(usage == null ? 0 : toInt(usage.get("total_tokens"))).cacheTokens(cacheTokens).build();
    }

    /**
     * resolveFinishReason.
     * 
     * @param finishReason finishReason
     * @param toolCalls toolCalls
     * @return the result
     * @since 0.1.7
     */
    private String resolveFinishReason(Object finishReason, List<ToolCall> toolCalls) {
        String value = asString(finishReason);
        if (value != null && !value.isBlank()) {
            return value;
        }
        return toolCalls != null && !toolCalls.isEmpty() ? "tool_calls" : "stop";
    }

    /**
     * isEmptyContent.
     * 
     * @param content content
     * @return the result
     * @since 0.1.7
     */
    private static boolean isEmptyContent(Object content) {
        if (content == null) {
            return true;
        }
        if (content instanceof String s) {
            return s.isBlank();
        }
        if (content instanceof List<?> list) {
            return list.isEmpty();
        }
        return false;
    }

    /**
     * stringifyContent.
     * 
     * @param content content
     * @return the result
     * @since 0.1.7
     */
    private static String stringifyContent(Object content) {
        if (content == null) {
            return "";
        }
        if (content instanceof String s) {
            return s;
        }
        try {
            return MAPPER.writeValueAsString(content);
        } catch (JsonProcessingException ignored) {
            return String.valueOf(content);
        }
    }

    @SuppressWarnings("unchecked")
    /**
     * asMap.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    /**
     * asListOfMaps.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static List<Map<String, Object>> asListOfMaps(Object value) {
        if (value instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return null;
    }

    /**
     * asString.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static String asString(Object value) {
        return value instanceof String s ? s : null;
    }

    /**
     * toInt.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static int toInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private final class StreamingChunkIterator implements Iterator<AssistantMessageChunk>, AutoCloseable {
        private final BufferedReader reader;
        private final String resolvedModel;
        private final BaseOutputParser outputParser;

        /**
         * StringBuilder.
         * 
         * @since 0.1.7
         */
        private final StringBuilder parserBuffer = new StringBuilder();

        private AssistantMessageChunk nextChunk;
        private boolean finished;
        private volatile boolean isClosed;

        /**
         * StreamingChunkIterator.
         * 
         * @param inputStream inputStream
         * @param resolvedModel resolvedModel
         * @param outputParser outputParser
         * @since 0.1.7
         */
        private StreamingChunkIterator(InputStream inputStream, String resolvedModel, BaseOutputParser outputParser) {
            this.reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            this.resolvedModel = resolvedModel;
            this.outputParser = outputParser;
        }

        /**
         * hasNext.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public boolean hasNext() {
            if (nextChunk != null) {
                return true;
            }
            if (finished) {
                return false;
            }
            nextChunk = readNextChunk();
            if (nextChunk == null) {
                finished = true;
                closeQuietly();
                return false;
            }
            return true;
        }

        /**
         * next.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public AssistantMessageChunk next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            AssistantMessageChunk current = nextChunk;
            nextChunk = null;
            return current;
        }

        /**
         * readNextChunk.
         * 
         * @return the result
         * @since 0.1.7
         */
        private AssistantMessageChunk readNextChunk() {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || !trimmed.startsWith("data:")) {
                        continue;
                    }
                    String data = trimmed.substring("data:".length()).trim();
                    if ("[DONE]".equals(data)) {
                        return null;
                    }

                    @SuppressWarnings("unchecked")
                    Map<String, Object> event = MAPPER.readValue(data, Map.class);
                    AssistantMessageChunk chunk = parseStreamChunk(event, resolvedModel, outputParser, parserBuffer);
                    if (chunk != null) {
                        return chunk;
                    }
                }
                return null;
            } catch (java.net.SocketTimeoutException e) {
                // OkHttp readTimeout interrupts blocked readLine(); surface a clear SSE stall message.
                throw ErrorHelper.buildError(StatusCode.MODEL_CALL_FAILED, null, null, e,
                        Map.of("error_msg", "SSE stream read timeout (no data within OkHttp readTimeout)"));
            } catch (IOException e) {
                throw ErrorHelper.buildError(StatusCode.MODEL_CALL_FAILED, null, null, e,
                        Map.of("error_msg", "Failed to read streaming response"));
            }
        }

        // 关闭底层 reader，解除阻塞在 readLine() 的线程（readLine 不响应 interrupt）。
        /**
         * close.
         * 
         * @since 0.1.7
         */
        @Override
        public void close() {
            if (isClosed) {
                return;
            }
            isClosed = true;
            finished = true;
            closeQuietly();
        }

        /**
         * closeQuietly.
         * 
         * @since 0.1.7
         */
        private void closeQuietly() {
            try {
                reader.close();
            } catch (IOException ignored) {

                // Ignore.
            }
        }
    }
}
