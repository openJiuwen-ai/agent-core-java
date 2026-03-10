/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.state;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Workflow state collection managing io, global, comp, and workflow state partitions.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.state.workflow_state.StateCollection}.
 */
public class WorkflowStateCollection implements State {

    protected final CommitStateLike ioState;
    protected final CommitStateLike globalState;
    protected final CommitStateLike compState;
    protected final CommitStateLike workflowState;
    protected Map<String, Object> traceState;
    protected String parentId;
    protected String nodeId;

    public WorkflowStateCollection(
            CommitStateLike ioState,
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
        Map<String, Object> spanMap = new HashMap<>();
        spanMap.put(nodeId, span);
        traceState.putAll(spanMap);
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

    /**
     * Get workflow-scoped state by key.
     */
    public Object getWorkflow(Object key) {
        if (workflowState == null) {
            return null;
        }
        if (key == null) {
            return workflowState.getState();
        }
        return workflowState.get(key);
    }

    /**
     * Update workflow-scoped state for the current node.
     */
    public void updateWorkflow(Map<String, Object> data) {
        if (workflowState == null || data == null) {
            return;
        }
        workflowState.updateById(nodeId, data);
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

    /**
     * Commit component state.
     */
    public void commitCmp() {
        compState.commit(nodeId);
        ioState.commit(nodeId);
    }

    /**
     * Get inputs by a schema map.
     * Mirrors Python's {@code state.get_inputs(schema)}.
     *
     * @param schema the inputs schema (Map or other)
     * @return the resolved inputs
     */
    @SuppressWarnings("unchecked")
    public Object getInputs(Object schema) {
        if (ioState == null) {
            return null;
        }
        if (schema == null) {
            return ioState.get(nodeId);
        }
        return ioState.getByPrefix(schema, parentId);
    }

    /**
     * Get inputs by a transformer function.
     * Mirrors Python's {@code state.get_inputs_by_transformer(transformer)}.
     *
     * @param transformer a function that transforms state data
     * @return the transformed inputs
     */
    @SuppressWarnings("unchecked")
    public Object getInputsByTransformer(Object transformer) {
        if (transformer instanceof Function) {
            return ((Function<Object, Object>) transformer).apply(dump());
        }
        return null;
    }

    /**
     * Set outputs for the current node.
     * Mirrors Python's {@code state.set_outputs(results)}.
     *
     * @param results the output data to write
     */
    public void setOutputs(Object results) {
        if (ioState == null || results == null) {
            return;
        }
        Map<String, Object> wrappedData = new HashMap<>();
        wrappedData.put(nodeId, results);
        ioState.updateById(nodeId, wrappedData);
    }

    /**
     * Get outputs for a specific node.
     * Mirrors Python's {@code state.get_outputs(node_id)}.
     *
     * @param outputNodeId the node identifier whose outputs to retrieve
     * @return the node's output data
     */
    public Object getOutputs(String outputNodeId) {
        if (ioState == null) {
            return null;
        }
        String targetNodeId = outputNodeId != null ? outputNodeId : nodeId;
        return ioState.getByPrefix(targetNodeId, parentId);
    }

    /**
     * Commit user inputs (input data submitted to the workflow).
     *
     * @param inputs a map of user inputs
     */
    public void commitUserInputs(Map<String, Object> inputs) {
        if (ioState == null || globalState == null || inputs == null) {
            return;
        }
        Object ioData = State.DEFAULT_NODE_ID.equals(nodeId) ? inputs : Map.of(nodeId, inputs);
        ioState.updateById(nodeId, castMap(ioData));
        globalState.updateById(nodeId, inputs);
        commit();
    }

    /**
     * Commit all workflow state partitions.
     */
    public void commit() {
        ioState.commit();
        compState.commit();
        globalState.commit();
        workflowState.commit();
    }

    /**
     * Create a node-scoped state sharing the same underlying partitions.
     * Mirrors Python's {@code create_node_state(executable_id, parent_id)}.
     */
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

    @Override
    public Map<String, Object> getState() {
        return new HashMap<>();
    }

    @Override
    public void setState(Map<String, Object> state) {
        // Default no-op; overridden by CommitState
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object obj) {
        return (Map<String, Object>) obj;
    }
}
