// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.llm.modelclients;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.*;
import com.openjiuwen.core.foundation.llm.outputparsers.BaseOutputParser;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * LLM模型客户端抽象基类。
 * 对应 Python: agent-core/openjiuwen/core/foundation/llm/model_clients/base_model_client.py
 */
public abstract class BaseModelClient {

    protected static final Logger log = LoggerFactory.getLogger(BaseModelClient.class);

    protected final ModelRequestConfig modelConfig;
    protected final ModelClientConfig modelClientConfig;

    /**
     * 初始化模型客户端
     *
     * @param modelConfig       模型参数配置
     * @param modelClientConfig 客户端配置
     */
    protected BaseModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        this.modelConfig = modelConfig;
        this.modelClientConfig = modelClientConfig;
        validateConfig();
    }

    /**
     * 获取客户端名称（子类可覆盖）
     */
    protected String getClientName() {
        return this.getClass().getSimpleName();
    }

    /**
     * 验证配置参数
     */
    protected void validateConfig() {
        String clientName = getClientName();

        if (modelClientConfig.getApiKey() == null || modelClientConfig.getApiKey().isEmpty()) {
            throw new JiuWenBaseException(
                    StatusCode.MODEL_SERVICE_CONFIG_ERROR.getCode(),
                    "model client config api_key is required for " + clientName + "."
            );
        }
        if (modelClientConfig.getApiBase() == null || modelClientConfig.getApiBase().isEmpty()) {
            throw new JiuWenBaseException(
                    StatusCode.MODEL_SERVICE_CONFIG_ERROR.getCode(),
                    "model client config api_base is required for " + clientName + "."
            );
        }
        // 验证SSL配置：如果verify_ssl=true，则必须提供ssl_cert
        if (modelClientConfig.isVerifySsl() && 
                (modelClientConfig.getSslCert() == null || modelClientConfig.getSslCert().isEmpty())) {
            throw new JiuWenBaseException(
                    StatusCode.MODEL_SERVICE_CONFIG_ERROR.getCode(),
                    "model client config ssl_cert is required when verify_ssl is true for " + clientName + "."
            );
        }
    }

    /**
     * 将消息转换为字典格式
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> convertMessagesToDict(Object messages) {
        if (messages == null) {
            throw new JiuWenBaseException(
                    StatusCode.MODEL_INVOKE_PARAM_ERROR.getCode(),
                    "The message sent to the llm cannot be empty."
            );
        }

        if (messages instanceof String str) {
            return List.of(Map.of("role", "user", "content", str));
        }

        if (messages instanceof List<?> list) {
            if (list.isEmpty()) {
                throw new JiuWenBaseException(
                        StatusCode.MODEL_INVOKE_PARAM_ERROR.getCode(),
                        "The message sent to the llm cannot be empty."
                );
            }

            // 如果已经是Map列表，直接返回
            if (list.get(0) instanceof Map) {
                return (List<Map<String, Object>>) messages;
            }

            // 转换BaseMessage列表
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object msg : list) {
                if (msg instanceof BaseMessage baseMessage) {
                    Map<String, Object> msgDict = new HashMap<>();
                    msgDict.put("role", baseMessage.getRole());
                    msgDict.put("content", baseMessage.getContent());

                    if (msg instanceof AssistantMessage assistantMessage && assistantMessage.getToolCalls() != null) {
                        List<Map<String, Object>> toolCallsList = new ArrayList<>();
                        for (ToolCall tc : assistantMessage.getToolCalls()) {
                            toolCallsList.add(Map.of(
                                    "id", tc.getId() != null ? tc.getId() : "",
                                    "type", tc.getType() != null ? tc.getType() : "function",
                                    "function", Map.of(
                                            "name", tc.getName() != null ? tc.getName() : "",
                                            "arguments", tc.getArguments() != null ? tc.getArguments() : ""
                                    )
                            ));
                        }
                        msgDict.put("tool_calls", toolCallsList);
                    }

                    if (msg instanceof ToolMessage toolMessage) {
                        msgDict.put("tool_call_id", toolMessage.getToolCallId());
                    }

                    result.add(msgDict);
                }
            }
            return result;
        }

        throw new JiuWenBaseException(
                StatusCode.MODEL_INVOKE_PARAM_ERROR.getCode(),
                "Unsupported message type: " + messages.getClass().getName()
        );
    }

    /**
     * 将工具转换为字典格式
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> convertToolsToDict(List<?> tools) {
        if (tools == null || tools.isEmpty()) {
            return null;
        }

        // 如果已经是Map列表
        if (tools.get(0) instanceof Map) {
            return (List<Map<String, Object>>) tools;
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object tool : tools) {
            if (tool instanceof ToolInfo toolInfo) {
                result.add(Map.of(
                        "type", toolInfo.type(),
                        "function", Map.of(
                                "name", toolInfo.name(),
                                "description", toolInfo.description(),
                                "parameters", toolInfo.parameters()
                        )
                ));
            }
        }
        return result;
    }

    /**
     * 构建请求参数
     */
    public Map<String, Object> buildRequestParams(
            Object messages,
            List<?> tools,
            Double temperature,
            Double topP,
            String model,
            String stop,
            Integer maxTokens,
            boolean stream,
            Map<String, Object> kwargs
    ) {
        String finalModel = model != null ? model : modelConfig.getModelName();
        if (finalModel == null || finalModel.isEmpty()) {
            throw new JiuWenBaseException(
                    StatusCode.MODEL_CONFIG_ERROR.getCode(),
                    "The model cannot be None."
            );
        }

        List<Map<String, Object>> messagesDict = convertMessagesToDict(messages);

        Map<String, Object> params = new HashMap<>();
        params.put("model", finalModel);
        params.put("messages", messagesDict);
        params.put("stream", stream);

        // 添加温度参数
        double finalTemperature = temperature != null ? temperature : modelConfig.getTemperature();
        params.put("temperature", finalTemperature);

        // 添加top_p参数
        double finalTopP = topP != null ? topP : modelConfig.getTopP();
        params.put("top_p", finalTopP);

        // 添加max_tokens
        Integer finalMaxTokens = maxTokens != null ? maxTokens : modelConfig.getMaxTokens();
        if (finalMaxTokens != null) {
            params.put("max_tokens", finalMaxTokens);
        }

        // 添加stop
        String finalStop = stop != null ? stop : modelConfig.getStop();
        if (finalStop != null) {
            params.put("stop", finalStop);
        }

        // 添加工具
        List<Map<String, Object>> toolsDict = convertToolsToDict(tools);
        if (toolsDict != null && !toolsDict.isEmpty()) {
            params.put("tools", toolsDict);
            params.put("tool_choice", "auto");
        }

        // 添加额外参数
        if (kwargs != null) {
            Set<String> internalParams = Set.of("parser", "output_parser");
            for (Map.Entry<String, Object> entry : kwargs.entrySet()) {
                if (!internalParams.contains(entry.getKey())) {
                    params.put(entry.getKey(), entry.getValue());
                }
            }
        }

        return params;
    }

    /**
     * 异步调用LLM
     */
    public abstract CompletableFuture<AssistantMessage> invoke(
            Object messages,
            List<?> tools,
            Double temperature,
            Double topP,
            String model,
            Integer maxTokens,
            String stop,
            BaseOutputParser<?> outputParser,
            Double timeout,
            Map<String, Object> kwargs
    );

    /**
     * 异步流式调用LLM
     */
    public abstract Iterator<AssistantMessageChunk> stream(
            Object messages,
            List<?> tools,
            Double temperature,
            Double topP,
            String model,
            Integer maxTokens,
            String stop,
            BaseOutputParser<?> outputParser,
            Double timeout,
            Map<String, Object> kwargs
    );

    // Getters
    public ModelRequestConfig getModelConfig() {
        return modelConfig;
    }

    public ModelClientConfig getModelClientConfig() {
        return modelClientConfig;
    }
}

