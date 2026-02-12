/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.internal;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.WorkflowSessionWrapper;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.config.DefaultConfig;
import com.openjiuwen.core.session.interaction.SimpleAgentInteraction;
import com.openjiuwen.core.session.tracer.Tracer;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Task session for managing task execution with agent capabilities.
 * 
 * <p>Extends StateSession to provide task-specific functionality including
 * interaction handling, stream iteration, and workflow session creation.
 * 
 * <p>对应 Python: agent-core/openjiuwen/core/session/internal/wrapper.py - TaskSession
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class TaskSession extends StateSession {
    
    private SimpleAgentInteraction interaction;
    
    /**
     * Creates a new TaskSession with default configuration.
     */
    public TaskSession() {
        this(null, null, null, null);
    }
    
    /**
     * Creates a new TaskSession with a session ID.
     * 
     * @param sessionId the session ID
     */
    public TaskSession(String sessionId) {
        this(sessionId, null, null, null);
    }
    
    /**
     * Creates a new TaskSession with a session ID and config.
     * 
     * @param sessionId the session ID
     * @param config the configuration
     */
    public TaskSession(String sessionId, Config config) {
        this(sessionId, config, null, null);
    }
    
    /**
     * Creates a new TaskSession with all parameters.
     * 
     * @param sessionId the session ID
     * @param config the configuration (can be null)
     * @param resourceMgr the resource manager (can be null, passed as checkpointer)
     * @param card the agent card (can be null)
     */
    public TaskSession(String sessionId, Config config, Object resourceMgr, Object card) {
        super(createAgentSession(sessionId, config, resourceMgr, card));
        this.interaction = null;
    }
    
    /**
     * Creates an AgentSession for the TaskSession.
     */
    private static AgentSession createAgentSession(String sessionId, Config config, 
                                                    Object resourceMgr, Object card) {
        Config effectiveConfig = config != null ? config : new DefaultConfig();
        // Note: resourceMgr is passed as checkpointer in Python, but the types don't match exactly
        // In Java, we'll use the default checkpointer
        return new AgentSession(sessionId, effectiveConfig, null, card);
    }
    
    @Override
    public CompletableFuture<Void> trace(Map<String, Object> data) {
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Void> traceError(Exception error) {
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Void> interact(Object value) {
        if (interaction == null) {
            if (inner instanceof AgentSession agentSession) {
                interaction = new SimpleAgentInteraction(agentSession);
            } else {
                return CompletableFuture.failedFuture(
                    new IllegalStateException("Inner session is not an AgentSession"));
            }
        }
        
        String message = value instanceof String str ? str : (value != null ? value.toString() : "");
        return interaction.waitUserInputs(message).thenApply(v -> null);
    }
    
    /**
     * Gets the inner session.
     * 
     * @return the inner session
     */
    public BaseSession getInnerSession() {
        return inner;
    }
    
    /**
     * Gets the stream output iterator.
     * 
     * @return an iterable of stream data
     */
    public Iterable<Object> streamIterator() {
        if (inner.getStreamWriterManager() != null) {
            return inner.getStreamWriterManager().streamOutput();
        }
        return java.util.Collections.emptyList();
    }
    
    @Override
    public CompletableFuture<Void> postRun() {
        if (inner instanceof AgentSession agentSession) {
            return agentSession.getStreamWriterManager().streamEmitter().close()
                .thenCompose(v -> agentSession.getCheckpointer().postAgentExecute(agentSession));
        }
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Gets the tracer.
     * 
     * @return the tracer
     */
    public Tracer getTracer() {
        return inner.getTracer();
    }
    
    /**
     * Gets the environment variables.
     * 
     * <p>对应 Python: getattr(self._inner.config(), "_env")
     * 
     * @return the environment variables map
     */
    public Map<String, Object> getEnvs() {
        if (inner.getConfig() != null) {
            return inner.getConfig().getEnvMap();
        }
        return null;
    }
    
    /**
     * Creates a workflow session from this task session.
     * 
     * @return the new workflow session
     */
    public WorkflowSessionWrapper createWorkflowSession() {
        return new WorkflowSessionWrapper(this, getSessionId(), null);
    }
    
    /**
     * Gets the session ID (alias for getSessionId).
     * 
     * @return the session ID
     */
    public String getSessionIdValue() {
        return getSessionId();
    }
    
    // Note: Resource manager methods (getPrompt, getModel, getWorkflow, getTool) are marked as
    // "todo: all resource interface will be deleted when resource_mgr supports tag feature"
    // in the Python code, so we don't implement them here.
}

