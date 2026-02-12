// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.llm;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.modelclients.*;
import com.openjiuwen.core.foundation.llm.schema.*;
import com.openjiuwen.core.foundation.llm.outputparsers.BaseOutputParser;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 统一的LLM调用入口。
 * 对应 Python: agent-core/openjiuwen/core/foundation/llm/model.py
 * 
 * 职责：
 * 1. 根据client_id或配置获取/创建ModelClient实例
 * 2. 委托给ModelClient执行实际的LLM调用
 * 3. 提供统一的接口（invoke, stream）
 */
public class Model {

    private static final Map<String, Class<? extends BaseModelClient>> CLIENT_TYPE_REGISTRY = new HashMap<>();

    static {
        CLIENT_TYPE_REGISTRY.put("OpenAI", OpenAIModelClient.class);
        CLIENT_TYPE_REGISTRY.put("SiliconFlow", SiliconFlowModelClient.class);
    }

    private final ModelRequestConfig modelConfig;
    private final ModelClientConfig modelClientConfig;
    private final BaseModelClient client;

    /**
     * 初始化Model实例
     *
     * @param modelClientConfig 客户端配置
     * @param modelConfig       模型参数配置
     */
    public Model(ModelClientConfig modelClientConfig, ModelRequestConfig modelConfig) {
        this.modelConfig = modelConfig;
        this.modelClientConfig = modelClientConfig;

        if (modelClientConfig == null) {
            throw new JiuWenBaseException(
                    StatusCode.MODEL_SERVICE_CONFIG_ERROR.getCode(),
                    "model client config is none."
            );
        }

        this.client = createModelClient(modelClientConfig);
    }

    /**
     * 根据client_type创建对应的ModelClient实例
     */
    private BaseModelClient createModelClient(ModelClientConfig clientConfig) {
        if (clientConfig.getClientProvider() == null || clientConfig.getClientProvider().isEmpty()) {
            throw new JiuWenBaseException(
                    StatusCode.MODEL_SERVICE_CONFIG_ERROR.getCode(),
                    "model client config client_provider is none."
            );
        }
        if (clientConfig.getClientId() == null || clientConfig.getClientId().isEmpty()) {
            throw new JiuWenBaseException(
                    StatusCode.MODEL_SERVICE_CONFIG_ERROR.getCode(),
                    "model client config client_id is none."
            );
        }

        String clientProvider = clientConfig.getClientProvider();
        Class<? extends BaseModelClient> clientClass = CLIENT_TYPE_REGISTRY.get(clientProvider);

        if (clientClass == null) {
            String supportedTypes = String.join(", ", CLIENT_TYPE_REGISTRY.keySet());
            throw new JiuWenBaseException(
                    StatusCode.MODEL_SERVICE_CONFIG_ERROR.getCode(),
                    "Unsupported client_type: '" + clientProvider + "'. Supported types: " + supportedTypes
            );
        }

        try {
            return clientClass
                    .getConstructor(ModelRequestConfig.class, ModelClientConfig.class)
                    .newInstance(modelConfig, clientConfig);
        } catch (Exception e) {
            throw new JiuWenBaseException(
                    StatusCode.MODEL_SERVICE_CONFIG_ERROR.getCode(),
                    "Failed to create model client: " + e.getMessage()
            );
        }
    }

    /**
     * 异步LLM调用
     */
    public CompletableFuture<AssistantMessage> invoke(
            Object messages,
            List<?> tools,
            Double temperature,
            Double topP,
            Integer maxTokens,
            String stop,
            String model,
            BaseOutputParser<?> outputParser,
            Double timeout,
            Map<String, Object> kwargs
    ) {
        return client.invoke(
                messages,
                tools,
                temperature,
                topP,
                model,
                maxTokens,
                stop,
                outputParser,
                timeout,
                kwargs
        );
    }

    /**
     * 异步LLM调用（简化版）
     */
    public CompletableFuture<AssistantMessage> invoke(Object messages) {
        return invoke(messages, null, null, null, null, null, null, null, null, null);
    }

    /**
     * 异步流式LLM调用
     */
    public Iterator<AssistantMessageChunk> stream(
            Object messages,
            List<?> tools,
            Double temperature,
            Double topP,
            Integer maxTokens,
            String stop,
            String model,
            BaseOutputParser<?> outputParser,
            Double timeout,
            Map<String, Object> kwargs
    ) {
        return client.stream(
                messages,
                tools,
                temperature,
                topP,
                model,
                maxTokens,
                stop,
                outputParser,
                timeout,
                kwargs
        );
    }

    /**
     * 异步流式LLM调用（简化版）
     */
    public Iterator<AssistantMessageChunk> stream(Object messages) {
        return stream(messages, null, null, null, null, null, null, null, null, null);
    }

    // Getters
    public ModelRequestConfig getModelConfig() {
        return modelConfig;
    }

    public ModelClientConfig getModelClientConfig() {
        return modelClientConfig;
    }

    public BaseModelClient getClient() {
        return client;
    }

    /**
     * 注册新的客户端类型
     */
    public static void registerClientType(String providerName, Class<? extends BaseModelClient> clientClass) {
        CLIENT_TYPE_REGISTRY.put(providerName, clientClass);
    }
}

