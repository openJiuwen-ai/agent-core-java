// coding: utf-8
/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.tool_call.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * LLM response utilities using RITS API.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.tool_call.utils.rits}.
 */
public class RitsUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private RitsUtils() {
        // Utility class
    }

    /**
     * Get RITS response with error handling.
     *
     * @param modelId    Model identifier
     * @param prompt     Prompt string
     * @param llmApiKey  LLM API key
     * @param verifyFn   Verification function
     * @param verbose    Enable verbose logging
     * @param kwargs     Additional parameters
     * @return Response object or error map
     */
    public static Object getRitsResponse(
            String modelId,
            String prompt,
            String llmApiKey,
            Function<String, Object> verifyFn,
            boolean verbose,
            Map<String, Object> kwargs
    ) {
        try {
            return ritsResponse(modelId, prompt, llmApiKey, verifyFn, verbose, kwargs != null ? kwargs : Map.of());
        } catch (Exception e) {
            Loggers.AGENT.error("Cannot complete LLM call. Error: {}", e.getMessage());
            return Map.of("error", "Cannot complete LLM call. Error: " + e.getMessage());
        }
    }

    /**
     * Get RITS response with simplified parameters.
     *
     * @param modelId   Model identifier
     * @param prompt    Prompt string
     * @param llmApiKey LLM API key
     * @return Response string
     */
    public static String getRitsResponse(String modelId, String prompt, String llmApiKey) {
        try {
            Object response = ritsResponse(modelId, prompt, llmApiKey, null, false, Map.of());
            return response instanceof String text ? text : OBJECT_MAPPER.writeValueAsString(response);
        } catch (Exception e) {
            Loggers.AGENT.error("Cannot complete LLM call. Error: {}", e.getMessage());
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    /**
     * Execute RITS API call.
     *
     * @param modelId   Model identifier
     * @param prompt    Prompt string
     * @param llmApiKey LLM API key
     * @param verifyFn  Verification function
     * @param verbose   Enable verbose logging
     * @param kwargs    Additional parameters
     * @return Response object
     */
    public static Object ritsResponse(
            String modelId,
            String prompt,
            String llmApiKey,
            Function<String, Object> verifyFn,
            boolean verbose,
            Map<String, Object> kwargs
    ) {
        RuntimeException lastFailure = null;
        int maxAttempts = getInt(kwargs, "max_attempts", 2);
        for (int attempt = 0; attempt < Math.max(1, maxAttempts); attempt++) {
            try {
                ModelRequestConfig modelConfig = ModelRequestConfig.builder()
                        .modelName(modelId)
                        .temperature(getDouble(kwargs, "temperature", 1.0))
                        .build();
                ModelClientConfig clientConfig = ModelClientConfig.builder()
                        .clientProvider(getString(kwargs, "client_provider", "OpenAI"))
                        .apiBase(getString(kwargs, "api_base", "https://api.openai.com/v1"))
                        .apiKey(llmApiKey != null ? llmApiKey : "")
                        .verifySsl(getBoolean(kwargs, "verify_ssl", false))
                        .timeout(getDouble(kwargs, "timeout", 60.0))
                        .maxRetries(getInt(kwargs, "max_retries", 1))
                        .build();

                Model model = new Model(clientConfig, modelConfig);
                AssistantMessage response = model.invoke(
                        List.of(Map.of(
                                "role", getString(kwargs, "role", "developer"),
                                "content", prompt != null ? prompt : ""
                        )),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        Map.of()
                );

                String output = stringifyContent(response != null ? response.getContent() : null);
                if (verifyFn != null) {
                    return verifyFn.apply(output);
                }
                return output;
            } catch (Exception e) {
                lastFailure = e instanceof RuntimeException runtime ? runtime : new RuntimeException(e);
                if (verbose) {
                    Loggers.AGENT.warn("RITS attempt {} failed: {}", attempt + 1, e.getMessage());
                }
            }
        }
        String message = lastFailure != null ? lastFailure.getMessage() : "unknown failure";
        throw new RuntimeException("RITS response failed: " + message, lastFailure);
    }

    private static String stringifyContent(Object content) throws Exception {
        if (content == null) {
            return "";
        }
        if (content instanceof String text) {
            return text;
        }
        return OBJECT_MAPPER.writeValueAsString(content);
    }

    private static String getString(Map<String, Object> kwargs, String key, String defaultValue) {
        Object value = kwargs != null ? kwargs.get(key) : null;
        return value instanceof String text && !text.isBlank() ? text : defaultValue;
    }

    private static int getInt(Map<String, Object> kwargs, String key, int defaultValue) {
        Object value = kwargs != null ? kwargs.get(key) : null;
        return value instanceof Number number ? number.intValue() : defaultValue;
    }

    private static double getDouble(Map<String, Object> kwargs, String key, double defaultValue) {
        Object value = kwargs != null ? kwargs.get(key) : null;
        return value instanceof Number number ? number.doubleValue() : defaultValue;
    }

    private static boolean getBoolean(Map<String, Object> kwargs, String key, boolean defaultValue) {
        Object value = kwargs != null ? kwargs.get(key) : null;
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0d;
        }
        if (value instanceof String text) {
            return "true".equalsIgnoreCase(text) || "1".equals(text.trim());
        }
        return defaultValue;
    }
}
