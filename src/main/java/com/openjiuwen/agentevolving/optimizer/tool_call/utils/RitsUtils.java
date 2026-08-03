/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.optimizer.tool_call.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

/**
 * RITS LLM call helper.
 *
 * <p>Mirrors Python's module functions in
 * {@code openjiuwen/agent_evolving/optimizer/tool_call/utils/rits.py}.</p>
 */
public final class RitsUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_ATTEMPTS = 2;
    private static volatile ResponseProvider responseProvider = RitsUtils::invokeModel;

    private RitsUtils() {
    }

    public static Object getRitsResponse(String modelId, String prompt, String llmApiKey) {
        return getRitsResponse(modelId, prompt, llmApiKey, null, false, Map.of());
    }

    public static Object getRitsResponse(
            String modelId,
            String prompt,
            String llmApiKey,
            Function<String, Object> verifyFn
    ) {
        return getRitsResponse(modelId, prompt, llmApiKey, verifyFn, false, Map.of());
    }

    public static Object getRitsResponse(
            String modelId,
            String prompt,
            String llmApiKey,
            Function<String, Object> verifyFn,
            boolean verbose,
            Map<String, Object> kwargs
    ) {
        try {
            return ritsResponse(modelId, prompt, llmApiKey, verifyFn, verbose, kwargs);
        } catch (Exception exception) {
            return Map.of("error", "Cannot complete LLM call. Error: " + exception.getMessage());
        }
    }

    public static Object ritsResponse(
            String modelId,
            String prompt,
            String llmApiKey,
            Function<String, Object> verifyFn,
            boolean verbose,
            Map<String, Object> kwargs
    ) {
        RuntimeException lastFailure = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                ModelRequestConfig modelConfig = ModelRequestConfig.builder()
                        .modelName(modelId)
                        .temperature(1.0d)
                        .build();
                ModelClientConfig modelClient = ModelClientConfig.builder()
                        .clientProvider("OpenAI")
                        .apiBase("https://api.openai.com/v1")
                        .apiKey(llmApiKey)
                        .verifySsl(false)
                        .build();

                String output = responseProvider.invoke(
                        modelConfig,
                        modelClient,
                        new BaseMessage("developer", prompt == null ? "" : prompt)
                );
                if (verifyFn != null) {
                    return verifyFn.apply(output);
                }
                return output;
            } catch (Exception exception) {
                lastFailure = exception instanceof RuntimeException runtimeException
                        ? runtimeException
                        : new RuntimeException(exception);
            }
        }
        throw lastFailure != null ? lastFailure : new RuntimeException("RITS response failed");
    }

    static void setResponseProviderForTesting(ResponseProvider provider) {
        responseProvider = provider == null ? RitsUtils::invokeModel : provider;
    }

    private static String invokeModel(
            ModelRequestConfig modelConfig,
            ModelClientConfig modelClientConfig,
            BaseMessage message
    ) throws Exception {
        Model model = new Model(modelClientConfig, modelConfig);
        AssistantMessage response;
        try {
            response = model.invoke(List.of(message)).toCompletableFuture().get();
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception checked) {
                throw checked;
            }
            throw new RuntimeException(cause);
        }
        return stringifyContent(response == null ? null : response.getContent());
    }

    private static String stringifyContent(Object content) throws Exception {
        if (content == null) {
            return null;
        }
        if (content instanceof String text) {
            return text;
        }
        return OBJECT_MAPPER.writeValueAsString(content);
    }

    /**
     * Injectable response boundary.
     *
     * <p>Mirrors Python's {@code OpenAIModelClient.invoke} call in
     * {@code openjiuwen/agent_evolving/optimizer/tool_call/utils/rits.py}.</p>
     */
    @FunctionalInterface
    interface ResponseProvider {

        String invoke(
                ModelRequestConfig modelConfig,
                ModelClientConfig modelClientConfig,
                BaseMessage message
        ) throws Exception;
    }
}
