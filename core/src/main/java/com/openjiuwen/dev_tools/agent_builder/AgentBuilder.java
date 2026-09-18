// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.dev_tools.agent_builder;

import com.openjiuwen.core.common.security.JsonUtils;
import com.openjiuwen.dev_tools.agent_builder.builders.BaseAgentBuilder;
import com.openjiuwen.dev_tools.agent_builder.executor.AgentBuilderExecutor;
import com.openjiuwen.dev_tools.agent_builder.executor.HistoryManager;
import com.openjiuwen.dev_tools.agent_builder.utils.BuildProgress;
import com.openjiuwen.dev_tools.agent_builder.utils.ProgressManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unified Agent Builder entry point.
 *
 * <p>Mirrors Python's {@code AgentBuilder} in
 * {@code openjiuwen/dev_tools/agent_builder/main.py}.</p>
 */
public class AgentBuilder {

    private final Map<String, Object> modelInfo;
    private final Map<String, HistoryManager> historyManagerMap;
    private final Map<String, BaseAgentBuilder> agentBuilderMap;
    private final ExecutorFactory executorFactory;

    public AgentBuilder() {
        this(null);
    }

    public AgentBuilder(Map<String, Object> modelInfo) {
        this(modelInfo, null, null);
    }

    public AgentBuilder(
            Map<String, Object> modelInfo,
            Map<String, HistoryManager> historyManagerMap,
            Map<String, BaseAgentBuilder> agentBuilderMap) {
        this(modelInfo, historyManagerMap, agentBuilderMap, AgentBuilder::defaultExecutor);
    }

    AgentBuilder(
            Map<String, Object> modelInfo,
            Map<String, HistoryManager> historyManagerMap,
            Map<String, BaseAgentBuilder> agentBuilderMap,
            ExecutorFactory executorFactory) {
        this.modelInfo = modelInfo != null ? modelInfo : new LinkedHashMap<>();
        this.historyManagerMap = historyManagerMap != null ? historyManagerMap : new LinkedHashMap<>();
        this.agentBuilderMap = agentBuilderMap != null ? agentBuilderMap : new LinkedHashMap<>();
        this.executorFactory = executorFactory != null ? executorFactory : AgentBuilder::defaultExecutor;
    }

    public Map<String, Object> buildAgent(String query, String sessionId) {
        return buildAgent(query, sessionId, "llm_agent");
    }

    public Map<String, Object> buildAgent(String query, String sessionId, String agentType) {
        String effectiveAgentType = agentType != null ? agentType : "llm_agent";
        BuildExecutor executor = executorFactory.create(
                query,
                sessionId,
                effectiveAgentType,
                historyManagerMap,
                agentBuilderMap,
                modelInfo,
                true
        );

        Object result = executor.execute();
        Map<String, Object> statusInfo = executor.getBuildStatus();
        String state = String.valueOf(statusInfo.getOrDefault("state", "unknown"));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", mapStateToStatus(state, effectiveAgentType));
        response.put("session_id", sessionId);
        response.put("agent_type", effectiveAgentType);

        if (result instanceof String resultText) {
            handleStringResult(response, resultText, effectiveAgentType);
        } else if (result instanceof Map<?, ?> resultMap) {
            resultMap.forEach((key, value) -> response.put(String.valueOf(key), value));
        } else {
            response.put("response", result == null ? "None" : String.valueOf(result));
        }

        return response;
    }

    public Map<String, Object> buildLlmAgent(String query, String sessionId) {
        return buildAgent(query, sessionId, "llm_agent");
    }

    public Map<String, Object> buildWorkflow(String query, String sessionId) {
        return buildAgent(query, sessionId, "workflow");
    }

    public List<Map<String, String>> getSessionHistory(String sessionId) {
        return getSessionHistory(sessionId, null);
    }

    public List<Map<String, String>> getSessionHistory(String sessionId, Integer k) {
        HistoryManager historyManager = historyManagerMap.get(sessionId);
        if (historyManager == null) {
            return List.of();
        }
        if (k != null && k != 0) {
            return historyManager.getLatestKMessages(k);
        }
        return historyManager.getHistory();
    }

    public void clearSession(String sessionId) {
        HistoryManager historyManager = historyManagerMap.get(sessionId);
        if (historyManager != null) {
            historyManager.clear();
        }

        BaseAgentBuilder builder = agentBuilderMap.get(sessionId);
        if (builder != null) {
            builder.reset();
        }
    }

    public Map<String, Object> getBuildStatus(String sessionId) {
        BaseAgentBuilder builder = agentBuilderMap.get(sessionId);
        if (builder == null) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("session_id", sessionId);
            result.put("state", "not_found");
            return result;
        }
        return builder.getBuildStatus();
    }

    public static Map<String, Object> getProgress(String sessionId) {
        BuildProgress progress = ProgressManager.PROGRESS_MANAGER.getProgress(sessionId);
        return progress != null ? progress.toDict() : null;
    }

    public static String mapStateToStatus(String state, String agentType) {
        return switch (String.valueOf(state)) {
            case "initial" -> "llm_agent".equals(agentType) ? "clarifying" : "requesting";
            case "processing" -> "processing";
            case "completed" -> "completed";
            default -> "unknown";
        };
    }

    public Map<String, Object> getModelInfo() {
        return modelInfo;
    }

    public Map<String, HistoryManager> getHistoryManagerMap() {
        return historyManagerMap;
    }

    public Map<String, BaseAgentBuilder> getAgentBuilderMap() {
        return agentBuilderMap;
    }

    private static void handleStringResult(Map<String, Object> response, String resultText, String agentType) {
        try {
            response.put("dsl", JsonUtils.safeJsonLoads(resultText));
            response.put("status", "completed");
            return;
        } catch (Exception ignored) {
            // Python falls back to text/mermaid response handling when JSON parsing fails.
        }

        if ("llm_agent".equals(agentType)) {
            response.put("response", resultText);
            response.put("status", "clarifying");
            return;
        }

        if ("workflow".equals(agentType) && (resultText.contains("graph") || resultText.contains("flowchart"))) {
            response.put("mermaid_code", resultText);
            response.put("status", "processing");
            return;
        }

        if ("workflow".equals(agentType)) {
            response.put("response", resultText);
            response.put("status", "requesting");
        }
    }

    private static BuildExecutor defaultExecutor(
            String query,
            String sessionId,
            String agentType,
            Map<String, HistoryManager> historyManagerMap,
            Map<String, BaseAgentBuilder> agentBuilderMap,
            Map<String, Object> modelInfo,
            boolean enableProgress) {
        AgentBuilderExecutor executor = new AgentBuilderExecutor(
                query,
                sessionId,
                agentType,
                historyManagerMap,
                agentBuilderMap,
                modelInfo,
                enableProgress
        );
        return new BuildExecutor() {
            @Override
            public Object execute() {
                return executor.execute();
            }

            @Override
            public Map<String, Object> getBuildStatus() {
                return executor.getBuildStatus();
            }
        };
    }

    @FunctionalInterface
    interface ExecutorFactory {
        BuildExecutor create(
                String query,
                String sessionId,
                String agentType,
                Map<String, HistoryManager> historyManagerMap,
                Map<String, BaseAgentBuilder> agentBuilderMap,
                Map<String, Object> modelInfo,
                boolean enableProgress);
    }

    interface BuildExecutor {
        Object execute();

        Map<String, Object> getBuildStatus();
    }
}
