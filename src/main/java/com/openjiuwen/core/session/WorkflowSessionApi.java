/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import com.openjiuwen.core.session.config.Config;

import java.util.Map;
import java.util.UUID;

/**
 * Public workflow-session facade.
 *
 * <p>Mirrors Python's {@code Session} in
 * {@code openjiuwen/core/session/workflow.py}.</p>
 */
public class WorkflowSessionApi {

    private final BaseSession parent;
    private final String sessionId;
    private Map<String, Object> envs;
    private Object workflowCard;

    public WorkflowSessionApi() {
        this(null, null, null);
    }

    public WorkflowSessionApi(BaseSession parent, String sessionId, Map<String, Object> envs) {
        this.parent = parent;
        if (parent != null) {
            this.sessionId = sessionId;
            this.envs = parent.config() == null ? null : parent.config().getEnvs();
        } else if (sessionId != null) {
            this.sessionId = sessionId;
            this.envs = envs;
        } else {
            this.sessionId = UUID.randomUUID().toString();
            this.envs = envs;
        }
    }

    public static WorkflowSessionApi createWorkflowSession(BaseSession parent, String sessionId,
                                                           Map<String, Object> envs) {
        return new WorkflowSessionApi(parent, sessionId, envs);
    }

    public static WorkflowSessionApi createWorkflowSession(BaseSession parent) {
        return new WorkflowSessionApi(parent, null, null);
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

    public Config asConfig() {
        Config config = new Config();
        config.setEnvs(getEnvs());
        return config;
    }
}
