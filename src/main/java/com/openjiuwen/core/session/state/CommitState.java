/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.state;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * State with commit/rollback semantics for workflow execution.
 * 
 * <p>Extends WorkflowStateCollection with workflow state management,
 * input/output handling, and full commit/rollback support.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class CommitState extends WorkflowStateCollection {
    
    private final boolean workflowOnly;
    
    /**
     * Creates a new CommitState.
     * 
     * @param ioState the IO state
     * @param globalState the global state
     * @param compState the component state
     * @param workflowState the workflow state
     * @param traceState the trace state
     * @param parentId the parent node ID
     * @param nodeId the current node ID
     * @param workflowOnly whether this is workflow-only mode
     */
    public CommitState(
            CommitStateLike ioState,
            CommitStateLike globalState,
            CommitStateLike compState,
            CommitStateLike workflowState,
            Map<String, Object> traceState,
            String parentId,
            String nodeId,
            boolean workflowOnly) {
        super(ioState, globalState, compState, workflowState, traceState, parentId, nodeId);
        this.workflowOnly = workflowOnly;
    }
    
    /**
     * Creates a new CommitState with workflowOnly=true.
     */
    public CommitState(
            CommitStateLike ioState,
            CommitStateLike globalState,
            CommitStateLike compState,
            CommitStateLike workflowState,
            Map<String, Object> traceState,
            String parentId,
            String nodeId) {
        this(ioState, globalState, compState, workflowState, traceState, parentId, nodeId, true);
    }
    
    /**
     * Gets a value from workflow state.
     * 
     * @param key the key
     * @return the value, or null if not found
     */
    public Object getWorkflowState(Object key) {
        if (workflowState == null || key == null) {
            return null;
        }
        return workflowState.get(key);
    }
    
    /**
     * Updates and commits workflow state.
     * 
     * @param data the data to update
     */
    public void updateAndCommitWorkflowState(Map<String, Object> data) {
        workflowState.updateById(StateConstants.DEFAULT_WORKFLOW_ID, data);
        workflowState.commit();
    }
    
    /**
     * Sets outputs for the current node.
     * 
     * @param data the output data
     */
    public void setOutputs(Map<String, Object> data) {
        if (ioState == null || data == null) {
            return;
        }
        ioState.updateById(nodeId, Map.of(nodeId, data));
    }
    
    /**
     * Gets inputs using a schema.
     * 
     * @param schema the schema, or null to get all inputs for current node
     * @return the inputs
     */
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
     * Gets outputs for a specific node.
     * 
     * @param outputNodeId the node ID
     * @return the outputs
     */
    public Object getOutputs(String outputNodeId) {
        if (ioState == null) {
            return null;
        }
        return ioState.getByPrefix(outputNodeId, parentId);
    }
    
    /**
     * Gets inputs using a transformer.
     * 
     * @param <T> the result type
     * @param transformer the transformer
     * @return the transformed inputs
     */
    public <T> T getInputsByTransformer(Transformer<T> transformer) {
        if (ioState == null) {
            return null;
        }
        return ioState.getByTransformer(transformer);
    }
    
    /**
     * Commits user inputs.
     * 
     * @param inputs the user inputs
     */
    public void commitUserInputs(Object inputs) {
        if (ioState == null || inputs == null) {
            return;
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> inputMap = (Map<String, Object>) inputs;
        
        if (!StateConstants.DEFAULT_NODE_ID.equals(nodeId)) {
            ioState.updateById(nodeId, Map.of(nodeId, inputs));
        } else {
            ioState.updateById(nodeId, inputMap);
        }
        globalState.updateById(nodeId, inputMap);
        commit();
    }
    
    /**
     * Commits all state changes.
     */
    public void commit() {
        ioState.commit();
        compState.commit();
        globalState.commit();
        workflowState.commit();
    }
    
    /**
     * Rolls back all state changes for the current node.
     */
    public void rollback() {
        compState.rollback(nodeId);
        ioState.rollback(nodeId);
        globalState.rollback(nodeId);
        workflowState.rollback(nodeId);
    }
    
    @Override
    public Map<String, Object> getState() {
        Map<String, Object> result = new HashMap<>();
        result.put(StateConstants.IO_STATE_KEY, ioState.getState());
        result.put(StateConstants.GLOBAL_STATE_KEY, workflowOnly ? globalState.getState() : null);
        result.put(StateConstants.COMP_STATE_KEY, compState.getState());
        result.put(StateConstants.WORKFLOW_STATE_KEY, workflowState.getState());
        return result;
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public void setState(Map<String, Object> state) {
        if (state == null) {
            return;
        }
        
        Map<String, Object> globalStateData = (Map<String, Object>) state.get(StateConstants.GLOBAL_STATE_KEY);
        if (globalStateData != null) {
            globalState.setState(globalStateData);
        }
        
        ioState.setState((Map<String, Object>) state.get(StateConstants.IO_STATE_KEY));
        compState.setState((Map<String, Object>) state.get(StateConstants.COMP_STATE_KEY));
        workflowState.setState((Map<String, Object>) state.get(StateConstants.WORKFLOW_STATE_KEY));
    }
    
    /**
     * Gets all pending updates.
     * 
     * @return the updates map
     */
    public Map<String, Object> getUpdates() {
        Map<String, Object> result = new HashMap<>();
        result.put(StateConstants.IO_STATE_UPDATES_KEY, ioState.getUpdates());
        result.put(StateConstants.GLOBAL_STATE_UPDATES_KEY, workflowOnly ? globalState.getUpdates() : null);
        result.put(StateConstants.COMP_STATE_UPDATES_KEY, compState.getUpdates());
        result.put(StateConstants.WORKFLOW_STATE_UPDATES_KEY, workflowState.getUpdates());
        return result;
    }
    
    /**
     * Sets pending updates.
     * 
     * @param updates the updates to set
     */
    @SuppressWarnings("unchecked")
    public void setUpdates(Map<String, Object> updates) {
        if (updates == null) {
            return;
        }
        
        Object globalUpdates = updates.get(StateConstants.GLOBAL_STATE_UPDATES_KEY);
        if (globalUpdates != null) {
            globalState.setUpdates((Map<String, List<Map<String, Object>>>) globalUpdates);
        }
        
        Object ioUpdates = updates.get(StateConstants.IO_STATE_UPDATES_KEY);
        if (ioUpdates != null) {
            ioState.setUpdates((Map<String, List<Map<String, Object>>>) ioUpdates);
        }
        
        Object compUpdates = updates.get(StateConstants.COMP_STATE_UPDATES_KEY);
        if (compUpdates != null) {
            compState.setUpdates((Map<String, List<Map<String, Object>>>) compUpdates);
        }
        
        Object workflowUpdates = updates.get(StateConstants.WORKFLOW_STATE_UPDATES_KEY);
        if (workflowUpdates != null) {
            workflowState.setUpdates((Map<String, List<Map<String, Object>>>) workflowUpdates);
        }
    }
    
    /**
     * Creates a new node state for a child node.
     * 
     * @param childNodeId the child node ID
     * @param childParentId the parent ID for the child
     * @return a new CommitState for the child node
     */
    @Override
    public State createNodeState(String childNodeId, String childParentId) {
        return new CommitState(
            ioState, globalState, compState, workflowState,
            traceState, childParentId, childNodeId, workflowOnly);
    }
    
    @Override
    public Map<String, Object> getData() {
        return getState();
    }
}

