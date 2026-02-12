/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session;

import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.config.DefaultConfig;
import com.openjiuwen.core.session.internal.TaskSession;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Agent session wrapper for managing agent execution context.
 * 
 * <p>This class wraps a TaskSession to provide a high-level interface
 * for agent operations including session ID management, environment
 * variables, stream writing, and workflow session creation.
 * 
 * <p>对应 Python: agent-core/openjiuwen/core/session/agent.py - Session
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class AgentSessionWrapper {
    
    /**
     * The session ID.
     */
    private final String sessionId;
    
    /**
     * The inner task session.
     */
    private final TaskSession inner;
    
    /**
     * The agent card (metadata about the agent).
     */
    private final Object card;
    
    /**
     * Creates a new AgentSessionWrapper with default values.
     */
    public AgentSessionWrapper() {
        this(null, null, null);
    }
    
    /**
     * Creates a new AgentSessionWrapper with a session ID.
     * 
     * @param sessionId the session ID (can be null, will generate UUID)
     */
    public AgentSessionWrapper(String sessionId) {
        this(sessionId, null, null);
    }
    
    /**
     * Creates a new AgentSessionWrapper with a session ID and environment variables.
     * 
     * @param sessionId the session ID (can be null, will generate UUID)
     * @param envs the environment variables (can be null)
     */
    public AgentSessionWrapper(String sessionId, Map<String, Object> envs) {
        this(sessionId, envs, null);
    }
    
    /**
     * Creates a new AgentSessionWrapper with all parameters.
     * 
     * @param sessionId the session ID (can be null, will generate UUID)
     * @param envs the environment variables (can be null)
     * @param card the agent card (can be null)
     */
    public AgentSessionWrapper(String sessionId, Map<String, Object> envs, Object card) {
        if (sessionId == null) {
            sessionId = UUID.randomUUID().toString();
        }
        this.sessionId = sessionId;
        
        Config config = new DefaultConfig();
        if (envs != null) {
            config.setEnvs(envs);
        }
        
        this.inner = new TaskSession(sessionId, config, null, card);
        this.card = card;
    }
    
    /**
     * Gets the session ID.
     * 
     * @return the session ID
     */
    public String getSessionId() {
        return sessionId;
    }
    
    /**
     * Gets the environment variables.
     * 
     * @return the environment variables map
     */
    public Map<String, Object> getEnvs() {
        return inner.getEnvs();
    }
    
    /**
     * Gets the agent ID from the card.
     * 
     * @return the agent ID, or null if card is not set
     */
    public String getAgentId() {
        if (card == null) {
            return null;
        }
        try {
            var method = card.getClass().getMethod("getId");
            Object result = method.invoke(card);
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Gets the agent name from the card.
     * 
     * @return the agent name, or null if card is not set
     */
    public String getAgentName() {
        if (card == null) {
            return null;
        }
        try {
            var method = card.getClass().getMethod("getName");
            Object result = method.invoke(card);
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Gets the agent description from the card.
     * 
     * @return the agent description, or null if card is not set
     */
    public String getAgentDescription() {
        if (card == null) {
            return null;
        }
        try {
            var method = card.getClass().getMethod("getDescription");
            Object result = method.invoke(card);
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Writes data to the output stream.
     * 
     * @param data the data to write (can be a Map or OutputSchema)
     * @return a CompletableFuture that completes when writing is done
     */
    public CompletableFuture<Void> writeStream(Object data) {
        return inner.writeStream(data);
    }
    
    /**
     * Writes data to the custom stream.
     * 
     * @param data the data to write
     * @return a CompletableFuture that completes when writing is done
     */
    public CompletableFuture<Void> writeCustomStream(Map<String, Object> data) {
        return inner.writeCustomStream(data);
    }
    
    /**
     * Gets the stream output iterator.
     * 
     * @return an iterator of stream data
     */
    public Iterator<Object> streamIterator() {
        return inner.streamIterator().iterator();
    }
    
    /**
     * Performs post-run cleanup.
     * 
     * @return a CompletableFuture that completes when cleanup is done
     */
    public CompletableFuture<Void> postRun() {
        return inner.postRun();
    }
    
    /**
     * Creates a workflow session from this agent session.
     * 
     * @return the new workflow session
     */
    public WorkflowSessionWrapper createWorkflowSession() {
        return new WorkflowSessionWrapper(this, getSessionId(), null);
    }
    
    /**
     * Handles user interaction.
     * 
     * @param value the interaction value
     * @return a CompletableFuture that completes when interaction is done
     */
    public CompletableFuture<Void> interact(Object value) {
        return inner.interact(value);
    }
    
    /**
     * Gets the inner task session.
     * 
     * @return the inner task session
     */
    public TaskSession getInner() {
        return inner;
    }
    
    /**
     * Factory method to create an agent session.
     * 
     * @param sessionId the session ID (can be null)
     * @param envs the environment variables (can be null)
     * @param card the agent card (can be null)
     * @return the new agent session
     */
    public static AgentSessionWrapper createAgentSession(String sessionId, Map<String, Object> envs, Object card) {
        return new AgentSessionWrapper(sessionId, envs, card);
    }
}

