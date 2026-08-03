/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.session.state;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Workflow state collection managing io, global, comp, and workflow partitions.
 * <p>
 * Mirrors Python's {@code StateCollection} in
 * {@code openjiuwen/core/session/state/workflow_state.py}.
 * </p>
 */
public class WorkflowStateCollection implements State {

    protected final CommitStateLike ioState;
    protected final CommitStateLike globalState;
    protected final CommitStateLike compState;
    protected final CommitStateLike workflowState;
    protected Map<String, Object> traceState;
    protected String parentId;
    protected String nodeId;

    public WorkflowStateCollection(CommitStateLike ioState,
                                   CommitStateLike globalState,
                                   CommitStateLike compState,
                                   CommitStateLike workflowState,
                                   Map<String, Object> traceState,
                                   String parentId,
                                   String nodeId) {
        this.ioState = ioState;
        this.globalState = globalState;
        this.compState = compState;
        this.workflowState = workflowState;
        this.traceState = traceState != null ? traceState : new HashMap<>();
        this.parentId = parentId != null ? parentId : "";
        this.nodeId = nodeId != null ? nodeId : State.DEFAULT_NODE_ID;
    }

    @Override
    public Object getGlobal(Object key) {
        if (globalState == null || key == null) {
            return null;
        }
        Object result = globalState.get(key);
        if (result == null) {
            result = ioState.getByPrefix(key, parentId);
        }
        if (result == null) {
            result = ioState.getByPrefix(key, nodeId);
        }
        return result;
    }

    @Override
    public void updateGlobal(Map<String, Object> data) {
        if (globalState == null || data == null) {
            return;
        }
        globalState.updateById(nodeId, data);
    }

    @Override
    public void updateTrace(Object span) {
        traceState.put(nodeId, span);
    }

    @Override
    public void update(Map<String, Object> data) {
        if (compState == null) {
            return;
        }
        Map<String, Object> wrappedData = new HashMap<>();
        wrappedData.put(nodeId, data);
        compState.updateById(nodeId, wrappedData);
    }

    @Override
    public Object get(Object key) {
        if (compState == null) {
            return null;
        }
        if (key == null) {
            return compState.get(nodeId);
        }
        return compState.getByPrefix(key, nodeId);
    }

    @Override
    public Map<String, Object> dump() {
        Map<String, Object> result = new HashMap<>();
        result.put("io_state", ioState.getState());
        result.put("io_state_updates", ioState.getUpdates());
        result.put("global_state", globalState.getState());
        result.put("global_state_updates", globalState.getUpdates());
        result.put("comp_state", compState.getState());
        result.put("comp_state_updates", compState.getUpdates());
        result.put("workflow_state", workflowState.getState());
        result.put("workflow_state_updates", workflowState.getUpdates());
        result.put("trace_state", traceState);
        return result;
    }

    @Override
    public Map<String, Object> getState() {
        Map<String, Object> state = new HashMap<>();
        state.put(IO_STATE_KEY, ioState == null ? null : ioState.getState());
        state.put(GLOBAL_STATE_KEY, globalState == null ? null : globalState.getState());
        state.put(COMP_STATE_KEY, compState == null ? null : compState.getState());
        state.put(WORKFLOW_STATE_KEY, workflowState == null ? null : workflowState.getState());
        return state;
    }

    @Override
    public void setState(Map<String, Object> state) {
        if (state == null) {
            return;
        }
        applyState(globalState, state.get(GLOBAL_STATE_KEY));
        applyState(ioState, state.get(IO_STATE_KEY));
        applyState(compState, state.get(COMP_STATE_KEY));
        applyState(workflowState, state.get(WORKFLOW_STATE_KEY));
    }

    public void commitCmp() {
        compState.commit(nodeId);
        ioState.commit(nodeId);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getInputsByTransformer(Object transformer) {
        if (ioState == null || !(transformer instanceof Function<?, ?> function)) {
            return Map.of();
        }
        return (Map<String, Object>) ((Function<Object, Object>) function).apply(ioState.getState());
    }

    @SuppressWarnings("unchecked")
    private void applyState(CommitStateLike targetState, Object stateValue) {
        if (targetState != null && stateValue instanceof Map<?, ?> map) {
            targetState.setState((Map<String, Object>) map);
        }
    }
}
