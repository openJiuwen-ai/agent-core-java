/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.model_clients.InferenceAffinityModelClient;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unified entry point for InferenceAffinity (vLLM-style) invocation.
 * 
 * @since 0.1.7
 */
public class InferenceAffinityModel {
    private final ModelRequestConfig modelConfig;
    private final ModelClientConfig modelClientConfig;
    private final InferenceAffinityModelClient client;

    /**
     * InferenceAffinityModel.
     * 
     * @param modelClientConfig modelClientConfig
     * @param modelConfig modelConfig
     * @since 0.1.7
     */
    public InferenceAffinityModel(ModelClientConfig modelClientConfig, ModelRequestConfig modelConfig) {
        if (modelClientConfig == null) {
            throw ErrorHelper.buildError(StatusCode.MODEL_SERVICE_CONFIG_ERROR, "error_msg",
                    "model client config is none");
        }
        this.modelConfig = modelConfig;
        this.modelClientConfig = modelClientConfig;
        this.client = new InferenceAffinityModelClient(modelConfig, modelClientConfig);
    }

    /**
     * getModelConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    public ModelRequestConfig getModelConfig() {
        return modelConfig;
    }

    /**
     * getModelClientConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    public ModelClientConfig getModelClientConfig() {
        return modelClientConfig;
    }

    /**
     * invoke.
     * 
     * @param messages messages
     * @param tools tools
     * @param temperature temperature
     * @param topP topP
     * @param maxTokens maxTokens
     * @param stop stop
     * @param model model
     * @param outputParser outputParser
     * @param sessionId sessionId
     * @param enableCacheSharing enableCacheSharing
     * @param kwargs kwargs
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP, Integer maxTokens,
            String stop, String model, BaseOutputParser outputParser, String sessionId, boolean enableCacheSharing,
            Map<String, Object> kwargs) throws Exception {
        Map<String, Object> options = kwargs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(kwargs);
        if (sessionId != null) {
            options.put("session_id", sessionId);
        }
        options.put("enable_cache_sharing", enableCacheSharing);
        return client.invoke(messages, tools, temperature, topP, model, maxTokens, stop, outputParser, null, options);
    }

    /**
     * stream.
     * 
     * @param messages messages
     * @param tools tools
     * @param temperature temperature
     * @param topP topP
     * @param maxTokens maxTokens
     * @param stop stop
     * @param model model
     * @param outputParser outputParser
     * @param sessionId sessionId
     * @param enableCacheSharing enableCacheSharing
     * @param kwargs kwargs
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP,
            Integer maxTokens, String stop, String model, BaseOutputParser outputParser, String sessionId,
            boolean enableCacheSharing, Map<String, Object> kwargs) throws Exception {
        Map<String, Object> options = kwargs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(kwargs);
        if (sessionId != null) {
            options.put("session_id", sessionId);
        }
        options.put("enable_cache_sharing", enableCacheSharing);
        return client.stream(messages, tools, temperature, topP, model, maxTokens, stop, outputParser, null, options);
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
    public boolean release(String sessionId, List<?> messages, int messagesReleasedIndex, List<?> tools,
            Integer toolsReleasedIndex, String model) throws Exception {
        return client.release(sessionId, messages, messagesReleasedIndex, tools, toolsReleasedIndex, model);
    }
}
