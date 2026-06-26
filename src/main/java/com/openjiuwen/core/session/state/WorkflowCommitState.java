/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.session.state;

import java.util.HashMap;
import java.util.Map;

/**
 * Workflow commit state with commit, rollback, and node-state creation helpers.
 * <p>
 * Mirrors Python's {@code CommitState} in
 * {@code openjiuwen/core/session/state/workflow_state.py}.
 * </p>
 */
public class WorkflowCommitState extends WorkflowStateCollection {

    public WorkflowCommitState(CommitStateLike ioState,
                               CommitStateLike globalState,
                               CommitStateLike compState,
                               CommitStateLike workflowState,
                               Map<String, Object> traceState,
                               String parentId,
                               String nodeId) {
        super(ioState, globalState, compState, workflowState, traceState, parentId, nodeId);
    }

    public Object getWorkflowState(Object key) {
        if (workflowState == null || key == null) {
            return null;
        }
        return workflowState.get(key);
    }

    public void updateAndCommitWorkflowState(Map<String, Object> data) {
        if (workflowState == null || data == null) {
            return;
        }
        workflowState.updateById(DEFAULT_WORKFLOW_ID, data);
        workflowState.commit(null);
    }

    public void setOutputs(Map<String, Object> data) {
        if (ioState == null || data == null) {
            return;
        }
        Map<String, Object> wrappedData = new HashMap<>();
        wrappedData.put(nodeId, data);
        ioState.updateById(nodeId, wrappedData);
    }

    public Object getInputs(Object schema) {
        if (ioState == null) {
            return null;
        }
        if (schema == null) {
            return ioState.get(nodeId);
        }
        return ioState.getByPrefix(schema, parentId);
    }

    public Object getOutputs(String targetNodeId) {
        if (ioState == null) {
            return null;
        }
        String actualNodeId = targetNodeId != null ? targetNodeId : nodeId;
        return ioState.getByPrefix(actualNodeId, parentId);
    }

    public void commitUserInputs(Object inputs) {
        if (ioState == null || globalState == null || inputs == null) {
            return;
        }
        Object ioData = DEFAULT_NODE_ID.equals(nodeId) ? inputs : Map.of(nodeId, inputs);
        ioState.updateById(nodeId, castMap(ioData));
        globalState.updateById(nodeId, castMap(inputs));
        commit();
    }

    public void commit() {
        ioState.commit(null);
        compState.commit(null);
        globalState.commit(null);
        workflowState.commit(null);
    }

    public void rollback() {
        compState.rollback(nodeId);
        ioState.rollback(nodeId);
        globalState.rollback(nodeId);
        workflowState.rollback(nodeId);
    }

    @Override
    public Map<String, Object> getState() {
        Map<String, Object> state = new HashMap<>();
        state.put(IO_STATE_KEY, ioState.getState());
        state.put(GLOBAL_STATE_KEY, globalState.getState());
        state.put(COMP_STATE_KEY, compState.getState());
        state.put(WORKFLOW_STATE_KEY, workflowState.getState());
        return state;
    }

    @Override
    public void setState(Map<String, Object> state) {
        if (state == null) {
            return;
        }
        Object global = state.get(GLOBAL_STATE_KEY);
        if (global instanceof Map<?, ?> globalMap) {
            globalState.setState(castMap(globalMap));
        }
        Object io = state.get(IO_STATE_KEY);
        if (io instanceof Map<?, ?> ioMap) {
            ioState.setState(castMap(ioMap));
        }
        Object comp = state.get(COMP_STATE_KEY);
        if (comp instanceof Map<?, ?> compMap) {
            compState.setState(castMap(compMap));
        }
        Object workflow = state.get(WORKFLOW_STATE_KEY);
        if (workflow instanceof Map<?, ?> workflowMap) {
            workflowState.setState(castMap(workflowMap));
        }
    }

    public Map<String, Object> getUpdates() {
        Map<String, Object> updates = new HashMap<>();
        updates.put(IO_STATE_UPDATES_KEY, ioState.getUpdates());
        updates.put(GLOBAL_STATE_UPDATES_KEY, globalState.getUpdates());
        updates.put(COMP_STATE_UPDATES_KEY, compState.getUpdates());
        updates.put(WORKFLOW_STATE_UPDATES_KEY, workflowState.getUpdates());
        return updates;
    }

    public void setUpdates(Map<String, Object> updates) {
        if (updates == null) {
            return;
        }
        Object globalUpdates = updates.get(GLOBAL_STATE_UPDATES_KEY);
        if (globalUpdates instanceof Map<?, ?> globalMap) {
            globalState.setUpdates(castMap(globalMap));
        }
        Object ioUpdates = updates.get(IO_STATE_UPDATES_KEY);
        if (ioUpdates instanceof Map<?, ?> ioMap) {
            ioState.setUpdates(castMap(ioMap));
        }
        Object compUpdates = updates.get(COMP_STATE_UPDATES_KEY);
        if (compUpdates instanceof Map<?, ?> compMap) {
            compState.setUpdates(castMap(compMap));
        }
        Object workflowUpdates = updates.get(WORKFLOW_STATE_UPDATES_KEY);
        if (workflowUpdates instanceof Map<?, ?> workflowMap) {
            workflowState.setUpdates(castMap(workflowMap));
        }
    }

    public WorkflowCommitState createNodeState(String newNodeId, String newParentId) {
        return new WorkflowCommitState(
                ioState,
                globalState,
                compState,
                workflowState,
                traceState,
                newParentId,
                newNodeId
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }
}
