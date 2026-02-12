/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.internal;

import java.util.concurrent.CompletableFuture;

/**
 * Sub-workflow session for managing nested workflow execution.
 * 
 * <p>Extends NodeSession to provide sub-workflow specific functionality
 * including workflow nesting depth tracking and independent actor management.
 * 
 * <p>对应 Python: agent-core/openjiuwen/core/session/internal/workflow.py - SubWorkflowSession
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class SubWorkflowSession extends NodeSession {
    
    private final Object actorManager;
    
    /**
     * Creates a new SubWorkflowSession.
     * 
     * @param session the parent NodeSession
     * @param workflowId the sub-workflow ID
     */
    public SubWorkflowSession(NodeSession session, String workflowId) {
        this(session, workflowId, null);
    }
    
    /**
     * Creates a new SubWorkflowSession with an actor manager.
     * 
     * @param session the parent NodeSession
     * @param workflowId the sub-workflow ID
     * @param actorManager the actor manager (can be null)
     */
    public SubWorkflowSession(NodeSession session, String workflowId, Object actorManager) {
        // Call parent constructor with the parent's parent session, node ID, and node type
        super(session.getParent(), session.getNodeId(), session.getNodeType());
        
        // Override workflow-related fields
        this.workflowId = workflowId;
        this.workflowNestingDepth = session.getWorkflowNestingDepth() + 1;
        this.mainWorkflowId = session.getMainWorkflowId();
        this.actorManager = actorManager;
    }
    
    @Override
    public String getWorkflowId() {
        return workflowId;
    }
    
    @Override
    public int getWorkflowNestingDepth() {
        return workflowNestingDepth;
    }
    
    @Override
    public String getMainWorkflowId() {
        return mainWorkflowId;
    }
    
    @Override
    public Object getActorManager() {
        return actorManager;
    }
    
    @Override
    public CompletableFuture<Void> close() {
        if (actorManager != null) {
            try {
                // Try to call shutdown() method on actor manager
                var method = actorManager.getClass().getMethod("shutdown");
                Object result = method.invoke(actorManager);
                if (result instanceof CompletableFuture<?> future) {
                    return future.thenApply(v -> null);
                }
            } catch (Exception e) {
                // Ignore reflection errors
            }
        }
        return CompletableFuture.completedFuture(null);
    }
    
    // Override TracerWorkflowUtils.WorkflowSession methods to use overridden values
    
    @Override
    public String workflowId() {
        return getWorkflowId();
    }
}

