/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

import com.openjiuwen.core.graph.store.Store;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.interaction.InteractiveInput;

/**
 * Base checkpointer for agent, team, workflow, and graph state.
 *
 * <p>Mirrors Python's {@code Checkpointer} in
 * {@code openjiuwen/core/session/checkpointer/base.py}.</p>
 */
public abstract class Checkpointer {

    public static final String SESSION_NAMESPACE_AGENT = "agent";
    public static final String SESSION_NAMESPACE_AGENT_TEAM = "agent-team";
    public static final String SESSION_NAMESPACE_WORKFLOW = "workflow";
    public static final String WORKFLOW_NAMESPACE_GRAPH = "workflow-graph";

    public static String getThreadId(BaseSession session) {
        return String.join(":", session.sessionId(), session.workflowId());
    }

    public static String buildKey(String... parts) {
        for (String part : parts) {
            if (part == null) {
                throw new NullPointerException("key part");
            }
        }
        return String.join(":", parts);
    }

    public static String buildKeyWithNamespace(String sessionId, String namespace, String entityId,
                                               String... suffixes) {
        String[] parts = new String[3 + suffixes.length];
        parts[0] = sessionId;
        parts[1] = namespace;
        parts[2] = entityId;
        System.arraycopy(suffixes, 0, parts, 3, suffixes.length);
        return buildKey(parts);
    }

    public void preAgentExecute(BaseSession session, Object inputs) {
    }

    public void interruptAgentExecute(BaseSession session) {
    }

    public void postAgentExecute(BaseSession session) {
    }

    public void preAgentTeamExecute(BaseSession session, Object inputs) {
    }

    public void postAgentTeamExecute(BaseSession session) {
    }

    public void preWorkflowExecute(BaseSession session, InteractiveInput inputs) {
    }

    public void preWorkflowExecute(BaseSession session, Object inputs) {
        preWorkflowExecute(session, inputs instanceof InteractiveInput interactiveInput ? interactiveInput : null);
    }

    public void postWorkflowExecute(BaseSession session, Object result, Exception exception) {
    }

    public boolean sessionExists(String sessionId) {
        return false;
    }

    public void release(String sessionId) {
    }

    public Store graphStore() {
        return null;
    }
}
