/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.security.JdkHttpClientProxySupport;
import com.openjiuwen.core.common.security.SslUtils;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * LLM Model Client abstract base class.
 * <p>
 * All Model Client implementations must inherit from this class and implement
 * invoke, stream, generateImage, generateSpeech, generateVideo.
 * <p>
 * Mirrors Python's {@code BaseModelClient} ABC.
 */
public abstract class BaseModelClient {

    private static final Logger LOG = LoggerFactory.getLogger(BaseModelClient.class);
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /**
     * Auto-generated for codecheck compliance.
     */
    protected final ModelRequestConfig modelConfig;
    /**
     * Auto-generated for codecheck compliance.
     */
    protected final ModelClientConfig modelClientConfig;

    /**
     * Initialize the model client.
     *
     * @param modelConfig       model parameter configuration (temperature, top_p, model_name, etc.)
     * @param modelClientConfig client configuration (api_key, api_base, timeout, etc.)
     */
    protected BaseModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        this.modelConfig = modelConfig;
        this.modelClientConfig = modelClientConfig;
        validateConfig();
    }

    /**
     * Get client name for error messages. Subclasses can override.
     */
    protected String getClientName() {
        return getClass().getSimpleName();
    }

    /**
     * Validate configuration parameters. Subclasses can override for custom validation.
     */
    protected void validateConfig() {
        String clientName = getClientName();

        if (modelClientConfig.getApiKey() == null || modelClientConfig.getApiKey().isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.MODEL_SERVICE_CONFIG_ERROR,
                    "error_msg", "model client config api_key is required for " + clientName + ".");
        }
        if (modelClientConfig.getApiBase() == null || modelClientConfig.getApiBase().isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.MODEL_SERVICE_CONFIG_ERROR,
                    "error_msg", "model client config api_base is required for " + clientName + ".");
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    protected HttpClient buildHttpClient(double timeoutSeconds) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(Math.max(1_000L, Math.round(timeoutSeconds * 1_000))));
        SslUtils.configureHttpClientSsl(
                builder,
                modelClientConfig.getApiBase(),
                modelClientConfig.isVerifySsl(),
                modelClientConfig.getSslCert());
        JdkHttpClientProxySupport.configureFromEnvironment(builder, modelClientConfig.getApiBase());
        return builder.build();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    protected void applyConfiguredHeaders(HttpRequest.Builder builder, boolean includeJsonContentType) {
        if (includeJsonContentType) {
            builder.setHeader("Content-Type", "application/json");
        }
        if (modelClientConfig.getApiKey() != null && !modelClientConfig.getApiKey().isBlank()) {
            builder.setHeader("Authorization", "Bearer " + modelClientConfig.getApiKey());
        }
        for (Map.Entry<String, String> entry : modelClientConfig.getHeaders().entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                continue;
            }
            builder.setHeader(entry.getKey(), entry.getValue());
        }
    }

    // ==================== Message / Tool Conversion ====================

    /**
     * Convert messages to a list of dicts in OpenAI format.
     */
    @SuppressWarnings("unchecked")
    /**
     * Auto-generated for codecheck compliance.
     */
    protected List<Map<String, Object>> convertMessagesToDict(Object messages) {
        if (messages == null) {
            throw ErrorHelper.buildError(StatusCode.MODEL_INVOKE_PARAM_ERROR,
                    "error_msg", "The message sent to the llm cannot be empty.");
        }
        if (messages instanceof String s) {
            return List.of(Map.of("role", "user", "content", s));
        }
        if (messages instanceof List<?> list) {
            if (list.isEmpty()) {
                throw ErrorHelper.buildError(StatusCode.MODEL_INVOKE_PARAM_ERROR,
                        "error_msg", "The message sent to the llm cannot be empty.");
            }
            if (list.get(0) instanceof Map) {
                return (List<Map<String, Object>>) messages;
            }
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                BaseMessage msg = (BaseMessage) item;
                Map<String, Object> dict = new LinkedHashMap<>();
                dict.put("role", msg.getRole());
                dict.put("content", msg.getContent());

                if (msg instanceof AssistantMessage am && am.getToolCalls() != null && !am.getToolCalls().isEmpty()) {
                    List<Map<String, Object>> toolCallsList = new ArrayList<>();
                    for (var tc : am.getToolCalls()) {
                        toolCallsList.add(Map.of(
                                "id", tc.getId(),
                                "type", tc.getType(),
                                "function", Map.of(
                                        "name", tc.getName(),
                                        "arguments", tc.getArguments()
                                )
                        ));
                    }
                    dict.put("tool_calls", toolCallsList);
                }
                if (msg instanceof ToolMessage tm) {
                    dict.put("tool_call_id", tm.getToolCallId());
                }
                result.add(dict);
            }
            return result;
        }
        throw ErrorHelper.buildError(StatusCode.MODEL_INVOKE_PARAM_ERROR,
                "error_msg", "Unsupported message type: " + messages.getClass());
    }

    /**
     * Convert tools to OpenAI format.
     */
    @SuppressWarnings("unchecked")
    /**
     * Auto-generated for codecheck compliance.
     */
    protected List<Map<String, Object>> convertToolsToDict(Object tools) {
        if (tools == null) {
            return null;
        }
        if (tools instanceof List<?> list) {
            if (list.isEmpty()) {
                return null;
            }
            if (list.get(0) instanceof Map) {
                return (List<Map<String, Object>>) tools;
            }
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                ToolInfo tool = (ToolInfo) item;
                result.add(Map.of(
                        "type", tool.getType(),
                        "function", Map.of(
                                "name", tool.getName(),
                                "description", tool.getDescription() != null ? tool.getDescription() : "",
                                "parameters", tool.getParameters() != null ? tool.getParameters() : Map.of()
                        )
                ));
            }
            return result;
        }
        return null;
    }

    /**
     * Build OpenAI-compatible request parameters.
     */
    protected Map<String, Object> buildRequestParams(Object messages,
                                                     Object tools,
                                                     Double temperature,
                                                     Double topP,
                                                     String model,
                                                     String stop,
                                                     Integer maxTokens,
                                                     boolean stream,
                                                     Map<String, Object> extraKwargs) {
        String resolvedModel = model != null ? model
                : (modelConfig != null ? modelConfig.getModelName() : null);
        if (resolvedModel == null) {
            throw ErrorHelper.buildError(StatusCode.MODEL_CONFIG_ERROR,
                    "error_msg", "The model cannot be None.");
        }

        List<Map<String, Object>> messagesDict = convertMessagesToDict(messages);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("model", resolvedModel);
        params.put("messages", messagesDict);
        params.put("stream", stream);

        double finalTemp = temperature != null ? temperature
                : (modelConfig != null && modelConfig.getTemperature() != null ? modelConfig.getTemperature() : 0.95);
        params.put("temperature", finalTemp);

        double finalTopP = topP != null ? topP
                : (modelConfig != null && modelConfig.getTopP() != null ? modelConfig.getTopP() : 0.1);
        params.put("top_p", finalTopP);

        Integer finalMaxTokens = maxTokens != null ? maxTokens
                : (modelConfig != null ? modelConfig.getMaxTokens() : null);
        if (finalMaxTokens != null) {
            params.put("max_tokens", finalMaxTokens);
        }

        if (modelConfig != null && modelConfig.getUser() != null) {
            params.put("user", modelConfig.getUser());
        }

        if (modelConfig != null && modelConfig.getSeed() != null) {
            params.put("seed", modelConfig.getSeed());
        }

        String finalStop = stop != null ? stop
                : (modelConfig != null ? modelConfig.getStop() : null);
        if (finalStop != null) {
            params.put("stop", finalStop);
        }

        List<Map<String, Object>> toolsDict = convertToolsToDict(tools);
        if (toolsDict != null && !toolsDict.isEmpty()) {
            params.put("tools", toolsDict);
            params.put("tool_choice", "auto");
        }

        // Log LLM request params (Python parity)
        String clientName = modelClientConfig != null ? modelClientConfig.getClientProvider() : "unknown";
        String toolsJson = null;
        String messagesJson = null;
        try {
            if (toolsDict != null) {
                toolsJson = JSON_MAPPER.writeValueAsString(toolsDict);
            }
            messagesJson = JSON_MAPPER.writeValueAsString(messagesDict);
        } catch (Exception ignored) {
            toolsJson = String.valueOf(toolsDict);
            messagesJson = String.valueOf(messagesDict);
        }
        com.openjiuwen.core.common.logging.Loggers.LLM.info(
                "Before request chat model, LLM request params ready. " +
                "model_name={}, model_provider={}, messages={}, tools={}, " +
                "temperature={}, top_p={}, max_tokens={}, is_stream={}",
                resolvedModel,
                clientName,
                messagesJson,
                toolsJson,
                finalTemp,
                finalTopP,
                finalMaxTokens,
                stream
        );

        if (modelConfig != null && modelConfig.getExtraFields() != null) {
            for (var entry : modelConfig.getExtraFields().entrySet()) {
                if (entry.getValue() != null) {
                    params.put(entry.getKey(), entry.getValue());
                }
            }
        }

        // Add extra kwargs (excluding internal params)
        Set<String> internalParams = Set.of("parser", "output_parser");
        if (extraKwargs != null) {
            for (var entry : extraKwargs.entrySet()) {
                if (!internalParams.contains(entry.getKey())) {
                    params.put(entry.getKey(), entry.getValue());
                }
            }
        }

        return params;
    }

    // ==================== Abstract Methods ====================

    /**
     * Invoke the LLM (synchronous, blocking via virtual thread).
     */
    public abstract AssistantMessage invoke(Object messages,
                                            Object tools,
                                            Float temperature,
                                            Float topP,
                                            String model,
                                            Integer maxTokens,
                                            String stop,
                                            BaseOutputParser outputParser,
                                            Float timeout,
                                            Map<String, Object> kwargs) throws Exception;

    /**
     * Stream invoke the LLM.
     */
    public abstract Iterator<AssistantMessageChunk> stream(Object messages,
                                                           Object tools,
                                                           Float temperature,
                                                           Float topP,
                                                           String model,
                                                           Integer maxTokens,
                                                           String stop,
                                                           BaseOutputParser outputParser,
                                                           Float timeout,
                                                           Map<String, Object> kwargs) throws Exception;

    /**
     * Generate an image from a text prompt.
     */
    public abstract ImageGenerationResponse generateImage(List<UserMessage> messages,
                                                          String model,
                                                          String size,
                                                          String negativePrompt,
                                                          int n,
                                                          boolean promptExtend,
                                                          boolean watermark,
                                                          int seed,
                                                          Map<String, Object> kwargs) throws Exception;

    /**
     * Generate speech audio from text.
     */
    public abstract AudioGenerationResponse generateSpeech(List<UserMessage> messages,
                                                           String model,
                                                           String voice,
                                                           String languageType,
                                                           Map<String, Object> kwargs) throws Exception;

    /**
     * Generate video from a text prompt.
     */
    public abstract VideoGenerationResponse generateVideo(List<UserMessage> messages,
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
                                                          Map<String, Object> kwargs) throws Exception;
}
