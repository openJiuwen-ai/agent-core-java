/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder;

import com.openjiuwen.core.common.security.JsonUtils;
import com.openjiuwen.dev_tools.agent_builder.builders.AgentBuilderFactory;
import com.openjiuwen.dev_tools.agent_builder.builders.BaseAgentBuilder;
import com.openjiuwen.dev_tools.agent_builder.executor.AgentBuildExecutor;
import com.openjiuwen.dev_tools.agent_builder.executor.HistoryManager;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderEnums;
import com.openjiuwen.dev_tools.agent_builder.utils.ProgressReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Unified Agent Builder Entry Point.
 * <p>
 * Provides simple interface for building LLM Agent and Workflow Agent.
 * Manages session state and builder instances internally.
 * <p>
 * Mirrors Python's {@code AgentBuilder} in
 * {@code openjiuwen.dev_tools.agent_builder.main}.
 */
public class AgentBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(AgentBuilder.class);

    private final Map<String, Object> modelInfo;
    private final Map<String, HistoryManager> historyManagerMap;
    private final Map<String, BaseAgentBuilder> agentBuilderMap;

    /**
     * Initialize builder with default settings.
     */
    public AgentBuilder() {
        this(new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    public AgentBuilder(Map<String, Object> modelInfo) {
        this(modelInfo, new HashMap<>(), new HashMap<>());
    }

    /**
     * Initialize builder.
     *
     * @param modelInfo          LLM model configuration
     * @param historyManagerMap  Session history manager map (optional, for cross-instance reuse)
     * @param agentBuilderMap    Builder map (optional, for cross-instance reuse)
     */
    public AgentBuilder(
            Map<String, Object> modelInfo,
            Map<String, HistoryManager> historyManagerMap,
            Map<String, BaseAgentBuilder> agentBuilderMap) {
        this.modelInfo = modelInfo != null ? modelInfo : new HashMap<>();
        this.historyManagerMap = historyManagerMap != null ? historyManagerMap : new HashMap<>();
        this.agentBuilderMap = agentBuilderMap != null ? agentBuilderMap : new HashMap<>();
    }

    /**
     * Build agent (unified interface).
     *
     * @param query      User query
     * @param sessionId  Session ID
     * @param agentType  Agent type ('llm_agent' or 'workflow')
     * @return Build result dict containing status and corresponding data
     */
    public Map<String, Object> buildAgent(String query, String sessionId, String agentType) {
        // Get or create history manager for this session
        HistoryManager historyManager = historyManagerMap.computeIfAbsent(sessionId,
                k -> new HistoryManager());

        // Get or create builder for this session
        BaseAgentBuilder builder = agentBuilderMap.get(sessionId);
        if (builder == null) {
            // Create builder based on agent type
            AgentBuilderEnums.AgentType type = "workflow".equals(agentType)
                    ? AgentBuilderEnums.AgentType.WORKFLOW
                    : AgentBuilderEnums.AgentType.LLM_AGENT;
            builder = AgentBuilderFactory.create(type);
            agentBuilderMap.put(sessionId, builder);
        }

        AgentBuildExecutor executor = new AgentBuildExecutor(builder, historyManager);

        Map<String, Object> queryMap = new LinkedHashMap<>();
        queryMap.put("query", query);
        queryMap.put("session_id", sessionId);
        queryMap.put("agent_type", agentType);
        queryMap.put("model_info", modelInfo);

        Map<String, Object> result = executor.execute(queryMap);

        Map<String, Object> response = new LinkedHashMap<>();
        AgentBuilderEnums.BuildState state = builder.getState();
        String stateStr = state != null ? state.getValue() : "unknown";
        response.put("status", mapStateToStatus(stateStr, agentType));
        response.put("session_id", sessionId);
        response.put("agent_type", agentType);

        // Merge result data into response
        if (result != null) {
            Object resultData = result.get("result");
            if (resultData instanceof String resultStr) {
                try {
                    Map<String, Object> dsl = JsonUtils.safeJsonLoads(resultStr, Map.class);
                    response.put("dsl", dsl);
                    response.put("status", "completed");
                } catch (Exception e) {
                    if ("workflow".equals(agentType)
                            && (resultStr.contains("graph") || resultStr.contains("flowchart"))) {
                        response.put("mermaid_code", resultData);
                        response.put("status", "processing");
                    } else {
                        response.put("response", resultData);
                        response.put("status", "llm_agent".equals(agentType) ? "clarifying" : "requesting");
                    }
                }
            } else if (resultData instanceof Map<?, ?> resultMap) {
                resultMap.forEach((key, value) -> response.put(String.valueOf(key), value));
                response.put("status", "completed");
            }
            response.putAll(result);
        }

        return response;
    }

    /**
     * Build LLM Agent.
     *
     * @param query     User query
     * @param sessionId Session ID
     * @return Build result
     */
    public Map<String, Object> buildLlmAgent(String query, String sessionId) {
        return buildAgent(query, sessionId, "llm_agent");
    }

    /**
     * Build Workflow Agent.
     *
     * @param query     User query
     * @param sessionId Session ID
     * @return Build result
     */
    public Map<String, Object> buildWorkflow(String query, String sessionId) {
        return buildAgent(query, sessionId, "workflow");
    }

    /**
     * Map internal state to external status.
     *
     * @param state     Internal state
     * @param agentType Agent type
     * @return External status string
     */
    public static String mapStateToStatus(String state, String agentType) {
        return switch (state) {
            case "initial" -> "llm_agent".equals(agentType) ? "clarifying" : "requesting";
            case "processing" -> "processing";
            case "completed" -> "completed";
            default -> "unknown";
        };
    }

    public java.util.List<Map<String, Object>> getSessionHistory(String sessionId) {
        return getSessionHistory(sessionId, null);
    }

    public java.util.List<Map<String, Object>> getSessionHistory(String sessionId, Integer k) {
        HistoryManager historyManager = historyManagerMap.get(sessionId);
        if (historyManager == null) {
            return java.util.List.of();
        }
        if (k != null) {
            return historyManager.getRecent(k);
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
            return Map.of("session_id", sessionId, "state", "not_found");
        }
        Map<String, Object> status = new LinkedHashMap<>(builder.getBuildStatus());
        status.put("session_id", sessionId);
        return status;
    }

    public static Map<String, Object> getProgress(String sessionId) {
        return ProgressReporter.getProgress(sessionId);
    }

    /**
     * Get model info.
     *
     * @return Model info map
     */
    public Map<String, Object> getModelInfo() {
        return modelInfo;
    }

    /**
     * Get history manager map.
     *
     * @return History manager map
     */
    public Map<String, HistoryManager> getHistoryManagerMap() {
        return historyManagerMap;
    }

    /**
     * Get agent builder map.
     *
     * @return Agent builder map
     */
    public Map<String, BaseAgentBuilder> getAgentBuilderMap() {
        return agentBuilderMap;
    }
}
