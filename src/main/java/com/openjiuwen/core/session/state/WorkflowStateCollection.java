/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.state;

import java.util.Map;
import java.util.Optional;

/**
 * State collection for workflow sessions.
 * 
 * <p>Manages IO state, global state, component state, and workflow state
 * with commit/rollback semantics.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class WorkflowStateCollection extends State {
    
    protected final CommitStateLike ioState;
    protected final CommitStateLike globalState;
    protected final CommitStateLike compState;
    protected final CommitStateLike workflowState;
    protected final Map<String, Object> traceState;
    protected final String parentId;
    protected final String nodeId;
    
    /**
     * Creates a new WorkflowStateCollection.
     * 
     * @param ioState the IO state
     * @param globalState the global state
     * @param compState the component state
     * @param workflowState the workflow state
     * @param traceState the trace state
     * @param parentId the parent node ID
     * @param nodeId the current node ID
     */
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
        this.traceState = traceState != null ? traceState : new java.util.HashMap<>();
        this.parentId = parentId != null ? parentId : "";
        this.nodeId = nodeId != null ? nodeId : StateConstants.DEFAULT_NODE_ID;
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
        if (compState == null || data == null) {
            return;
        }
        compState.updateById(nodeId, Map.of(nodeId, data));
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
     * Commits component and IO state for the current node.
     */
    public void commitCmp() {
        compState.commit(nodeId);
        ioState.commit(nodeId);
    }
    
    @Override
    public Map<String, Object> getState() {
        return Map.of(); // Override in subclass
    }
    
    @Override
    public void setState(Map<String, Object> state) {
        // Override in subclass
    }
    
    @Override
    public Map<String, Object> getData() {
        return getState();
    }
}

