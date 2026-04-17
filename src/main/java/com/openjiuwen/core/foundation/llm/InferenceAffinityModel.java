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
 */
public class InferenceAffinityModel {

    private final ModelRequestConfig modelConfig;
    private final ModelClientConfig modelClientConfig;
    private final InferenceAffinityModelClient client;

    public InferenceAffinityModel(ModelClientConfig modelClientConfig, ModelRequestConfig modelConfig) {
        if (modelClientConfig == null) {
            throw ErrorHelper.buildError(StatusCode.MODEL_SERVICE_CONFIG_ERROR,
                    "error_msg", "model client config is none");
        }
        this.modelConfig = modelConfig;
        this.modelClientConfig = modelClientConfig;
        this.client = new InferenceAffinityModelClient(modelConfig, modelClientConfig);
    }

    public ModelRequestConfig getModelConfig() {
        return modelConfig;
    }

    public ModelClientConfig getModelClientConfig() {
        return modelClientConfig;
    }

    public AssistantMessage invoke(Object messages,
                                   Object tools,
                                   Float temperature,
                                   Float topP,
                                   Integer maxTokens,
                                   String stop,
                                   String model,
                                   BaseOutputParser outputParser,
                                   String sessionId,
                                   boolean enableCacheSharing,
                                   Map<String, Object> kwargs) throws Exception {
        Map<String, Object> options = kwargs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(kwargs);
        if (sessionId != null) {
            options.put("session_id", sessionId);
        }
        options.put("enable_cache_sharing", enableCacheSharing);
        return client.invoke(messages, tools, temperature, topP, model, maxTokens, stop, outputParser, null, options);
    }

    public Iterator<AssistantMessageChunk> stream(Object messages,
                                                  Object tools,
                                                  Float temperature,
                                                  Float topP,
                                                  Integer maxTokens,
                                                  String stop,
                                                  String model,
                                                  BaseOutputParser outputParser,
                                                  String sessionId,
                                                  boolean enableCacheSharing,
                                                  Map<String, Object> kwargs) throws Exception {
        Map<String, Object> options = kwargs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(kwargs);
        if (sessionId != null) {
            options.put("session_id", sessionId);
        }
        options.put("enable_cache_sharing", enableCacheSharing);
        return client.stream(messages, tools, temperature, topP, model, maxTokens, stop, outputParser, null, options);
    }

    public boolean release(String sessionId,
                           List<?> messages,
                           int messagesReleasedIndex,
                           List<?> tools,
                           Integer toolsReleasedIndex,
                           String model) throws Exception {
        return client.release(sessionId, messages, messagesReleasedIndex, tools, toolsReleasedIndex, model);
    }
}
