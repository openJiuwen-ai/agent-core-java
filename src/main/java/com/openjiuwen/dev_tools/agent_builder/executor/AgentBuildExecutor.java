/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.executor;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.ValidationError;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.dev_tools.agent_builder.builders.BaseAgentBuilder;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderEnums;
import com.openjiuwen.dev_tools.agent_builder.utils.ProgressReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Agent build executor — orchestrates the build process.
 * <p>
 * Mirrors Python's {@code executor} in
 * {@code openjiuwen.dev_tools.agent_builder.executor.executor}.
 */
public class AgentBuildExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(AgentBuildExecutor.class);

    private final BaseAgentBuilder builder;
    private final HistoryManager historyManager;
    private final ProgressReporter progressReporter;

    public AgentBuildExecutor(BaseAgentBuilder builder, HistoryManager historyManager) {
        this.builder = builder;
        this.historyManager = historyManager;
        this.progressReporter = new ProgressReporter();
    }

    public static Model createCoreModel(Map<String, Object> modelInfo) {
        Map<String, Object> info = modelInfo != null ? modelInfo : Map.of();
        Object provider = firstPresent(info, "model_provider", "client_provider");
        Object modelName = firstPresent(info, "model_name", "model");
        Object apiKey = info.get("api_key");
        if (isBlank(provider) || isBlank(modelName) || isBlank(apiKey)) {
            throw new ValidationError(
                    StatusCode.COMPONENT_LLM_CONFIG_INVALID,
                    "model_info missing required fields",
                    Map.of("required", List.of("model_provider", "model_name", "api_key"),
                            "got_keys", new ArrayList<>(info.keySet())),
                    null,
                    Map.of("error_msg", "model_info missing required fields"));
        }

        String clientProvider = normalizeProvider(String.valueOf(provider));
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(clientProvider)
                .clientId(String.valueOf(modelName))
                .apiKey(String.valueOf(apiKey))
                .apiBase(String.valueOf(info.getOrDefault("api_base", "")))
                .verifySsl(Boolean.TRUE.equals(info.get("verify_ssl")))
                .build();
        ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                .modelName(String.valueOf(modelName))
                .temperature(asDouble(info.get("temperature")))
                .topP(asDouble(info.get("top_p")))
                .maxTokens(asInteger(info.get("max_tokens")))
                .build();
        return new Model(clientConfig, requestConfig);
    }

    private static Object firstPresent(Map<String, Object> info, String first, String second) {
        Object value = info.get(first);
        return value != null ? value : info.get(second);
    }

    private static boolean isBlank(Object value) {
        return value == null || String.valueOf(value).isBlank();
    }

    private static String normalizeProvider(String provider) {
        return switch (provider) {
            case "openai", "OpenAI" -> "OpenAI";
            case "openrouter", "OpenRouter" -> "OpenRouter";
            case "siliconflow", "SiliconFlow" -> "SiliconFlow";
            case "dashscope", "DashScope" -> "DashScope";
            default -> provider;
        };
    }

    private static Double asDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private static Integer asInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    /** Execute a build query. */
    public Map<String, Object> execute(Map<String, Object> query) {
        LOG.info("[AgentBuildExecutor] Executing build query, state={}", builder.getState());

        progressReporter.report(AgentBuilderEnums.ProgressStage.INITIALIZING,
                AgentBuilderEnums.ProgressStatus.RUNNING);

        Map<String, Object> result = builder.build(query, historyManager.getHistory());

        historyManager.addEntry(query);
        LOG.info("[AgentBuildExecutor] Build completed, state={}", builder.getState());
        return result;
    }
}
