/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Inference Affinity (vLLM-style) client with cache sharing and release support.
 * 
 * @since 0.1.7
 */
public class InferenceAffinityModelClient extends BaseModelClient {
    private static final Logger LOG = LoggerFactory.getLogger(InferenceAffinityModelClient.class);

    /**
     * ObjectMapper.
     * 
     * @since 0.1.7
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient;

    /**
     * InferenceAffinityModelClient.
     * 
     * @param modelConfig modelConfig
     * @param modelClientConfig modelClientConfig
     * @since 0.1.7
     */
    public InferenceAffinityModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        super(modelConfig, modelClientConfig);
        this.httpClient = buildHttpClient(modelClientConfig.getTimeout());
    }

    /**
     * getClientName.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    protected String getClientName() {
        return "InferenceAffinity client";
    }

    /**
     * validateConfig.
     * 
     * @since 0.1.7
     */
    @Override
    protected void validateConfig() {
        if (modelClientConfig.getApiBase() == null || modelClientConfig.getApiBase().isBlank()) {
            throw ErrorHelper.buildError(StatusCode.MODEL_SERVICE_CONFIG_ERROR, "error_msg",
                    "model client config api_base is required for InferenceAffinity client.");
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
            buildAndSanitizeParams(messages, tools, temperature, topP, model, maxTokens, stop, false, kwargs);

        HttpResponse<String> response = httpClient.send(buildJsonRequest("/v1/chat/completions", params, timeout),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ensureSuccess(response.statusCode(), response.body());

        @SuppressWarnings("unchecked")
        Map<String, Object> responseMap = MAPPER.readValue(response.body(), Map.class);
        return parseAssistantMessage(responseMap, resolveModelName(model, responseMap), outputParser);
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
            buildAndSanitizeParams(messages, tools, temperature, topP, model, maxTokens, stop, true, kwargs);

        HttpResponse<InputStream> response = httpClient.send(buildJsonRequest("/v1/chat/completions", params, timeout),
                HttpResponse.BodyHandlers.ofInputStream());
        ensureSuccess(response.statusCode(), null);
        return new StreamingChunkIterator(response.body(), resolveModelName(model, null), outputParser);
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
     * @since 0.1.7
     */
    @Override
    public ImageGenerationResponse generateImage(List<UserMessage> messages, String model, String size,
            String negativePrompt, int n, boolean promptExtend, boolean watermark, int seed,
            Map<String, Object> kwargs) {
        throw new UnsupportedOperationException("Image generation is not supported by InferenceAffinityModelClient");
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
     * @since 0.1.7
     */
    @Override
    public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model, String voice,
            String languageType, Map<String, Object> kwargs) {
        throw new UnsupportedOperationException("Speech generation is not supported by InferenceAffinityModelClient");
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
     * @since 0.1.7
     */
    @Override
    public VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl, String audioUrl,
            String model, String size, String resolution, int duration, boolean promptExtend, boolean watermark,
            String negativePrompt, Integer seed, Map<String, Object> kwargs) {
        throw new UnsupportedOperationException("Video generation is not supported by InferenceAffinityModelClient");
    }

    /**
     * release.
     * 
     * @param sessionId sessionId
     * @param messages messages
     * @param messagesReleasedIndex messagesReleasedIndex
     * @param tools tools
     * @param toolsReleasedIndex toolsReleasedIndex
     * @param model model
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public boolean release(String sessionId, Object messages, int messagesReleasedIndex, Object tools,
            Integer toolsReleasedIndex, String model) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", resolveModelName(model, null));
        body.put("cache_salt", sessionId);
        body.put("cache_sharing", true);
        body.put("messages", convertMessagesToDict(messages));
        body.put("messages_released_index", messagesReleasedIndex);
        if (tools != null) {
            body.put("tools", convertToolsToDict(tools));
        }
        if (toolsReleasedIndex != null) {
            body.put("tools_released_index", toolsReleasedIndex);
        }

        HttpResponse<String> response = httpClient.send(buildJsonRequest("/release_kv_cache", body, null),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return response.statusCode() >= 200 && response.statusCode() < 300;
    }

    /**
     * buildAndSanitizeParams.
     * 
     * @param messages messages
     * @param tools tools
     * @param temperature temperature
     * @param topP topP
     * @param model model
     * @param maxTokens maxTokens
     * @param stop stop
     * @param stream stream
     * @param kwargs kwargs
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> buildAndSanitizeParams(Object messages, Object tools, Float temperature, Float topP,
            String model, Integer maxTokens, String stop, boolean stream, Map<String, Object> kwargs) {
        Map<String, Object> remainingKwargs = kwargs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(kwargs);
        Object sessionId = remainingKwargs.remove("session_id");
        Object enableCacheSharing = remainingKwargs.remove("enable_cache_sharing");

        Map<String, Object> params =
            buildRequestParams(messages, tools, temperature != null ? temperature.doubleValue() : null,
                    topP != null ? topP.doubleValue() : null, model, stop, maxTokens, stream, remainingKwargs);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messageList = (List<Map<String, Object>>) params.get("messages");
        params.put("messages", sanitizeToolCalls(messageList));

        if (Boolean.TRUE.equals(enableCacheSharing) && sessionId != null) {
            params.put("cache_sharing", true);
            params.put("cache_salt", String.valueOf(sessionId));
        }
        return params;
    }

    /**
     * buildJsonRequest.
     * 
     * @param suffix suffix
     * @param body body
     * @param timeoutOverride timeoutOverride
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    private HttpRequest buildJsonRequest(String suffix, Map<String, Object> body, Float timeoutOverride)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(normalizedApiBase() + suffix)).timeout(
                resolveTimeout(timeoutOverride != null ? timeoutOverride : (float) modelClientConfig.getTimeout()));
        applyConfiguredHeaders(builder, true);
        return builder
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
    }

    /**
     * normalizedApiBase.
     * 
     * @return the result
     * @since 0.1.7
     */
    private String normalizedApiBase() {
        return modelClientConfig.getApiBase().replaceAll("/+$", "");
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
        List<ToolCall> toolCalls = AssistantMessage.convertOpenAiToolCalls(asListOfMaps(message.get("tool_calls")));

        return AssistantMessage.builder().content(content).toolCalls(toolCalls)
                .usageMetadata(buildUsageMetadata(responseMap.get("usage"), resolvedModel))
                .finishReason(resolveFinishReason(choice.get("finish_reason"), toolCalls))
                .parserContent(parseWithOutputParser(content, outputParser))
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

        if (isEmptyContent(content) && (toolCalls == null || toolCalls.isEmpty()) && reasoningContent == null
                && usageMetadata == null && "null".equals(normalizedFinishReason)) {
            return null;
        }

        return AssistantMessageChunk.builder().content(content == null ? "" : content).toolCalls(toolCalls)
                .usageMetadata(usageMetadata).finishReason(normalizedFinishReason)
                .parserContent(parseStreamingContent(content, outputParser, parserBuffer))
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
            LOG.warn("Failed to parse InferenceAffinity response with output parser", e);
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
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * sanitizeToolCalls.
     * 
     * @param messages messages
     * @return the result
     * @since 0.1.7
     */
    private List<Map<String, Object>> sanitizeToolCalls(List<Map<String, Object>> messages) {
        if (messages == null) {
            return List.of();
        }
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (Map<String, Object> original : messages) {
            Map<String, Object> message = new LinkedHashMap<>(original);
            if (!"assistant".equals(message.get("role"))) {
                result.add(message);
                continue;
            }
            Object rawToolCalls = message.get("tool_calls");
            if (!(rawToolCalls instanceof List<?> list)) {
                result.add(message);
                continue;
            }
            List<Map<String, Object>> cleaned = new java.util.ArrayList<>();
            for (Object item : list) {
                Map<String, Object> toolCall = asMap(item);
                if (toolCall == null) {
                    continue;
                }
                Map<String, Object> function = asMap(toolCall.get("function"));
                Map<String, Object> cleanedToolCall = new LinkedHashMap<>();
                cleanedToolCall.put("id", toolCall.getOrDefault("id", ""));
                cleanedToolCall.put("type", "function");
                cleanedToolCall.put("index", toolCall.get("index"));
                cleanedToolCall.put("function",
                        Map.of("name", function != null ? String.valueOf(function.getOrDefault("name", "")) : "",
                                "arguments",
                                function != null ? String.valueOf(function.getOrDefault("arguments", "")) : ""));
                cleaned.add(cleanedToolCall);
            }
            message.put("tool_calls", cleaned);
            result.add(message);
        }
        return result;
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
        return responseMap != null ? String.valueOf(responseMap.getOrDefault("model", "")) : "";
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
        } catch (JsonProcessingException e) {
            return String.valueOf(content);
        }
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

    @SuppressWarnings("unchecked")
    /**
     * asMap.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
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
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : null;
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
        throw new RuntimeException("HTTP " + statusCode + ": " + (body == null ? "" : body));
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
            } catch (IOException e) {
                throw new RuntimeException("Failed to read InferenceAffinity stream response", e);
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
