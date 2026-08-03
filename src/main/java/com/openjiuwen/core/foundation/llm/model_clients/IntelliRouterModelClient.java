/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.openjiuwen.core.common.exception.BaseError;
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
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;

/**
 * IntelliRouter model client backed by an injectable reliable router.
 *
 * <p>Mirrors Python's {@code IntelliRouterModelClient} in
 * {@code openjiuwen/core/foundation/llm/model_clients/intelli_router_model_client.py}.</p>
 */
public class IntelliRouterModelClient extends BaseModelClient {

    public static final String __client_name__ = "intelli_router";

    private static final Logger LOG = LoggerFactory.getLogger(IntelliRouterModelClient.class);
    private static final ObjectMapper SORTED_MAPPER = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    private static final Object ROUTER_CACHE_LOCK = new Object();
    private static final Map<String, ReliableRouter> ROUTER_CACHE = new LinkedHashMap<>();

    private static volatile RouterFactory routerFactory;

    private final ReliableRouter router;

    public IntelliRouterModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        this(modelConfig, modelClientConfig, null);
    }

    public IntelliRouterModelClient(
            ModelRequestConfig modelConfig,
            ModelClientConfig modelClientConfig,
            ReliableRouter router) {
        super(modelConfig, modelClientConfig);
        if (router != null) {
            this.router = router;
        } else {
            IntelliRouterClientConfig routerConfig = IntelliRouterClientConfig.fromModelClientConfig(modelClientConfig);
            this.router = getOrCreateRouter(routerConfig);
        }
    }

    @Override
    protected String getClientName() {
        return __client_name__;
    }

    @Override
    protected void validateConfig() {
        // Python IntelliRouter client intentionally does not require api_key or api_base.
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
        try {
            List<Map<String, Object>> convertedMessages = convertMessagesToDict(messages);
            String modelName = resolveModelName(model);
            Map<String, Object> requestParams = buildIntelliRouterRequestParams(
                    convertedMessages,
                    tools,
                    temperature,
                    topP,
                    maxTokens,
                    stop,
                    modelName,
                    false,
                    timeout,
                    kwargs);
            Map<String, Object> response = router.completion(modelName, convertedMessages, requestParams);
            return convertResponse(response, outputParser);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw intelliRouterCallError("invoke", exception);
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
        try {
            List<Map<String, Object>> convertedMessages = convertMessagesToDict(messages);
            String modelName = resolveModelName(model);
            Map<String, Object> requestParams = buildIntelliRouterRequestParams(
                    convertedMessages,
                    tools,
                    temperature,
                    topP,
                    maxTokens,
                    stop,
                    modelName,
                    true,
                    timeout,
                    kwargs);
            return new ChunkIterator(router.streamCompletion(modelName, convertedMessages, requestParams));
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw intelliRouterCallError("stream", exception);
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
        throw ErrorHelper.buildError(
                StatusCode.MODEL_CALL_FAILED,
                "error_msg",
                "IntelliRouter does not support image generation"
        );
    }

    @Override
    public AudioGenerationResponse generateSpeech(List<UserMessage> messages,
                                                  String model,
                                                  String voice,
                                                  String languageType,
                                                  Map<String, Object> kwargs) {
        throw ErrorHelper.buildError(
                StatusCode.MODEL_CALL_FAILED,
                "error_msg",
                "IntelliRouter does not support speech generation"
        );
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
        throw ErrorHelper.buildError(
                StatusCode.MODEL_CALL_FAILED,
                "error_msg",
                "IntelliRouter does not support video generation"
        );
    }

    private static BaseError intelliRouterCallError(String operation, Exception exception) {
        String message = "IntelliRouter " + operation + " failed: " + exception.getMessage();
        return ErrorHelper.buildError(
                StatusCode.MODEL_CALL_FAILED,
                null,
                null,
                exception,
                Map.of("error_msg", message));
    }

    static String makeRouterKey(IntelliRouterClientConfig config) {
        String deploymentsJson = sortedJson(config.getDeployments());
        String kwargsJson = sortedJson(config.getStrategyKwargs());
        String raw = deploymentsJson
                + "|" + config.getStrategy()
                + "|" + kwargsJson
                + "|" + config.getNumRetries()
                + "|" + config.getTimeout()
                + "|" + config.isEnableHealthCheck()
                + "|" + config.getHealthCheckInterval()
                + "|" + config.isVerifySsl();
        return md5(raw);
    }

    static void setRouterFactoryForTesting(RouterFactory factory) {
        routerFactory = factory;
    }

    static void clearRouterCacheForTesting() {
        synchronized (ROUTER_CACHE_LOCK) {
            ROUTER_CACHE.clear();
        }
    }

    static ReliableRouter getOrCreateRouterForTesting(IntelliRouterClientConfig config) {
        return getOrCreateRouter(config);
    }

    static ReliableRouter createRouterForTesting(IntelliRouterClientConfig config) {
        return createRouter(config);
    }

    ReliableRouter routerForTesting() {
        return router;
    }

    Map<String, Object> buildRequestParamsForTesting(
            List<Map<String, Object>> messages,
            Object tools,
            Float temperature,
            Float topP,
            Integer maxTokens,
            String stop,
            boolean stream,
            Float timeout,
            Map<String, Object> kwargs) {
        return buildIntelliRouterRequestParams(
                messages,
                tools,
                temperature,
                topP,
                maxTokens,
                stop,
                resolveModelName(null),
                stream,
                timeout,
                kwargs);
    }

    AssistantMessage convertResponseForTesting(Map<String, Object> response) throws Exception {
        return convertResponse(response, null);
    }

    AssistantMessageChunk convertChunkForTesting(Map<String, Object> chunk) {
        return convertChunk(chunk);
    }

    private static ReliableRouter getOrCreateRouter(IntelliRouterClientConfig config) {
        String key = makeRouterKey(config);
        synchronized (ROUTER_CACHE_LOCK) {
            ReliableRouter cachedRouter = ROUTER_CACHE.get(key);
            if (cachedRouter == null) {
                cachedRouter = createRouter(config);
                ROUTER_CACHE.put(key, cachedRouter);
            }
            return cachedRouter;
        }
    }

    private static ReliableRouter createRouter(IntelliRouterClientConfig config) {
        RouterFactory factory = routerFactory;
        if (factory == null) {
            throw ErrorHelper.buildError(
                    StatusCode.MODEL_SERVICE_CONFIG_ERROR,
                    "error_msg",
                    "intelli_router package is not installed. Please install it with: pip install intelli-router"
            );
        }
        return factory.create(config);
    }

    private Map<String, Object> buildIntelliRouterRequestParams(
            List<Map<String, Object>> messages,
            Object tools,
            Float temperature,
            Float topP,
            Integer maxTokens,
            String stop,
            String model,
            boolean stream,
            Float timeout,
            Map<String, Object> kwargs) {
        Map<String, Object> params = new LinkedHashMap<>();

        Double finalTemperature = temperature != null
                ? temperature.doubleValue()
                : modelConfig == null ? null : modelConfig.getTemperature();
        Double finalTopP = topP != null
                ? topP.doubleValue()
                : modelConfig == null ? null : modelConfig.getTopP();
        Integer finalMaxTokens = maxTokens != null
                ? maxTokens
                : modelConfig == null ? null : modelConfig.getMaxTokens();

        if (finalTemperature != null) {
            params.put("temperature", finalTemperature);
        }
        if (finalTopP != null) {
            params.put("top_p", finalTopP);
        }
        if (finalMaxTokens != null) {
            params.put("max_tokens", finalMaxTokens);
        }
        if (stop != null) {
            params.put("stop", stop);
        }
        if (timeout != null) {
            params.put("timeout", timeout.doubleValue());
        }

        List<Map<String, Object>> toolsDict = convertToolsToDict(tools);
        if (toolsDict != null && !toolsDict.isEmpty()) {
            params.put("tools", toolsDict);
        }

        if (kwargs != null) {
            params.putAll(kwargs);
        }

        logRequestParams(model, messages, toolsDict, finalTemperature, finalTopP, finalMaxTokens, stop, stream);
        return params;
    }

    private AssistantMessage convertResponse(
            Map<String, Object> response,
            BaseOutputParser outputParser) throws Exception {
        List<Map<String, Object>> choices = asListOfMaps(response == null ? null : response.get("choices"));
        Map<String, Object> message;
        Object content;
        if (choices == null || choices.isEmpty()) {
            message = Map.of();
            content = "";
        } else {
            message = asMap(choices.get(0).get("message"));
            if (message == null) {
                message = Map.of();
            }
            content = orEmpty(message.get("content"));
        }

        String reasoningContent = asString(message.get("reasoning_content"));
        List<ToolCall> toolCalls = convertToolCalls(asListOfMaps(message.get("tool_calls")));
        Object contentValue = content;
        String contentText = stringifyContent(contentValue);
        if (outputParser != null && !contentText.isEmpty()) {
            try {
                CompletableFuture<Object> parsedFuture = outputParser.parse(contentText);
                Object parsed = parsedFuture == null ? null : parsedFuture.get();
                contentValue = parsed instanceof String parsedText ? parsedText : String.valueOf(parsed);
            } catch (Exception ex) {
                LOG.warn("Output parser failed to parse content, using raw content as fallback.", ex);
            }
        }

        return AssistantMessage.builder()
                .content(contentValue)
                .toolCalls(toolCalls.isEmpty() ? null : toolCalls)
                .finishReason(toolCalls.isEmpty() ? "stop" : "tool_calls")
                .reasoningContent(reasoningContent)
                .build();
    }

    private AssistantMessageChunk convertChunk(Map<String, Object> chunk) {
        List<Map<String, Object>> choices = asListOfMaps(chunk == null ? null : chunk.get("choices"));
        Object content = "";
        if (choices != null && !choices.isEmpty()) {
            Map<String, Object> delta = asMap(choices.get(0).get("delta"));
            content = delta == null ? "" : orEmpty(delta.get("content"));
        }
        return AssistantMessageChunk.builder().content(content).build();
    }

    private List<ToolCall> convertToolCalls(List<Map<String, Object>> rawToolCalls) {
        if (rawToolCalls == null || rawToolCalls.isEmpty()) {
            return List.of();
        }
        List<ToolCall> result = new ArrayList<>();
        for (int index = 0; index < rawToolCalls.size(); index++) {
            Map<String, Object> rawToolCall = rawToolCalls.get(index);
            Map<String, Object> function = asMap(rawToolCall.get("function"));
            if (function == null) {
                function = Map.of();
            }
            Object rawIndex = rawToolCall.get("index");
            result.add(ToolCall.builder()
                    .id(stringifyContent(orEmpty(rawToolCall.get("id"))))
                    .type("function")
                    .name(stringifyContent(orEmpty(function.get("name"))))
                    .arguments(stringifyContent(orEmpty(function.get("arguments"))))
                    .index(rawIndex instanceof Number number ? number.intValue() : index)
                    .build());
        }
        return result;
    }

    private String resolveModelName(String explicitModel) {
        if (explicitModel != null && !explicitModel.isEmpty()) {
            return explicitModel;
        }
        return modelConfig == null ? null : modelConfig.getModelName();
    }

    private void logRequestParams(
            String model,
            List<Map<String, Object>> messages,
            List<Map<String, Object>> tools,
            Double temperature,
            Double topP,
            Integer maxTokens,
            String stop,
            boolean stream) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("client_name", getClientName());
        metadata.put("model_name", model);
        metadata.put("model_provider", modelClientConfig == null ? null : modelClientConfig.getClientProvider());
        metadata.put("messages", messages);
        metadata.put("tools", tools);
        metadata.put("temperature", temperature);
        metadata.put("top_p", topP);
        metadata.put("max_tokens", maxTokens);
        metadata.put("is_stream", stream);
        metadata.put("stop", stop);
        LOG.info("Before request chat model, LLM request params ready. {}", metadata);
    }

    private static String sortedJson(Object value) {
        try {
            return SORTED_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to serialize IntelliRouter config", ex);
        }
    }

    private static String md5(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("MD5 digest is not available", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asListOfMaps(Object value) {
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : null;
    }

    private static String asString(Object value) {
        return value instanceof String text ? text : null;
    }

    private static Object orEmpty(Object value) {
        return value == null ? "" : value;
    }

    private static String stringifyContent(Object value) {
        return value instanceof String text ? text : String.valueOf(value);
    }

    /**
     * Third-party ReliableRouter boundary used by the Java translation.
     *
     * <p>Mirrors Python's imported {@code ReliableRouter} usage in
     * {@code openjiuwen/core/foundation/llm/model_clients/intelli_router_model_client.py}.</p>
     */
    public interface ReliableRouter {
        Map<String, Object> completion(
                String model,
                List<Map<String, Object>> messages,
                Map<String, Object> requestParams) throws Exception;

        Iterator<Map<String, Object>> streamCompletion(
                String model,
                List<Map<String, Object>> messages,
                Map<String, Object> requestParams) throws Exception;
    }

    /**
     * Factory hook for creating router instances from cached IntelliRouter config.
     *
     * <p>Mirrors Python's {@code IntelliRouterModelClient._create_router} in
     * {@code openjiuwen/core/foundation/llm/model_clients/intelli_router_model_client.py}.</p>
     */
    @FunctionalInterface
    public interface RouterFactory {
        ReliableRouter create(IntelliRouterClientConfig config) throws BaseError;
    }

    /**
     * Lazy chunk converter for streaming router responses.
     *
     * <p>Mirrors Python's {@code IntelliRouterModelClient._convert_chunk} iteration in
     * {@code openjiuwen/core/foundation/llm/model_clients/intelli_router_model_client.py}.</p>
     */
    private final class ChunkIterator implements Iterator<AssistantMessageChunk> {
        private final Iterator<Map<String, Object>> delegate;

        private ChunkIterator(Iterator<Map<String, Object>> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean hasNext() {
            return delegate != null && delegate.hasNext();
        }

        @Override
        public AssistantMessageChunk next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return convertChunk(delegate.next());
        }
    }
}
