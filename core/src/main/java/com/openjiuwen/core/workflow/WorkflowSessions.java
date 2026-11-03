/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.WorkflowSession;

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

    public static WorkflowSession createWorkflowSession(BaseSession parent,
                                                            String sessionId,
                                                            Map<String, Object> envs) {
        return WorkflowSession.create(parent, sessionId, envs);
    }

    public static WorkflowSession createWorkflowSession() {
        return WorkflowSession.create(null, null, null);
    }

    public static WorkflowSession createWorkflowSession(String sessionId) {
        return WorkflowSession.create(null, sessionId, null);
    }

    public static WorkflowSession createWorkflowSession(BaseSession parent) {
        return WorkflowSession.create(parent, null, null);
    }
}
