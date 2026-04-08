/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

import com.openjiuwen.core.graph.store.Store;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.internal.NodeSession;
import com.openjiuwen.core.session.internal.WorkflowSession;

/**
 * Abstract checkpointer for managing session state persistence across workflow/agent executions.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.checkpointer.base.Checkpointer}.
 */
public abstract class Checkpointer {

    /**
     * Namespace for agent state under session.
     */
    public static final String SESSION_NAMESPACE_AGENT = "agent";

    /**
     * Namespace for workflow state under session.
     */
    public static final String SESSION_NAMESPACE_WORKFLOW = "workflow";

    /**
     * Namespace for graph state under workflow.
     */
    public static final String WORKFLOW_NAMESPACE_GRAPH = "workflow-graph";

    /**
     * Get the thread ID for a session (session_id:workflow_id).
     *
     * @param session the session
     * @return the thread ID string
     */
    public static String getThreadId(BaseSession session) {
        return session.sessionId() + ":" + getWorkflowId(session);
    }

    /**
     * Pre-workflow execution hook.
     *
     * @param session the session
     * @param inputs  the interactive input, or null for fresh execution
     */
    public abstract void preWorkflowExecute(BaseSession session, InteractiveInput inputs);

    /**
     * Post-workflow execution hook.
     *
     * @param session   the session
     * @param result    the execution result
     * @param exception any exception that occurred
     */
    public abstract void postWorkflowExecute(BaseSession session, Object result, Exception exception);

    /**
     * Pre-agent execution hook.
     *
     * @param session the session
     * @param inputs  the input data
     */
    public abstract void preAgentExecute(BaseSession session, Object inputs);

    /**
     * Interrupt agent execution for interaction.
     *
     * @param session the session
     */
    public abstract void interruptAgentExecute(BaseSession session);

    /**
     * Post-agent execution hook.
     *
     * @param session the session
     */
    public abstract void postAgentExecute(BaseSession session);

    /**
     * Check whether a session exists.
     *
     * @param sessionId the session ID
     * @return true if the session exists
     */
    public abstract boolean sessionExists(String sessionId);

    /**
     * Release (clear) all checkpoints for a session.
     *
     * @param sessionId the session ID
     */
    public abstract void release(String sessionId);

    /**
     * Get the graph store used by this checkpointer.
     *
     * @return the graph store for workflow graph checkpoints
     */
    public abstract Store graphStore();

    // ---- Utility ----

    protected static String getWorkflowId(BaseSession session) {
        if (session instanceof WorkflowSession ws) {
            return ws.workflowId();
        } else if (session instanceof NodeSession ns) {
            return ns.workflowId();
        }
        return session.sessionId();
    }

    /**
     * Build a key by joining parts with ':'.
     *
     * @param parts the key parts
     * @return joined key
     */
    public static String buildKey(String... parts) {
        return String.join(":", parts);
    }

    /**
     * Build a key with namespace structure: session:namespace:entity_id:suffixes.
     *
     * @param sessionId the session ID
     * @param namespace the namespace
     * @param entityId  the entity ID
     * @param suffixes  additional key suffixes
     * @return the built key
     */
    public static String buildKeyWithNamespace(String sessionId, String namespace,
                                                String entityId, String... suffixes) {
        StringBuilder sb = new StringBuilder();
        sb.append(sessionId).append(':')
                .append(namespace).append(':')
                .append(entityId);
        for (String suffix : suffixes) {
            sb.append(':').append(suffix);
        }
        return sb.toString();
    }
}
