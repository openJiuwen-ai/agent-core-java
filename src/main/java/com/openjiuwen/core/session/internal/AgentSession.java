/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.internal;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.SessionModule;
import com.openjiuwen.core.session.callback.CallbackManager;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.state.AgentStateCollection;
import com.openjiuwen.core.session.state.InMemoryCommitState;
import com.openjiuwen.core.session.state.InMemoryWorkflowState;
import com.openjiuwen.core.session.state.State;
import com.openjiuwen.core.session.stream.StreamEmitter;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import com.openjiuwen.core.session.tracer.Span;
import com.openjiuwen.core.session.tracer.Tracer;

/**
 * Agent session implementation for managing agent execution context.
 * 
 * <p>Provides full session management including state, tracer, stream writing,
 * callback management, and checkpointing for agent execution.
 * 
 * <p>对应 Python: agent-core/openjiuwen/core/session/internal/agent.py - AgentSession
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class AgentSession implements BaseSession {
    
    private final String sessionId;
    private final Config config;
    private final AgentStateCollection state;
    private final StreamWriterManager streamWriterManager;
    private final CallbackManager callbackManager;
    private final Tracer tracer;
    private final Checkpointer checkpointer;
    private final Span agentSpan;
    private final Object card;
    
    /**
     * Creates a new AgentSession with a session ID.
     * 
     * @param sessionId the session ID
     */
    public AgentSession(String sessionId) {
        this(sessionId, null, null, null);
    }
    
    /**
     * Creates a new AgentSession with a session ID and config.
     * 
     * @param sessionId the session ID
     * @param config the configuration
     */
    public AgentSession(String sessionId, Config config) {
        this(sessionId, config, null, null);
    }
    
    /**
     * Creates a new AgentSession with a session ID, config, and checkpointer.
     * 
     * @param sessionId the session ID
     * @param config the configuration
     * @param checkpointer the checkpointer
     */
    public AgentSession(String sessionId, Config config, Checkpointer checkpointer) {
        this(sessionId, config, checkpointer, null);
    }
    
    /**
     * Creates a new AgentSession with all parameters.
     * 
     * @param sessionId the session ID
     * @param config the configuration (can be null)
     * @param checkpointer the checkpointer (can be null)
     * @param card the agent card (can be null)
     */
    public AgentSession(String sessionId, Config config, Checkpointer checkpointer, Object card) {
        this.sessionId = sessionId;
        this.config = config;
        this.state = new AgentStateCollection();
        this.streamWriterManager = new StreamWriterManager(new StreamEmitter());
        this.callbackManager = new CallbackManager();
        
        // Initialize tracer
        Tracer tracerInstance = new Tracer();
        tracerInstance.init(this.streamWriterManager, this.callbackManager);
        this.tracer = tracerInstance;
        
        // Use default checkpointer if not provided
        this.checkpointer = checkpointer != null ? checkpointer : SessionModule.getDefaultInMemoryCheckpointer();
        
        // Create agent span
        this.agentSpan = this.tracer != null 
            ? this.tracer.getTracerAgentSpanManager().createAgentSpan() 
            : null;
        
        this.card = card;
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
    
    /**
     * Gets the agent span.
     * 
     * @return the agent span
     */
    public Span getSpan() {
        return agentSpan;
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
        return checkpointer;
    }
    
    /**
     * Creates a workflow session from this agent session.
     * 
     * <p>The workflow session inherits the global state from this agent session.
     * 
     * @return the new workflow session
     */
    public WorkflowSession createWorkflowSession() {
        // Create InMemoryWorkflowState with the global state wrapped in InMemoryCommitState
        InMemoryWorkflowState workflowState = new InMemoryWorkflowState(
            new InMemoryCommitState(state.getGlobalStateInstance())
        );
        
        return new WorkflowSession("", this, sessionId, workflowState, null);
    }
    
    /**
     * Gets the agent ID.
     * 
     * <p>First tries to get the ID from the agent config, then falls back to the card.
     * 
     * @return the agent ID, or null if not available
     */
    public String getAgentId() {
        Object agentConfig = config != null ? config.getAgentConfig() : null;
        if (agentConfig != null) {
            try {
                var idMethod = agentConfig.getClass().getMethod("getId");
                Object id = idMethod.invoke(agentConfig);
                if (id != null) {
                    return id.toString();
                }
            } catch (Exception e) {
                // Try 'id' field directly
                try {
                    var idField = agentConfig.getClass().getField("id");
                    Object id = idField.get(agentConfig);
                    if (id != null) {
                        return id.toString();
                    }
                } catch (Exception ignored) {
                    // Ignore
                }
            }
        }
        
        // Fall back to card
        if (card != null) {
            try {
                var idMethod = card.getClass().getMethod("getId");
                Object id = idMethod.invoke(card);
                if (id != null) {
                    return id.toString();
                }
            } catch (Exception e) {
                // Try 'id' field directly
                try {
                    var idField = card.getClass().getField("id");
                    Object id = idField.get(card);
                    if (id != null) {
                        return id.toString();
                    }
                } catch (Exception ignored) {
                    // Ignore
                }
            }
        }
        
        return null;
    }
    
    /**
     * Gets the agent card.
     * 
     * @return the agent card
     */
    public Object getCard() {
        return card;
    }
}

