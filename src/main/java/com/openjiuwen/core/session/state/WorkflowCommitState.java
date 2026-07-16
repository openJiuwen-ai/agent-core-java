/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.state;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Workflow commit state with full commit/rollback and node state creation.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.state.workflow_state.CommitState}.
 * 
 * @since 0.1.7
 */
public class WorkflowCommitState extends WorkflowStateCollection {
    private Map<String, Object> snapshot;

    /**
     * WorkflowCommitState.
     * 
     * @param ioState ioState
     * @param globalState globalState
     * @param compState compState
     * @param workflowState workflowState
     * @param traceState traceState
     * @param parentId parentId
     * @param nodeId nodeId
     * @since 0.1.7
     */
    public WorkflowCommitState(CommitStateLike ioState, CommitStateLike globalState, CommitStateLike compState,
            CommitStateLike workflowState, Map<String, Object> traceState, String parentId, String nodeId) {
        super(ioState, globalState, compState, workflowState, traceState, parentId, nodeId);
        this.snapshot = new LinkedHashMap<>();
    }

    /**
     * Commit all state partitions.
     * Passes null to commit ALL pending updates from all node IDs,
     * matching the Python behavior where commit() defaults to node_id=None.
     * 
     * @since 0.1.7
     */
    public void commit() {
        ioState.commit(null);
        globalState.commit(null);
        compState.commit(null);
        workflowState.commit(null);
    }

    /**
     * Commit component and IO state for the current node.
     * Mirrors Python's {@code commit_cmp()}.
     * 
     * @since 0.1.7
     */
    public void commitCmp() {
        compState.commit(nodeId);
        ioState.commit(nodeId);
    }

    /**
     * Commit workflow-scoped state for the current node.
     * 
     * @since 0.1.7
     */
    public void commitWorkflow() {
        workflowState.commit(nodeId);
    }

    /**
     * Update and immediately commit workflow-scoped state.
     * 
     * @param data data
     * @since 0.1.7
     */
    public void updateAndCommitWorkflowState(Map<String, Object> data) {
        if (data == null) {
            return;
        }
        workflowState.updateById(nodeId, data);
        workflowState.commit(nodeId);
    }

    /**
     * Rollback all state partitions.
     * 
     * @since 0.1.7
     */
    public void rollback() {
        ioState.rollback(nodeId);
        globalState.rollback(nodeId);
        compState.rollback(nodeId);
        workflowState.rollback(nodeId);
    }

    /**
     * getState.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Map<String, Object> getState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put(IO_STATE_KEY, ioState.getState());
        state.put(GLOBAL_STATE_KEY, globalState.getState());
        state.put(COMP_STATE_KEY, compState.getState());
        state.put(WORKFLOW_STATE_KEY, workflowState.getState());
        state.put(TRACE_STATE_KEY, new LinkedHashMap<>(traceState));
        return state;
    }

    /**
     * setState.
     * 
     * @param state state
     * @since 0.1.7
     */
    @Override
    public void setState(Map<String, Object> state) {
        if (state == null) {
            return;
        }
        Object io = state.get(IO_STATE_KEY);
        if (io instanceof Map) {
            ioState.setState(castMap(io));
        }
        Object global = state.get(GLOBAL_STATE_KEY);
        if (global instanceof Map) {
            globalState.setState(castMap(global));
        }
        Object comp = state.get(COMP_STATE_KEY);
        if (comp instanceof Map) {
            compState.setState(castMap(comp));
        }
        Object workflow = state.get(WORKFLOW_STATE_KEY);
        if (workflow instanceof Map) {
            workflowState.setState(castMap(workflow));
        }
        Object trace = state.get(TRACE_STATE_KEY);
        if (trace instanceof Map) {
            traceState = new LinkedHashMap<>(castMap(trace));
        }
    }

    /**
     * Create a node state for the given node ID.
     * 
     * @param newNodeId the node identifier
     * @param newParentId newParentId
     * @return a new WorkflowStateCollection for the node
     * @since 0.1.7
     */
    public WorkflowCommitState createNodeState(String newNodeId, String newParentId) {
        return new WorkflowCommitState(ioState, globalState, compState, workflowState, traceState, newParentId,
                newNodeId);
    }

    /**
     * Backward-compatible overload for tests and callers that only provide node id.
     * 
     * @param newNodeId newNodeId
     * @return the result
     * @since 0.1.7
     */
    public WorkflowCommitState createNodeState(String newNodeId) {
        return createNodeState(newNodeId, parentId);
    }

    /**
     * Get IO state.
     * 
     * @return the io state
     * @since 0.1.7
     */
    public CommitStateLike getIoState() {
        return ioState;
    }

    /**
     * Get global state.
     * 
     * @return the global state
     * @since 0.1.7
     */
    public CommitStateLike getGlobalState() {
        return globalState;
    }

    /**
     * Get component state.
     * 
     * @return the component state
     * @since 0.1.7
     */
    public CommitStateLike getCompState() {
        return compState;
    }

    /**
     * Get workflow state.
     * 
     * @return the workflow state
     * @since 0.1.7
     */
    public CommitStateLike getWorkflowState() {
        return workflowState;
    }

    /**
     * Get trace state.
     * 
     * @return the trace state
     * @since 0.1.7
     */
    public Map<String, Object> getTraceState() {
        return traceState;
    }

    /**
     * Get pending updates for all partitions.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getUpdates() {
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(IO_STATE_UPDATES_KEY, ioState.getUpdates());
        updates.put(GLOBAL_STATE_UPDATES_KEY, globalState.getUpdates());
        updates.put(COMP_STATE_UPDATES_KEY, compState.getUpdates());
        updates.put(WORKFLOW_STATE_UPDATES_KEY, workflowState.getUpdates());
        return updates;
    }

    /**
     * Restore pending updates for all partitions.
     * 
     * @param updates updates
     * @since 0.1.7
     */
    public void setUpdates(Map<String, Object> updates) {
        if (updates == null) {
            return;
        }

        Object ioUpdates = updates.get(IO_STATE_UPDATES_KEY);
        if (ioUpdates instanceof Map) {
            ioState.setUpdates(castMap(ioUpdates));
        }

        Object globalUpdates = updates.get(GLOBAL_STATE_UPDATES_KEY);
        if (globalUpdates instanceof Map) {
            globalState.setUpdates(castMap(globalUpdates));
        }

        Object compUpdates = updates.get(COMP_STATE_UPDATES_KEY);
        if (compUpdates instanceof Map) {
            compState.setUpdates(castMap(compUpdates));
        }

        Object workflowUpdates = updates.get(WORKFLOW_STATE_UPDATES_KEY);
        if (workflowUpdates instanceof Map) {
            workflowState.setUpdates(castMap(workflowUpdates));
        }
    }

    @SuppressWarnings("unchecked")
    /**
     * castMap.
     * 
     * @param obj obj
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> castMap(Object obj) {
        return (Map<String, Object>) obj;
    }
}
