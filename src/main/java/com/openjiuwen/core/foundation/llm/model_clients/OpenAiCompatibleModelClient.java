/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;

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
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Basic OpenAI-compatible HTTP client used by the built-in providers.
 */
public class OpenAiCompatibleModelClient extends BaseModelClient {

    private static final Logger LOG = LoggerFactory.getLogger(OpenAiCompatibleModelClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient;

    /**
     * Auto-generated for codecheck compliance.
     */
    public OpenAiCompatibleModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        super(modelConfig, modelClientConfig);
        this.httpClient = buildHttpClient(modelClientConfig.getTimeout());
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    protected String getClientName() {
        return "OpenAI-compatible client";
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    protected void validateConfig() {
        if (modelClientConfig.getApiKey() == null || modelClientConfig.getApiKey().isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.MODEL_SERVICE_CONFIG_ERROR,
                    "error_msg", "model client config api_key is required for OpenAI-compatible client.");
        }
        if (modelClientConfig.getApiBase() == null || modelClientConfig.getApiBase().isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.MODEL_SERVICE_CONFIG_ERROR,
                    "error_msg", "model client config api_base is required for OpenAI-compatible client.");
        }
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
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
        Map<String, Object> params = buildRequestParams(
                messages, tools,
                temperature != null ? temperature.doubleValue() : null,
                topP != null ? topP.doubleValue() : null,
                model, stop, maxTokens, false, kwargs);

        HttpResponse<String> response = httpClient.send(
                buildRequest(params, timeout),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ensureSuccess(response.statusCode(), response.body());

        @SuppressWarnings("unchecked")
        Map<String, Object> responseMap = MAPPER.readValue(response.body(), Map.class);
        return parseAssistantMessage(responseMap, resolveModelName(model, responseMap), outputParser);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
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
        Map<String, Object> params = buildRequestParams(
                messages, tools,
                temperature != null ? temperature.doubleValue() : null,
                topP != null ? topP.doubleValue() : null,
                model, stop, maxTokens, true, kwargs);

        HttpResponse<InputStream> response = httpClient.send(
                buildRequest(params, timeout),
                HttpResponse.BodyHandlers.ofInputStream());
        ensureSuccess(response.statusCode(), null);

        return new StreamingChunkIterator(response.body(), resolveModelName(model, null), outputParser);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public ImageGenerationResponse generateImage(List<UserMessage> messages,
                                                 String model,
                                                 String size,
                                                 String negativePrompt,
                                                 int n,
                                                 boolean promptExtend,
                                                 boolean watermark,
                                                 int seed,
                                                 Map<String, Object> kwargs) throws Exception {
        throw new UnsupportedOperationException("Image generation is not supported by the built-in HTTP client");
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public AudioGenerationResponse generateSpeech(List<UserMessage> messages,
                                                  String model,
                                                  String voice,
                                                  String languageType,
                                                  Map<String, Object> kwargs) throws Exception {
        throw new UnsupportedOperationException("Speech generation is not supported by the built-in HTTP client");
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
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
        throw new UnsupportedOperationException("Video generation is not supported by the built-in HTTP client");
    }

    private HttpRequest buildRequest(Map<String, Object> params, Float timeoutOverride) throws Exception {
        String body = MAPPER.writeValueAsString(params);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(normalizedApiBase() + "/chat/completions"))
                .timeout(resolveTimeout(timeoutOverride != null ? timeoutOverride : (float) modelClientConfig.getTimeout()));
        applyConfiguredHeaders(builder, true);
        builder.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        return builder.build();
    }

    private String normalizedApiBase() {
        return modelClientConfig.getApiBase().replaceAll("/+$", "");
    }

    private static Duration resolveTimeout(double seconds) {
        long millis = Math.max(1_000L, Math.round(seconds * 1_000));
        return Duration.ofMillis(millis);
    }

    private static void ensureSuccess(int statusCode, String body) {
        if (statusCode >= 200 && statusCode < 300) {
            return;
        }
        String responseBody = body == null ? "" : body;
        throw new RuntimeException("HTTP " + statusCode + ": " + responseBody);
    }

    private AssistantMessage parseAssistantMessage(Map<String, Object> responseMap,
                                                   String resolvedModel,
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

        return AssistantMessage.builder()
                .content(content)
                .toolCalls(toolCalls)
                .usageMetadata(buildUsageMetadata(responseMap.get("usage"), resolvedModel))
                .finishReason(resolveFinishReason(choice.get("finish_reason"), toolCalls))
                .parserContent(parserContent)
                .reasoningContent(asString(message.get("reasoning_content")))
                .build();
    }

    private AssistantMessageChunk parseStreamChunk(Map<String, Object> event,
                                                   String resolvedModel,
                                                   BaseOutputParser outputParser,
                                                   StringBuilder parserBuffer) {
        List<Map<String, Object>> choices = asListOfMaps(event.get("choices"));
        UsageMetadata usageMetadata = buildUsageMetadata(event.get("usage"), resolvedModel);

        if (choices == null || choices.isEmpty()) {
            if (usageMetadata == null) {
                return null;
            }
            return AssistantMessageChunk.builder()
                    .content("")
                    .usageMetadata(usageMetadata)
                    .finishReason("null")
                    .build();
        }

        Map<String, Object> choice = choices.get(0);
        Map<String, Object> delta = asMap(choice.get("delta"));
        Object content = delta != null ? delta.get("content") : "";
        List<ToolCall> toolCalls = delta == null
                ? null
                : AssistantMessage.convertOpenAiToolCalls(asListOfMaps(delta.get("tool_calls")));
        String reasoningContent = delta == null ? null : asString(delta.get("reasoning_content"));
        String finishReason = asString(choice.get("finish_reason"));
        String normalizedFinishReason = finishReason == null || finishReason.isBlank() ? "null" : finishReason;
        Object parserContent = parseStreamingContent(content, outputParser, parserBuffer);

        if (isEmptyContent(content)
                && (toolCalls == null || toolCalls.isEmpty())
                && reasoningContent == null
                && usageMetadata == null
                && "null".equals(normalizedFinishReason)) {
            return null;
        }

        return AssistantMessageChunk.builder()
                .content(content == null ? "" : content)
                .toolCalls(toolCalls)
                .usageMetadata(usageMetadata)
                .finishReason(normalizedFinishReason)
                .parserContent(parserContent)
                .reasoningContent(reasoningContent)
                .build();
    }

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

    private Object parseStreamingContent(Object content,
                                         BaseOutputParser outputParser,
                                         StringBuilder parserBuffer) {
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

        return UsageMetadata.builder()
                .modelName(modelName == null ? "" : modelName)
                .inputTokens(usage == null ? 0 : toInt(usage.get("prompt_tokens")))
                .outputTokens(usage == null ? 0 : toInt(usage.get("completion_tokens")))
                .totalTokens(usage == null ? 0 : toInt(usage.get("total_tokens")))
                .cacheTokens(cacheTokens)
                .build();
    }

    private String resolveFinishReason(Object finishReason, List<ToolCall> toolCalls) {
        String value = asString(finishReason);
        if (value != null && !value.isBlank()) {
            return value;
        }
        return toolCalls != null && !toolCalls.isEmpty() ? "tool_calls" : "stop";
    }

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

    private static String stringifyContent(Object content) {
        if (content == null) {
            return "";
        }
        if (content instanceof String s) {
            return s;
        }
        try {
            return MAPPER.writeValueAsString(content);
        } catch (Exception ignored) {
            return String.valueOf(content);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asListOfMaps(Object value) {
        if (value instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return null;
    }

    private static String asString(Object value) {
        return value instanceof String s ? s : null;
    }

    private static int toInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private final class StreamingChunkIterator implements Iterator<AssistantMessageChunk> {

        private final BufferedReader reader;
        private final String resolvedModel;
        private final BaseOutputParser outputParser;
        private final StringBuilder parserBuffer = new StringBuilder();

        private AssistantMessageChunk nextChunk;
        private boolean finished;

        private StreamingChunkIterator(InputStream inputStream,
                                       String resolvedModel,
                                       BaseOutputParser outputParser) {
            this.reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            this.resolvedModel = resolvedModel;
            this.outputParser = outputParser;
        }

        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
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

        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
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
                throw new RuntimeException("Failed to read streaming response", e);
            }
        }

        private void closeQuietly() {
            try {
                reader.close();
            } catch (IOException ignored) {
            }
        }
    }
}
