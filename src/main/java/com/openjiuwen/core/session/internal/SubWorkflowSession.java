/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.internal;

import com.openjiuwen.core.graph.stream_actor.ActorManager;
import com.openjiuwen.core.session.BaseSession;

/**
 * Node session used for nested workflow execution.
 *
 * <p>Mirrors Python's {@code SubWorkflowSession} in
 * {@code openjiuwen/core/session/internal/workflow.py}.</p>
 */
public class SubWorkflowSession extends NodeSession {

    private final String workflowId;
    private final String mainWorkflowId;
    private final int workflowNestingDepth;
    private final ActorManager actorManager;

    public SubWorkflowSession(NodeSession session, String workflowId, ActorManager actorManager) {
        super(session.parent(), session.nodeId(), session.nodeType(), false);
        this.workflowId = workflowId;
        this.mainWorkflowId = session.mainWorkflowId();
        this.workflowNestingDepth = session.workflowNestingDepth() + 1;
        this.actorManager = actorManager;
    }

    public SubWorkflowSession(NodeSession session, String workflowId) {
        this(session, workflowId, null);
    }

    public SubWorkflowSession(BaseSession session, String nodeId, String nodeType, String subWorkflowId) {
        super(session, nodeId, nodeType, false);
        this.workflowId = subWorkflowId == null || subWorkflowId.isEmpty() ? super.workflowId() : subWorkflowId;
        this.mainWorkflowId = super.mainWorkflowId();
        this.workflowNestingDepth = super.workflowNestingDepth() + 1;
        this.actorManager = null;
        Object tracer = tracer();
        if (tracer != null) {
            try {
                tracer.getClass().getMethod("registerWorkflowSpanManager", String.class)
                        .invoke(tracer, executableId());
            } catch (ReflectiveOperationException ignored) {
                // Tracing registration is best effort and must not alter session construction.
            }
        }
    }

    public String subWorkflowId() {
        return workflowId;
    }

    @Override
    public String workflowId() {
        return workflowId;
    }

    @Override
    public String mainWorkflowId() {
        return mainWorkflowId;
    }

    @Override
    public int workflowNestingDepth() {
        return workflowNestingDepth;
    }

    @Override
    public ActorManager actorManager() {
        return actorManager;
    }
}
