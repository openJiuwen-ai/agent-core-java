/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import com.openjiuwen.core.session.callback.CallbackManager;

import java.util.Map;
import java.util.UUID;

/**
 * User-facing workflow session managing the lifecycle of a workflow execution.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.workflow.Session}.
 */
public class WorkflowSessionApi {

    private final String sessionId;
    private final Map<String, Object> envs;
    private final CallbackManager callbackManager;
    private final BaseSession parent;
    private Object workflowCard;

    /**
     * Create a workflow session from a parent session.
     *
     * @param parent    the parent base session (nullable)
     * @param sessionId the session ID (nullable, auto-generated if absent)
     * @param envs      environment variables (nullable)
     */
    public WorkflowSessionApi(BaseSession parent, String sessionId, Map<String, Object> envs) {
        this.callbackManager = new CallbackManager();
        this.parent = parent;

        if (parent != null) {
            this.sessionId = sessionId != null ? sessionId : UUID.randomUUID().toString();
            this.envs = parent.config() != null ? parent.config().getEnvs() : envs;
        } else if (sessionId != null) {
            this.sessionId = sessionId;
            this.envs = envs;
        } else {
            this.sessionId = UUID.randomUUID().toString();
            this.envs = envs;
        }
    }

    public WorkflowSessionApi(BaseSession parent, String sessionId) {
        this(parent, sessionId, null);
    }

    public WorkflowSessionApi(String sessionId) {
        this(null, sessionId, null);
    }

    public CallbackManager getCallbackManager() {
        return callbackManager;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Map<String, Object> getEnvs() {
        return envs;
    }

    public BaseSession getParent() {
        return parent;
    }

    public void setWorkflowCard(Object card) {
        this.workflowCard = card;
    }

    public Object getWorkflowCard() {
        return workflowCard;
    }

    /**
     * Factory method to create a workflow session.
     */
    public static WorkflowSessionApi create(BaseSession parent, String sessionId, Map<String, Object> envs) {
        return new WorkflowSessionApi(parent, sessionId, envs);
    }
}
