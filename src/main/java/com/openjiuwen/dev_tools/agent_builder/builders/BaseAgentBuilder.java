/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders;

import com.openjiuwen.dev_tools.agent_builder.executor.HistoryManager;
import com.openjiuwen.dev_tools.agent_builder.resource.ResourceRetriever;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderEnums;
import com.openjiuwen.dev_tools.agent_builder.utils.ProgressReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Base agent builder defining unified build flow and interface.
 * <p>
 * Uses template method pattern to define build flow framework.
 * <p>
 * Mirrors Python's {@code BaseAgentBuilder} in
 * {@code openjiuwen.dev_tools.agent_builder.builders.base}.
 */
public abstract class BaseAgentBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(BaseAgentBuilder.class);

    protected AgentBuilderEnums.BuildState state = AgentBuilderEnums.BuildState.INITIAL;
    protected ProgressReporter progressReporter;
    protected final Object llm;
    protected final HistoryManager historyManager;
    protected final ResourceRetriever retriever;
    protected final Map<String, Object> resource = new LinkedHashMap<>();

    protected BaseAgentBuilder(ProgressReporter progressReporter) {
        this(null, new HistoryManager(), progressReporter);
    }

    protected BaseAgentBuilder(Object llm, HistoryManager historyManager, ProgressReporter progressReporter) {
        this.llm = llm;
        this.historyManager = historyManager != null ? historyManager : new HistoryManager();
        this.retriever = new ResourceRetriever(llm);
        this.progressReporter = progressReporter;
    }

    public Object getLlm() {
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

    public ProgressReporter getProgressReporter() {
        return progressReporter;
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

    public void reset() {
        state = AgentBuilderEnums.BuildState.INITIAL;
        resource.clear();
    }

    public Map<String, Object> getBuildStatus() {
        Map<String, Object> resourceCount = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : resource.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Collection<?> collection) {
                resourceCount.put(entry.getKey(), collection.size());
            } else if (value instanceof Map<?, ?> map) {
                resourceCount.put(entry.getKey(), map.size());
            } else {
                resourceCount.put(entry.getKey(), 1);
            }
        }

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("state", state.getValue());
        status.put("resource_count", resourceCount);
        return status;
    }

    /**
     * Python-compatible execution entry point.
     */
    public Map<String, Object> execute(String query) {
        return build(Map.of("query", query), historyManager.getHistory());
    }

    /**
     * Main build entry point — template method.
     */
    public Map<String, Object> build(Map<String, Object> query, List<Map<String, Object>> history) {
        LOG.info("[AgentBuilder] Build started, state={}", state);
        switch (state) {
            case INITIAL:
                return handleInitial(query, history);
            case PROCESSING:
                return handleProcessing(query, history);
            case COMPLETED:
                return handleCompleted(query, history);
            default:
                throw new IllegalStateException("Unknown build state: " + state);
        }
    }

    // Abstract methods for subclasses
    protected abstract Map<String, Object> handleInitial(Map<String, Object> query, List<Map<String, Object>> history);
    protected abstract Map<String, Object> handleProcessing(Map<String, Object> query, List<Map<String, Object>> history);

    protected Map<String, Object> handleCompleted(Map<String, Object> query, List<Map<String, Object>> history) {
        return handleCompleted();
    }

    protected Map<String, Object> handleCompleted() {
        return Map.of("status", "completed", "state", "completed");
    }
}
