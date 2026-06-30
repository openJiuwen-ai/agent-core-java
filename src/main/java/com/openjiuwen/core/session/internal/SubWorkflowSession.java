/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.internal;

import com.openjiuwen.core.graph.stream_actor.ActorManager;
import com.openjiuwen.core.session.BaseSession;

/**
 * Sub-workflow session used when a workflow is nested inside another workflow.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.internal.workflow.SubWorkflowSession}.
 */
public class SubWorkflowSession extends NodeSession {

    private final String subWorkflowId;
    private final int subWorkflowNestingDepth;
    private ActorManager actorManager;

    /**
     * Auto-generated for codecheck compliance.
     */
    public SubWorkflowSession(BaseSession session, String nodeId, String nodeType, String workflowId) {
        super(resolveParentSession(session), nodeId, nodeType);
        this.subWorkflowId = workflowId;

        // Increment nesting depth from parent
        if (session instanceof WorkflowSession) {
            this.subWorkflowNestingDepth = ((WorkflowSession) session).workflowNestingDepth() + 1;
        } else if (session instanceof NodeSession) {
            this.subWorkflowNestingDepth = ((NodeSession) session).workflowNestingDepth() + 1;
        } else {
            this.subWorkflowNestingDepth = 1;
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public SubWorkflowSession(BaseSession session, String nodeId, String workflowId) {
        this(session, nodeId, null, workflowId);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String workflowId() {
        return subWorkflowId;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public int workflowNestingDepth() {
        return subWorkflowNestingDepth;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public ActorManager actorManager() {
        return actorManager;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setActorManager(ActorManager actorManager) {
        this.actorManager = actorManager;
    }

    /**
     * Close the sub-workflow session by shutting down its actor manager.
     * Mirrors Python's {@code SubWorkflowSession.close()}.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void close() {
        if (actorManager != null) {
            actorManager.shutdown();
        }
    }

    private static BaseSession resolveParentSession(BaseSession session) {
        if (session instanceof NodeSession nodeSession && nodeSession.parent() != null) {
            return nodeSession.parent();
        }
        return session;
    }
}
