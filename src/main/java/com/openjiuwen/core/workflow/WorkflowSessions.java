/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.workflow;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.WorkflowSessionApi;

import java.util.Map;

/**
 * Convenience facade for creating workflow sessions from the workflow package.
 *
 * <p>Mirrors Python's top-level export of {@code Session} and {@code create_workflow_session}
 * from {@code openjiuwen.core.workflow}.</p>
 */
public final class WorkflowSessions {

    private WorkflowSessions() {
    }

    /**
     * Create a new workflow session.
     *
     * @param parent    optional parent session
     * @param sessionId optional session ID (auto-generated if null)
     * @param envs      optional environment variables
     * @return a new WorkflowSessionApi instance
     */
    public static WorkflowSessionApi createWorkflowSession(BaseSession parent,
                                                            String sessionId,
                                                            Map<String, Object> envs) {
        return WorkflowSessionApi.create(parent, sessionId, envs);
    }

    /**
     * Create a new workflow session with defaults.
     *
     * @return a new WorkflowSessionApi instance
     */
    public static WorkflowSessionApi createWorkflowSession() {
        return WorkflowSessionApi.create(null, null, null);
    }

    /**
     * Create a new workflow session with a specific session ID.
     *
     * @param sessionId the session ID
     * @return a new WorkflowSessionApi instance
     */
    public static WorkflowSessionApi createWorkflowSession(String sessionId) {
        return WorkflowSessionApi.create(null, sessionId, null);
    }

    /**
     * Create a new workflow session with a parent session.
     *
     * @param parent the parent session
     * @return a new WorkflowSessionApi instance
     */
    public static WorkflowSessionApi createWorkflowSession(BaseSession parent) {
        return WorkflowSessionApi.create(parent, null, null);
    }
}
