/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder;

import com.openjiuwen.core.common.utils.JsonUtils;
import com.openjiuwen.dev_tools.agent_builder.builders.BaseAgentBuilder;
import com.openjiuwen.dev_tools.agent_builder.executor.AgentBuildExecutor;
import com.openjiuwen.dev_tools.agent_builder.executor.HistoryManager;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderEnums;
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
        AgentBuildExecutor executor = new AgentBuildExecutor(
                query,
                sessionId,
                agentType,
                historyManagerMap,
                agentBuilderMap,
                modelInfo,
                true
        );

        Object result = executor.execute();

        Map<String, Object> statusInfo = executor.getBuildStatus();
        String state = (String) statusInfo.getOrDefault("state", "unknown");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", mapStateToStatus(state, agentType));
        response.put("session_id", sessionId);
        response.put("agent_type", agentType);

        if (result instanceof String) {
            try {
                Map<String, Object> dsl = JsonUtils.parseJsonToMap((String) result);
                response.put("dsl", dsl);
                response.put("status", "completed");
            } catch (Exception e) {
                if ("llm_agent".equals(agentType)) {
                    response.put("response", result);
                    response.put("status", "clarifying");
                } else if ("workflow".equals(agentType)) {
                    String resultStr = (String) result;
                    if (resultStr.contains("graph") || resultStr.contains("flowchart")) {
                        response.put("mermaid_code", result);
                        response.put("status", "processing");
                    } else {
                        response.put("response", result);
                        response.put("status", "requesting");
                    }
                }
            }
        } else if (result instanceof Map) {
            response.putAll((Map<String, Object>) result);
        } else {
            response.put("response", String.valueOf(result));
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
    private static String mapStateToStatus(String state, String agentType) {
        if ("completed".equals(state)) {
            return "completed";
        } else if ("processing".equals(state) || "initial".equals(state)) {
            return "processing";
        } else if ("error".equals(state)) {
            return "error";
        }
        return "unknown";
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