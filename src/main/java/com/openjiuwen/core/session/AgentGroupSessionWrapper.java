/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.core.session;

import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.config.DefaultConfig;
import com.openjiuwen.core.session.internal.TaskSession;

import java.util.Map;
import java.util.UUID;

/**
 * Agent group session wrapper for managing agent group execution context.
 * 
 * <p>This class wraps a TaskSession to provide a high-level interface
 * for agent group operations including session ID management and
 * environment variables.
 * 
 * <p>对应 Python: agent-core/openjiuwen/core/session/agent_group.py - Session
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class AgentGroupSessionWrapper {
    
    /**
     * The session ID.
     */
    private final String sessionId;
    
    /**
     * The inner task session.
     */
    private final TaskSession inner;
    
    /**
     * Creates a new AgentGroupSessionWrapper with default values.
     */
    public AgentGroupSessionWrapper() {
        this(null, null);
    }
    
    /**
     * Creates a new AgentGroupSessionWrapper with a session ID.
     * 
     * @param sessionId the session ID (can be null, will generate UUID)
     */
    public AgentGroupSessionWrapper(String sessionId) {
        this(sessionId, null);
    }
    
    /**
     * Creates a new AgentGroupSessionWrapper with all parameters.
     * 
     * @param sessionId the session ID (can be null, will generate UUID)
     * @param envs the environment variables (can be null)
     */
    public AgentGroupSessionWrapper(String sessionId, Map<String, Object> envs) {
        if (sessionId == null) {
            sessionId = UUID.randomUUID().toString();
        }
        this.sessionId = sessionId;
        
        Config config = new DefaultConfig();
        if (envs != null) {
            config.setEnvs(envs);
        }
        
        this.inner = new TaskSession(sessionId, config);
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
     * Gets the inner task session.
     * 
     * @return the inner task session
     */
    public TaskSession getInner() {
        return inner;
    }
    
    /**
     * Factory method to create an agent group session.
     * 
     * @param sessionId the session ID (can be null)
     * @param envs the environment variables (can be null)
     * @return the new agent group session
     */
    public static AgentGroupSessionWrapper createAgentGroupSession(String sessionId, Map<String, Object> envs) {
        return new AgentGroupSessionWrapper(sessionId, envs);
    }
}

