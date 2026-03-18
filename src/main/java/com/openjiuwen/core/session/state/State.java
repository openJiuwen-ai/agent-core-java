/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.state;

import java.util.Map;

/**
 * Abstract base State interface for session state management.
 * <p>
 * Mirrors Python's {@code State}.
 */
public interface State extends RecoverableState {

    /** Key for global state partition. */
    String GLOBAL_STATE_KEY = "global_state";
    /** Key for io state partition. */
    String IO_STATE_KEY = "io_state";
    /** Key for io state updates. */
    String IO_STATE_UPDATES_KEY = "io_state_updates";
    /** Key for global state updates. */
    String GLOBAL_STATE_UPDATES_KEY = "global_state_updates";
    /** Key for component state partition. */
    String COMP_STATE_KEY = "comp_state";
    /** Key for component state updates. */
    String COMP_STATE_UPDATES_KEY = "comp_state_updates";
    /** Key for workflow state partition. */
    String WORKFLOW_STATE_KEY = "workflow_state";
    /** Key for workflow state updates. */
    String WORKFLOW_STATE_UPDATES_KEY = "workflow_state_updates";
    /** Key for agent state partition. */
    String AGENT_STATE_KEY = "agent_state";
    /** Key for trace state partition. */
    String TRACE_STATE_KEY = "trace_state";
    /** Default node id. */
    String DEFAULT_NODE_ID = "default";
    /** Default workflow id. */
    String DEFAULT_WORKFLOW_ID = "workflow";

    /**
     * Get global state by key.
     */
    Object getGlobal(Object key);

    /**
     * Update global state.
     */
    void updateGlobal(Map<String, Object> data);

    /**
     * Update trace state.
     */
    void updateTrace(Object span);

    /**
     * Update component/local state.
     */
    void update(Map<String, Object> data);

    /**
     * Get component/local state by key.
     */
    Object get(Object key);

    /**
     * Dump full state for debugging.
     */
    Map<String, Object> dump();
}
