/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders;

import com.openjiuwen.core.common.exception.ApplicationError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.dev_tools.agent_builder.executor.HistoryManager;
import com.openjiuwen.dev_tools.agent_builder.resource.ResourceRetriever;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderEnums;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderUtils;
import com.openjiuwen.dev_tools.agent_builder.utils.ProgressReporter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Base agent builder defining the unified build flow and interface.
 *
 * <p>Mirrors Python's {@code BaseAgentBuilder} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/base.py}.</p>
 */
public abstract class BaseAgentBuilder {
    private static final LoggerProtocol LOGGER = LogManager.getLogger("agent_builder");
    private static final String RESOURCE_ID = "resource_id";

    private final Model llm;
    private final HistoryManager historyManager;
    private final ResourceRetriever retriever;
    private AgentBuilderEnums.BuildState state;
    private final Map<String, Object> resource;
    private ProgressReporter progressReporter;

    protected BaseAgentBuilder(Model llm, HistoryManager historyManager) {
        this(llm, historyManager, null);
    }

    protected BaseAgentBuilder(Model llm, HistoryManager historyManager, ProgressReporter progressReporter) {
        this.llm = Objects.requireNonNull(llm, "llm");
        this.historyManager = Objects.requireNonNull(historyManager, "historyManager");
        this.retriever = new ResourceRetriever(llm);
        this.resource = new LinkedHashMap<>();
        this.state = AgentBuilderEnums.BuildState.INITIAL;
        this.progressReporter = progressReporter;
    }

    public Model getLlm() {
        return llm;
    }

    public HistoryManager getHistoryManager() {
        return historyManager;
    }

    public AgentBuilderEnums.BuildState getState() {
        return state;
    }

    public void setState(AgentBuilderEnums.BuildState state) {
        this.state = state;
    }

    public Map<String, Object> getResource() {
        return resource;
    }

    public void setResource(Map<String, Object> resource) {
        this.resource.clear();
        if (resource != null) {
            this.resource.putAll(resource);
        }
    }

    public ProgressReporter getProgressReporter() {
        return progressReporter;
    }

    public void setProgressReporter(ProgressReporter progressReporter) {
        this.progressReporter = progressReporter;
    }

    /**
     * Execute the template build flow.
     *
     * @param query user query.
     * @return an intermediate string result or a final DSL map, matching Python's union return.
     */
    public Object execute(String query) {
        String safeQuery = query == null ? "" : query;
        LOGGER.debug(
                "Starting build flow state={} query_length={}",
                stateValue(),
                safeQuery.length());

        try {
            if (progressReporter != null) {
                progressReporter.startStage(
                        AgentBuilderEnums.ProgressStage.INITIALIZING,
                        "Starting build flow...",
                        Map.of("state", stateValue()));
            }

            List<Map<String, String>> dialogHistory = historyManager.getHistory();
            if (progressReporter != null) {
                progressReporter.startStage(
                        AgentBuilderEnums.ProgressStage.RESOURCE_RETRIEVING,
                        "Retrieving relevant resources...",
                        Map.of("dialog_length", dialogHistory.size()));
            }

            updateResource(dialogHistory);
            if (progressReporter != null) {
                progressReporter.completeStage(
                        "Resource retrieval completed",
                        Map.of("resource_count", resourceCount()));
            }

            Object result;
            if (state == AgentBuilderEnums.BuildState.INITIAL) {
                result = handleInitial(safeQuery, dialogHistory);
            } else if (state == AgentBuilderEnums.BuildState.PROCESSING) {
                result = handleProcessing(safeQuery, dialogHistory);
            } else if (state == AgentBuilderEnums.BuildState.COMPLETED) {
                result = handleCompleted(safeQuery, dialogHistory);
            } else {
                String errorMessage = "Unknown build state: " + state;
                LOGGER.error("Unknown build state state={}", String.valueOf(state));
                if (progressReporter != null) {
                    progressReporter.failStage(errorMessage, "Build state error");
                }
                throw new ApplicationError(
                        StatusCode.LLM_AGENT_STATE_ERROR,
                        errorMessage,
                        null,
                        null,
                        Map.of("error_msg", errorMessage));
            }

            LOGGER.debug(
                    "Build flow completed state={} result_type={}",
                    stateValue(),
                    result == null ? "null" : result.getClass().getSimpleName());

            if (progressReporter != null && state == AgentBuilderEnums.BuildState.COMPLETED) {
                progressReporter.complete("Build completed");
            }
            return result;
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Build flow failed state={} error={} error_type={}",
                    stateValue(),
                    exceptionMessage(exception),
                    exception.getClass().getSimpleName());
            if (progressReporter != null) {
                progressReporter.failStage(
                        exceptionMessage(exception),
                        "Build failed",
                        Map.of("error_type", exception.getClass().getSimpleName()));
            }
            throw exception;
        }
    }

    /**
     * Python-compatible resource refresh hook.
     */
    protected void updateResource(List<Map<String, String>> dialogHistory) {
        try {
            Map<String, Object> newResource = retriever.retrieve(toWildcardList(dialogHistory), isWorkflowBuilder());
            for (Map.Entry<String, Object> entry : newResource.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                Object existing = resource.get(key);
                if (!resource.containsKey(key)) {
                    resource.put(key, value);
                } else if (existing instanceof List<?> existingList && value instanceof List<?> valueList) {
                    resource.put(key, mergeResourceLists(existingList, valueList, RESOURCE_ID));
                } else {
                    resource.put(key, value);
                }
            }

            LOGGER.debug("Resource update completed resource_keys={}", resource.keySet());
        } catch (RuntimeException exception) {
            LOGGER.warning(
                    "Resource update failed, continuing with existing resources error={}",
                    exceptionMessage(exception));
        }
    }

    protected List<Map<String, Object>> mergeResourceLists(
            List<?> existing,
            List<?> newItems,
            String uniqueKey) {
        return AgentBuilderUtils.mergeDictLists(toMapList(existing), toMapList(newItems), uniqueKey);
    }

    public void reset() {
        state = AgentBuilderEnums.BuildState.INITIAL;
        resource.clear();
        resetInternalState();
        LOGGER.debug("Builder state has been reset");
    }

    public Map<String, Object> getBuildStatus() {
        Map<String, Object> resourceCount = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : resource.entrySet()) {
            Object value = entry.getValue();
            resourceCount.put(entry.getKey(), value instanceof List<?> list ? list.size() : 1);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("state", stateValue());
        result.put("resource_count", resourceCount);
        return result;
    }

    public Map<String, Object> get_build_status() {
        return getBuildStatus();
    }

    public boolean isWorkflowBuilder() {
        return isWorkflowBuilderInternal();
    }

    public boolean is_workflow_builder() {
        return isWorkflowBuilder();
    }

    protected abstract Object handleInitial(String query, List<Map<String, String>> dialogHistory);

    protected abstract Object handleProcessing(String query, List<Map<String, String>> dialogHistory);

    protected abstract Object handleCompleted(String query, List<Map<String, String>> dialogHistory);

    protected abstract void resetInternalState();

    protected abstract boolean isWorkflowBuilderInternal();

    private Map<String, Object> resourceCount() {
        Map<String, Object> counts = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : resource.entrySet()) {
            Object value = entry.getValue();
            counts.put(entry.getKey(), value instanceof List<?> list ? list.size() : 1);
        }
        return counts;
    }

    private String stateValue() {
        return state == null ? "null" : state.getValue();
    }

    private static List<Map<String, ?>> toWildcardList(List<Map<String, String>> source) {
        List<Map<String, ?>> result = new ArrayList<>();
        for (Map<String, String> item : source) {
            result.add(item);
        }
        return result;
    }

    private static List<Map<String, Object>> toMapList(List<?> values) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> converted = new LinkedHashMap<>();
                map.forEach((key, item) -> converted.put(String.valueOf(key), item));
                result.add(converted);
            }
        }
        return result;
    }

    private static String exceptionMessage(Throwable exception) {
        Throwable effective = exception;
        if (effective instanceof java.util.concurrent.CompletionException && effective.getCause() != null) {
            effective = effective.getCause();
        }
        String message = effective.getMessage();
        return message == null ? effective.toString() : message;
    }
}
