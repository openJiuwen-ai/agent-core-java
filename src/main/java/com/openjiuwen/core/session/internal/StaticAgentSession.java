/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.internal;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.SessionModule;
import com.openjiuwen.core.session.callback.CallbackManager;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.config.DefaultConfig;
import com.openjiuwen.core.session.state.State;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import com.openjiuwen.core.session.tracer.Tracer;

import java.util.concurrent.CompletableFuture;

/**
 * Static agent session for creating agent sessions.
 * 
 * <p>Provides a lightweight session implementation that can create
 * full AgentSession instances. Most methods return null as this is
 * primarily a factory for creating agent sessions.
 * 
 * <p>对应 Python: agent-core/openjiuwen/core/session/internal/agent.py - StaticAgentSession
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class StaticAgentSession implements BaseSession {
    
    private final Config config;
    private final Checkpointer checkpointer;
    
    /**
     * Creates a new StaticAgentSession with default configuration.
     */
    public StaticAgentSession() {
        this(null);
    }
    
    /**
     * Creates a new StaticAgentSession with the given configuration.
     * 
     * @param config the configuration (can be null)
     */
    public StaticAgentSession(Config config) {
        this.config = config != null ? config : new DefaultConfig();
        this.checkpointer = SessionModule.getDefaultInMemoryCheckpointer();
    }
    
    @Override
    public Config getConfig() {
        return config;
    }
    
    @Override
    public Checkpointer getCheckpointer() {
        return checkpointer;
    }
    
    @Override
    public State getState() {
        return null;
    }
    
    @Override
    public Tracer getTracer() {
        return null;
    }
    
    @Override
    public StreamWriterManager getStreamWriterManager() {
        return null;
    }
    
    @Override
    public CallbackManager getCallbackManager() {
        return null;
    }
    
    @Override
    public String getSessionId() {
        return null;
    }
    
    /**
     * Creates a new agent session.
     * 
     * <p>This method creates an AgentSession and calls pre_agent_execute on the checkpointer.
     * 
     * @param sessionId the session ID
     * @param inputs the inputs for the agent (can be null)
     * @return a CompletableFuture that completes with the new AgentSession
     */
    public CompletableFuture<BaseSession> createAgentSession(String sessionId, Object inputs) {
        AgentSession session = new AgentSession(sessionId, config, checkpointer);
        return checkpointer.preAgentExecute(session, inputs)
            .thenApply(v -> session);
    }
    
    /**
     * Creates a new agent session without inputs.
     * 
     * @param sessionId the session ID
     * @return a CompletableFuture that completes with the new AgentSession
     */
    public CompletableFuture<BaseSession> createAgentSession(String sessionId) {
        return createAgentSession(sessionId, null);
    }
}

