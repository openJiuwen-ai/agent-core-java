/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.internal;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.callback.CallbackManager;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.config.DefaultConfig;
import com.openjiuwen.core.session.state.InMemoryWorkflowState;
import com.openjiuwen.core.session.state.State;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import com.openjiuwen.core.session.tracer.Tracer;
import com.openjiuwen.core.session.tracer.TracerWorkflowUtils;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Workflow session implementation for managing workflow execution context.
 * 
 * <p>Implements both BaseSession and TracerWorkflowUtils.WorkflowSession interfaces
 * to provide workflow-specific session management with tracing support.
 * 
 * <p>对应 Python: agent-core/openjiuwen/core/session/internal/workflow.py - WorkflowSession
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class WorkflowSession implements BaseSession, TracerWorkflowUtils.WorkflowSession {
    
    private final String sessionId;
    private final BaseSession parent;
    private final Config config;
    private Tracer tracer;
    private final State state;
    private final CallbackManager callbackManager;
    private StreamWriterManager streamWriterManager;
    private Object actorManager;
    private String workflowId;
    
    /**
     * Creates a new WorkflowSession with default values.
     */
    public WorkflowSession() {
        this("", null, null, null, null);
    }
    
    /**
     * Creates a new WorkflowSession with a workflow ID.
     * 
     * @param workflowId the workflow ID
     */
    public WorkflowSession(String workflowId) {
        this(workflowId, null, null, null, null);
    }
    
    /**
     * Creates a new WorkflowSession with a parent session.
     * 
     * @param workflowId the workflow ID
     * @param parent the parent session
     */
    public WorkflowSession(String workflowId, BaseSession parent) {
        this(workflowId, parent, null, null, null);
    }
    
    /**
     * Creates a new WorkflowSession with all parameters.
     * 
     * @param workflowId the workflow ID
     * @param parent the parent session
     * @param sessionId the session ID (can be null)
     * @param state the state (can be null)
     * @param callbackManager the callback manager (can be null)
     */
    public WorkflowSession(String workflowId, BaseSession parent, String sessionId, 
                           State state, CallbackManager callbackManager) {
        this.parent = parent;
        this.workflowId = workflowId != null ? workflowId : "";
        
        if (parent != null) {
            this.sessionId = sessionId != null ? sessionId : parent.getSessionId();
            this.config = parent.getConfig();
            this.tracer = parent.getTracer();
        } else {
            this.sessionId = sessionId != null ? sessionId : UUID.randomUUID().toString().replace("-", "");
            this.config = new DefaultConfig();
            this.tracer = null;
        }
        
        this.state = state != null ? state : new InMemoryWorkflowState();
        this.callbackManager = callbackManager != null ? callbackManager : new CallbackManager();
        this.streamWriterManager = null;
        this.actorManager = null;
    }
    
    /**
     * Sets the stream writer manager.
     * 
     * <p>Once set, the stream writer manager cannot be changed.
     * 
     * @param streamWriterManager the stream writer manager
     */
    public void setStreamWriterManager(StreamWriterManager streamWriterManager) {
        if (this.streamWriterManager != null) {
            return;
        }
        this.streamWriterManager = streamWriterManager;
    }
    
    /**
     * Sets the tracer.
     * 
     * @param tracer the tracer
     */
    public void setTracer(Tracer tracer) {
        this.tracer = tracer;
    }
    
    /**
     * Sets the actor manager.
     * 
     * <p>Once set, the actor manager cannot be changed.
     * 
     * @param actorManager the actor manager
     */
    public void setActorManager(Object actorManager) {
        if (this.actorManager != null) {
            return;
        }
        this.actorManager = actorManager;
    }
    
    /**
     * Sets the workflow ID.
     * 
     * @param workflowId the workflow ID
     */
    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }
    
    @Override
    public Object getActorManager() {
        return actorManager;
    }
    
    @Override
    public Config getConfig() {
        return config;
    }
    
    @Override
    public State getState() {
        return state;
    }
    
    @Override
    public Tracer getTracer() {
        return tracer;
    }
    
    @Override
    public StreamWriterManager getStreamWriterManager() {
        return streamWriterManager;
    }
    
    @Override
    public CallbackManager getCallbackManager() {
        return callbackManager;
    }
    
    @Override
    public String getSessionId() {
        return sessionId;
    }
    
    @Override
    public Checkpointer getCheckpointer() {
        if (parent != null) {
            return parent.getCheckpointer();
        }
        return null;
    }
    
    /**
     * Gets the workflow ID.
     * 
     * @return the workflow ID
     */
    public String getWorkflowId() {
        return workflowId;
    }
    
    /**
     * Gets the main workflow ID.
     * 
     * @return the main workflow ID (same as workflow ID for top-level workflows)
     */
    public String getMainWorkflowId() {
        return getWorkflowId();
    }
    
    /**
     * Gets the workflow nesting depth.
     * 
     * @return 0 for top-level workflows
     */
    public int getWorkflowNestingDepth() {
        return 0;
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
    
    /**
     * Gets the parent session.
     * 
     * @return the parent session
     */
    public BaseSession getParent() {
        return parent;
    }
    
    // ========== TracerWorkflowUtils.WorkflowSession interface methods ==========
    
    @Override
    public Tracer tracer() {
        return getTracer();
    }
    
    @Override
    public String workflowId() {
        return getWorkflowId();
    }
    
    @Override
    public String executableId() {
        // For WorkflowSession, executable ID is the workflow ID
        return getWorkflowId();
    }
    
    @Override
    public String parentId() {
        // Top-level workflow has no parent
        return "";
    }
    
    @Override
    public String nodeId() {
        // WorkflowSession doesn't have a node ID
        return "";
    }
    
    @Override
    public String nodeType() {
        // WorkflowSession doesn't have a node type
        return "";
    }
    
    @Override
    public Object state() {
        return getState();
    }
    
    @Override
    public TracerWorkflowUtils.WorkflowConfig config() {
        return workflowId -> getConfig().getWorkflowConfig(workflowId);
    }
}

