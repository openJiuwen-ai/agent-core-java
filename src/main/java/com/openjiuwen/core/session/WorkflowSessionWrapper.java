/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session;

import com.openjiuwen.core.session.callback.CallbackManager;

import java.util.Map;
import java.util.UUID;

/**
 * Workflow session wrapper for managing workflow execution context.
 * 
 * <p>This class manages basic session information including session ID,
 * environment variables, callback manager, and workflow card.
 * 
 * <p>对应 Python: agent-core/openjiuwen/core/session/workflow.py - Session
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class WorkflowSessionWrapper {
    
    /**
     * Environment variables for the session.
     */
    private Map<String, Object> envs;
    
    /**
     * Callback manager for handling callbacks.
     */
    private final CallbackManager callbackManager;
    
    /**
     * Parent session (typically an agent session).
     */
    private final Object parent;
    
    /**
     * Session identifier.
     */
    private final String sessionId;
    
    /**
     * Workflow card metadata.
     */
    private Object workflowCard;
    
    /**
     * Creates a new WorkflowSessionWrapper with default values.
     */
    public WorkflowSessionWrapper() {
        this(null, null, null);
    }
    
    /**
     * Creates a new WorkflowSessionWrapper with a parent session.
     * 
     * @param parent the parent session
     */
    public WorkflowSessionWrapper(Object parent) {
        this(parent, null, null);
    }
    
    /**
     * Creates a new WorkflowSessionWrapper with a session ID.
     * 
     * @param sessionId the session ID
     */
    public WorkflowSessionWrapper(String sessionId) {
        this(null, sessionId, null);
    }
    
    /**
     * Creates a new WorkflowSessionWrapper with all parameters.
     * 
     * @param parent the parent session (typically an agent session)
     * @param sessionId the session ID (can be null)
     * @param envs the environment variables (can be null)
     */
    public WorkflowSessionWrapper(Object parent, String sessionId, Map<String, Object> envs) {
        this.envs = envs;
        this.callbackManager = new CallbackManager();
        this.parent = parent;
        
        if (parent != null) {
            // Try to get session ID and envs from parent
            this.sessionId = getSessionIdFromParent(parent);
            this.envs = getEnvsFromParent(parent);
        } else if (sessionId != null) {
            this.sessionId = sessionId;
        } else {
            this.sessionId = UUID.randomUUID().toString();
        }
        
        this.workflowCard = null;
    }
    
    /**
     * Gets the callback manager.
     * 
     * @return the callback manager
     */
    public CallbackManager getCallbackManager() {
        return callbackManager;
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
     * @return the environment variables
     */
    public Map<String, Object> getEnvs() {
        return envs;
    }
    
    /**
     * Gets the parent session.
     * 
     * @return the parent session
     */
    public Object getParent() {
        return parent;
    }
    
    /**
     * Sets the workflow card.
     * 
     * @param card the workflow card
     */
    public void setWorkflowCard(Object card) {
        this.workflowCard = card;
    }
    
    /**
     * Gets the workflow card.
     * 
     * @return the workflow card
     */
    public Object getWorkflowCard() {
        return workflowCard;
    }
    
    /**
     * Extracts session ID from parent using reflection.
     * 
     * @param parent the parent object
     * @return the session ID or a new UUID if extraction fails
     */
    private String getSessionIdFromParent(Object parent) {
        try {
            // Try getSessionId() method first
            var method = parent.getClass().getMethod("getSessionId");
            Object result = method.invoke(parent);
            if (result != null) {
                return result.toString();
            }
        } catch (Exception e) {
            // Try get_session_id() method as fallback
            try {
                var method = parent.getClass().getMethod("get_session_id");
                Object result = method.invoke(parent);
                if (result != null) {
                    return result.toString();
                }
            } catch (Exception ignored) {
                // Ignore
            }
        }
        return UUID.randomUUID().toString();
    }
    
    /**
     * Extracts environment variables from parent using reflection.
     * 
     * @param parent the parent object
     * @return the environment variables or null if extraction fails
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getEnvsFromParent(Object parent) {
        try {
            // Try getEnvs() method first
            var method = parent.getClass().getMethod("getEnvs");
            Object result = method.invoke(parent);
            if (result instanceof Map<?, ?>) {
                return (Map<String, Object>) result;
            }
        } catch (Exception e) {
            // Try get_envs() method as fallback
            try {
                var method = parent.getClass().getMethod("get_envs");
                Object result = method.invoke(parent);
                if (result instanceof Map<?, ?>) {
                    return (Map<String, Object>) result;
                }
            } catch (Exception ignored) {
                // Ignore
            }
        }
        return this.envs;
    }
    
    /**
     * Creates a new workflow session.
     * 
     * @param parent the parent session
     * @param sessionId the session ID
     * @param envs the environment variables
     * @return the new workflow session
     */
    public static WorkflowSessionWrapper createWorkflowSession(Object parent, String sessionId, 
                                                               Map<String, Object> envs) {
        return new WorkflowSessionWrapper(parent, sessionId, envs);
    }
}

