/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.WorkflowSessionApi;

import java.util.Map;

/**
 * Convenience facade for creating workflow sessions from the workflow package.
 *
 * <p>Mirrors Python's {@code Session} and {@code create_workflow_session} exports in
 * {@code openjiuwen/core/workflow/__init__.py}.</p>
 */
public final class WorkflowSessions {

    private WorkflowSessions() {
    }

    public static WorkflowSessionApi createWorkflowSession(BaseSession parent,
                                                            String sessionId,
                                                            Map<String, Object> envs) {
        return WorkflowSessionApi.create(parent, sessionId, envs);
    }

    public static WorkflowSessionApi createWorkflowSession() {
        return WorkflowSessionApi.create(null, null, null);
    }

    public static WorkflowSessionApi createWorkflowSession(String sessionId) {
        return WorkflowSessionApi.create(null, sessionId, null);
    }

    public static WorkflowSessionApi createWorkflowSession(BaseSession parent) {
        return WorkflowSessionApi.create(parent, null, null);
    }
}
