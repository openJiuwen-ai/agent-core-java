/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.openjiuwen.core.common.security.JsonUtils;
import com.openjiuwen.dev_tools.agent_builder.executor.HistoryManager;
import com.openjiuwen.dev_tools.agent_builder.utils.BuildProgress;
import com.openjiuwen.dev_tools.agent_builder.utils.ProgressRegistry;
import com.openjiuwen.dev_tools.agent_builder.utils.ProgressReporter;
import com.openjiuwen.dev_tools.agent_builder.utils.ProgressStage;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified Java entry point for agent-builder style flows.
 * <p>
 * This is the Java-side migration baseline: session history, progress, and result normalization
 * are now first-class even before the deeper Python builder pipeline is fully ported.
 * 
 * @since 0.1.7
 */
public class AgentBuilder {
    private final Map<String, Object> modelInfo;
    private final Map<String, HistoryManager> historyManagerMap;
    private final Map<String, AgentBuilderSession> agentBuilderMap;

    /**
     * AgentBuilder.
     * 
     * @since 0.1.7
     */
    public AgentBuilder() {
        this(Map.of(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
    }

    /**
     * AgentBuilder.
     * 
     * @param modelInfo modelInfo
     * @since 0.1.7
     */
    public AgentBuilder(Map<String, Object> modelInfo) {
        this(modelInfo, new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
    }

    /**
     * AgentBuilder.
     * 
     * @param modelInfo modelInfo
     * @param historyManagerMap historyManagerMap
     * @param agentBuilderMap agentBuilderMap
     * @since 0.1.7
     */
    public AgentBuilder(Map<String, Object> modelInfo, Map<String, HistoryManager> historyManagerMap,
            Map<String, AgentBuilderSession> agentBuilderMap) {
        this.modelInfo = modelInfo != null ? new LinkedHashMap<>(modelInfo) : new LinkedHashMap<>();
        this.historyManagerMap = historyManagerMap != null ? historyManagerMap : new ConcurrentHashMap<>();
        this.agentBuilderMap = agentBuilderMap != null ? agentBuilderMap : new ConcurrentHashMap<>();
    }

    /**
     * buildAgent.
     * 
     * @param query query
     * @param sessionId sessionId
     * @param agentType agentType
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> buildAgent(String query, String sessionId, String agentType) {
        String resolvedAgentType = agentType != null ? agentType : "llm_agent";
        HistoryManager historyManager = historyManagerMap.computeIfAbsent(sessionId, ignored -> new HistoryManager());
        AgentBuilderSession session = agentBuilderMap.computeIfAbsent(sessionId,
                ignored -> new AgentBuilderSession(sessionId, resolvedAgentType));

        historyManager.addUserMessage(query);
        session.agentType = resolvedAgentType;
        ProgressReporter reporter = session.progressReporter;
        reporter.startStage(ProgressStage.INITIALIZING, "Initializing builder session",
                Map.of("agent_type", resolvedAgentType), 10.0);
        reporter.completeStage("Session initialized", Map.of("history_size", historyManager.getHistory().size()));
        reporter.startStage(ProgressStage.CLARIFYING, "Analyzing request",
                Map.of("query_length", query != null ? query.length() : 0), 35.0);

        Object executionResult = execute(query, resolvedAgentType, session);
        reporter.completeStage("Request analyzed", Map.of("state", session.state));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", mapStateToStatus(session.state, resolvedAgentType));
        response.put("session_id", sessionId);
        response.put("agent_type", resolvedAgentType);

        if (executionResult instanceof String textResult) {
            Map<String, Object> dsl = tryParseJsonObject(textResult);
            if (!dsl.isEmpty()) {
                response.put("dsl", dsl);
                response.put("status", "completed");
                reporter.startStage(ProgressStage.COMPLETED, "Build completed", Map.of("result_type", "dsl"), 100.0);
                reporter.complete("Build completed");
                session.state = "completed";
            } else if ("workflow".equals(resolvedAgentType) && looksLikeMermaid(textResult)) {
                response.put("mermaid_code", textResult);
                response.put("status", "processing");
                reporter.startStage(ProgressStage.GENERATING_CONFIG, "Workflow draft generated",
                        Map.of("result_type", "mermaid"), 80.0);
                reporter.completeStage("Workflow draft ready", Map.of());
                session.state = "processing";
            } else {
                response.put("response", textResult);
                session.state = "initial";
            }
        } else if (executionResult instanceof Map<?, ?> resultMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = new LinkedHashMap<>((Map<String, Object>) resultMap);
            response.putAll(typed);
            if (typed.containsKey("dsl")) {
                session.state = "completed";
                response.put("status", "completed");
                reporter.startStage(ProgressStage.COMPLETED, "Build completed", Map.of("result_type", "dsl"), 100.0);
                reporter.complete("Build completed");
            }
        } else {
            response.put("response", String.valueOf(executionResult));
        }

        historyManager.addAssistantMessage(extractAssistantMessage(response));
        session.lastUpdate = Instant.now();
        return response;
    }

    /**
     * buildLlmAgent.
     * 
     * @param query query
     * @param sessionId sessionId
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> buildLlmAgent(String query, String sessionId) {
        return buildAgent(query, sessionId, "llm_agent");
    }

    /**
     * buildWorkflow.
     * 
     * @param query query
     * @param sessionId sessionId
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> buildWorkflow(String query, String sessionId) {
        return buildAgent(query, sessionId, "workflow");
    }

    /**
     * getSessionHistory.
     * 
     * @param sessionId sessionId
     * @return the result
     * @since 0.1.7
     */
    public List<Map<String, String>> getSessionHistory(String sessionId) {
        return getSessionHistory(sessionId, null);
    }

    /**
     * getSessionHistory.
     * 
     * @param sessionId sessionId
     * @param k k
     * @return the result
     * @since 0.1.7
     */
    public List<Map<String, String>> getSessionHistory(String sessionId, Integer k) {
        HistoryManager historyManager = historyManagerMap.get(sessionId);
        if (historyManager == null) {
            return List.of();
        }
        return k != null ? historyManager.getLatestKMessages(k) : historyManager.getHistory();
    }

    /**
     * clearSession.
     * 
     * @param sessionId sessionId
     * @since 0.1.7
     */
    public void clearSession(String sessionId) {
        HistoryManager historyManager = historyManagerMap.get(sessionId);
        if (historyManager != null) {
            historyManager.clear();
        }

        AgentBuilderSession session = agentBuilderMap.remove(sessionId);
        if (session != null) {
            ProgressRegistry.remove(sessionId);
            session.reset();
        }
    }

    /**
     * getBuildStatus.
     * 
     * @param sessionId sessionId
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getBuildStatus(String sessionId) {
        AgentBuilderSession session = agentBuilderMap.get(sessionId);
        if (session == null) {
            return Map.of("session_id", sessionId, "state", "not_found");
        }
        return session.toStatusMap(historyManagerMap.get(sessionId));
    }

    /**
     * getProgress.
     * 
     * @param sessionId sessionId
     * @return the result
     * @since 0.1.7
     */
    public static Map<String, Object> getProgress(String sessionId) {
        BuildProgress progress = ProgressRegistry.getProgress(sessionId);
        return progress != null ? progress.toMap() : null;
    }

    /**
     * mapStateToStatus.
     * 
     * @param state state
     * @param agentType agentType
     * @return the result
     * @since 0.1.7
     */
    public static String mapStateToStatus(String state, String agentType) {
        return switch (state) {
            case "initial" -> "llm_agent".equals(agentType) ? "clarifying" : "requesting";
            case "processing" -> "processing";
            case "completed" -> "completed";
            default -> "unknown";
        };
    }

    /**
     * getModelInfo.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getModelInfo() {
        return new LinkedHashMap<>(modelInfo);
    }

    /**
     * getHistoryManagerMap.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, HistoryManager> getHistoryManagerMap() {
        return historyManagerMap;
    }

    /**
     * getAgentBuilderMap.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, AgentBuilderSession> getAgentBuilderMap() {
        return agentBuilderMap;
    }

    /**
     * execute.
     * 
     * @param query query
     * @param agentType agentType
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private Object execute(String query, String agentType, AgentBuilderSession session) {
        String normalized = query != null ? query.trim() : "";
        if (normalized.isBlank()) {
            session.state = "initial";
            return "Please provide the target agent or workflow requirements first.";
        }

        Map<String, Object> parsedJson = tryParseJsonObject(normalized);
        if (!parsedJson.isEmpty()) {
            session.state = "completed";
            return normalized;
        }

        if ("workflow".equals(agentType) && looksLikeMermaid(normalized)) {
            session.state = "processing";
            return normalized;
        }

        if (needsClarification(normalized, agentType)) {
            session.state = "initial";
            return "llm_agent".equals(agentType)
                    ? "Please clarify the agent goal, expected behavior, and any constraints or tools it"
                            + " should use."
                    : "Please clarify the workflow goal, key steps, input/output expectations, and branch"
                            + " conditions.";
        }

        session.state = "completed";
        return Map.of("dsl", buildDsl(normalized, agentType, session.sessionId));
    }

    /**
     * buildDsl.
     * 
     * @param query query
     * @param agentType agentType
     * @param sessionId sessionId
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> buildDsl(String query, String agentType, String sessionId) {
        Map<String, Object> dsl = new LinkedHashMap<>();
        dsl.put("session_id", sessionId);
        dsl.put("agent_type", agentType);
        dsl.put("name", agentType + "_" + sessionId);
        dsl.put("description", query);
        dsl.put("model_info", new LinkedHashMap<>(modelInfo));
        if ("workflow".equals(agentType)) {
            dsl.put("workflow", Map.of("summary", query, "steps", List.of(Map.of("id", "start", "type", "start"),
                    Map.of("id", "main", "type", "task", "description", query), Map.of("id", "end", "type", "end"))));
        }
        return dsl;
    }

    /**
     * needsClarification.
     * 
     * @param query query
     * @param agentType agentType
     * @return the result
     * @since 0.1.7
     */
    private static boolean needsClarification(String query, String agentType) {
        if (query.length() < 24) {
            return true;
        }
        String lower = query.toLowerCase(Locale.ROOT);
        if ("workflow".equals(agentType)) {
            return !(lower.contains("step") || lower.contains("流程") || lower.contains("workflow"));
        }
        return !(lower.contains("agent") || lower.contains("助手") || lower.contains("assistant"));
    }

    /**
     * looksLikeMermaid.
     * 
     * @param text text
     * @return the result
     * @since 0.1.7
     */
    private static boolean looksLikeMermaid(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        return normalized.contains("graph td") || normalized.contains("flowchart");
    }

    @SuppressWarnings("unchecked")
    /**
     * tryParseJsonObject.
     * 
     * @param text text
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> tryParseJsonObject(String text) {
        try {
            return JsonUtils.getMapper().readValue(text, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException ignored) {
            return Map.of();
        }
    }

    /**
     * extractAssistantMessage.
     * 
     * @param response response
     * @return the result
     * @since 0.1.7
     */
    private static String extractAssistantMessage(Map<String, Object> response) {
        if (response.containsKey("response")) {
            return String.valueOf(response.get("response"));
        }
        if (response.containsKey("mermaid_code")) {
            return String.valueOf(response.get("mermaid_code"));
        }
        if (response.containsKey("dsl")) {
            return JsonUtils.safeJsonDumps(response.get("dsl"), "{}");
        }
        return JsonUtils.safeJsonDumps(response, "{}");
    }

    /**
     * AgentBuilderSession.
     * 
     * @since 0.1.7
     */
    public static final class AgentBuilderSession {
        private final String sessionId;
        private String agentType;
        private String state;
        private Instant lastUpdate;
        private final ProgressReporter progressReporter;

        /**
         * AgentBuilderSession.
         * 
         * @param sessionId sessionId
         * @param agentType agentType
         * @since 0.1.7
         */
        private AgentBuilderSession(String sessionId, String agentType) {
            this.sessionId = sessionId;
            this.agentType = agentType;
            this.state = "initial";
            this.lastUpdate = Instant.now();
            this.progressReporter = new ProgressReporter(sessionId, agentType);
            ProgressRegistry.register(sessionId, progressReporter);
        }

        /**
         * toStatusMap.
         * 
         * @param historyManager historyManager
         * @return the result
         * @since 0.1.7
         */
        private Map<String, Object> toStatusMap(HistoryManager historyManager) {
            Map<String, Object> status = new LinkedHashMap<>();
            status.put("session_id", sessionId);
            status.put("agent_type", agentType);
            status.put("state", state);
            status.put("history_size", historyManager != null ? historyManager.getHistory().size() : 0);
            status.put("last_update", lastUpdate.toString());
            return status;
        }

        /**
         * reset.
         * 
         * @since 0.1.7
         */
        private void reset() {
            this.state = "initial";
        }
    }
}
