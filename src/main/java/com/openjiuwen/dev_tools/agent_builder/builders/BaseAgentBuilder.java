/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders;

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
    protected final Map<String, Object> resource = new LinkedHashMap<>();

    protected BaseAgentBuilder(ProgressReporter progressReporter) {
        this.progressReporter = progressReporter != null ? progressReporter : new ProgressReporter();
    }

    public AgentBuilderEnums.BuildState getState() {
        return state;
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
                return handleCompleted();
            default:
                throw new IllegalStateException("Unknown build state: " + state);
        }
    }

    // Abstract methods for subclasses
    protected abstract Map<String, Object> handleInitial(Map<String, Object> query, List<Map<String, Object>> history);
    protected abstract Map<String, Object> handleProcessing(Map<String, Object> query, List<Map<String, Object>> history);

    protected Map<String, Object> handleCompleted() {
        return Map.of("status", "completed", "state", "completed");
    }
}
