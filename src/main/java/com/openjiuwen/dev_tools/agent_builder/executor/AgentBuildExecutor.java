/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.executor;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.ValidationError;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.dev_tools.agent_builder.builders.AgentBuilderFactory;
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
    private final String query;
    private final String sessionId;
    private final String agentType;
    private final Model llm;

    public AgentBuildExecutor(BaseAgentBuilder builder, HistoryManager historyManager) {
        this.builder = builder;
        this.historyManager = historyManager;
        this.progressReporter = new ProgressReporter();
        this.query = null;
        this.sessionId = null;
        this.agentType = null;
        this.llm = null;
    }

    public AgentBuildExecutor(
            String query,
            String sessionId,
            String agentType,
            Map<String, HistoryManager> historyManagerMap,
            Map<String, BaseAgentBuilder> agentBuilderMap,
            Map<String, Object> modelInfo,
            boolean enableProgress) {
        this.query = query;
        this.sessionId = sessionId;
        this.agentType = agentType;
        this.llm = createCoreModel(modelInfo);
        this.historyManager = getHistoryManager(sessionId, historyManagerMap);
        this.progressReporter = enableProgress ? ProgressReporter.createReporter(sessionId, agentType) : null;
        Map<String, BaseAgentBuilder> builders = agentBuilderMap != null ? agentBuilderMap : new LinkedHashMap<>();
        this.builder = builders.computeIfAbsent(sessionId, key -> createBuilder(agentType, progressReporter));
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
        String apiBase = resolveApiBase(clientProvider, info.get("api_base"));
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(clientProvider)
                .clientId(String.valueOf(modelName))
                .apiKey(String.valueOf(apiKey))
                .apiBase(apiBase)
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

    public static HistoryManager getHistoryManager(
            String sessionId,
            Map<String, HistoryManager> historyManagerMap) {
        Objects.requireNonNull(historyManagerMap, "historyManagerMap");
        return historyManagerMap.computeIfAbsent(sessionId, key -> new HistoryManager());
    }

    private static BaseAgentBuilder createBuilder(String agentType, ProgressReporter progressReporter) {
        AgentBuilderEnums.AgentType type;
        try {
            type = AgentBuilderEnums.AgentType.fromValue(agentType);
        } catch (IllegalArgumentException e) {
            throw new ValidationError(
                    StatusCode.COMPONENT_LLM_CONFIG_INVALID,
                    "Unsupported agent type: " + agentType,
                    Map.of("agent_type", agentType,
                            "supported_types", List.of("llm_agent", "workflow")),
                    e,
                    Map.of("error_msg", "Unsupported agent type: " + agentType));
        }
        return AgentBuilderFactory.create(type, progressReporter);
    }

    public String getQuery() {
        return query;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getAgentType() {
        return agentType;
    }

    public Model getLlm() {
        return llm;
    }

    public HistoryManager getHistoryManager() {
        return historyManager;
    }

    public ProgressReporter getProgressReporter() {
        return progressReporter;
    }

    public BaseAgentBuilder getBuilder() {
        return builder;
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

    private static String resolveApiBase(String clientProvider, Object apiBase) {
        if (apiBase != null && !String.valueOf(apiBase).isBlank()) {
            return String.valueOf(apiBase);
        }
        return switch (clientProvider) {
            case "OpenAI" -> "https://api.openai.com/v1";
            case "OpenRouter" -> "https://openrouter.ai/api/v1";
            case "SiliconFlow" -> "https://api.siliconflow.cn/v1";
            case "DashScope" -> "https://dashscope.aliyuncs.com/compatible-mode/v1";
            default -> "";
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

        if (progressReporter != null) {
            progressReporter.report(AgentBuilderEnums.ProgressStage.INITIALIZING,
                    AgentBuilderEnums.ProgressStatus.RUNNING);
        }

        Map<String, Object> result = builder.build(query, historyManager.getHistory());

        historyManager.addEntry(query);
        LOG.info("[AgentBuildExecutor] Build completed, state={}", builder.getState());
        return result;
    }

    public Map<String, Object> execute() {
        Map<String, Object> queryMap = new LinkedHashMap<>();
        queryMap.put("query", query);
        queryMap.put("session_id", sessionId);
        queryMap.put("agent_type", agentType);

        historyManager.addUserMessage(query);
        LOG.info("[AgentBuildExecutor] Executing build query, state={}", builder.getState());
        Map<String, Object> result = builder.build(queryMap, historyManager.getHistory());
        LOG.info("[AgentBuildExecutor] Build completed, state={}", builder.getState());
        return result;
    }

    public Map<String, Object> getBuildStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("session_id", sessionId);
        status.put("agent_type", agentType);
        status.putAll(builder.getBuildStatus());
        return status;
    }
}
