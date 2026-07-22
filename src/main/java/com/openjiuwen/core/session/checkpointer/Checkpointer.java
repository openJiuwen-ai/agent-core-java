/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

import com.openjiuwen.core.graph.store.Store;
import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantKVStoreKeyResolver;
import com.openjiuwen.core.multitenant.TenantNamespaceFactory;
import com.openjiuwen.core.multitenant.TenantNamespaceFactories;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.internal.NodeSession;
import com.openjiuwen.core.session.internal.WorkflowSession;

/**
 * Abstract checkpointer for managing session state persistence across workflow/agent executions.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.checkpointer.base.Checkpointer}.
 * 
 * @since 0.1.7
 */
public abstract class Checkpointer {
    /**
     * SESSION_NAMESPACE_AGENT.
     * 
     * @since 0.1.7
     */
    public static final String SESSION_NAMESPACE_AGENT = "agent";

    /**
     * SESSION_NAMESPACE_WORKFLOW.
     * 
     * @since 0.1.7
     */
    public static final String SESSION_NAMESPACE_WORKFLOW = "workflow";

    /**
     * WORKFLOW_NAMESPACE_GRAPH.
     * 
     * @since 0.1.7
     */
    public static final String WORKFLOW_NAMESPACE_GRAPH = "workflow-graph";

    private TenantNamespaceFactory namespaceFactory = TenantNamespaceFactories.KV_STORE_DEFAULT;

    public void setNamespaceFactory(TenantNamespaceFactory namespaceFactory) {
        this.namespaceFactory = namespaceFactory != null ? namespaceFactory : TenantNamespaceFactories.KV_STORE_DEFAULT;
    }

    public TenantNamespaceFactory getNamespaceFactory() {
        return namespaceFactory;
    }

    /**
     * Get the thread ID for a session (session_id:workflow_id).
     * 
     * @param session the session
     * @return the thread ID string
     * @since 0.1.7
     */
    public static String getThreadId(BaseSession session) {
        return session.sessionId() + ":" + getWorkflowId(session);
    }

    /**
     * Pre-workflow execution hook.
     * 
     * @param session the session
     * @param inputs the interactive input, or null for fresh execution
     * @since 0.1.7
     */
    public abstract void preWorkflowExecute(BaseSession session, InteractiveInput inputs);

    /**
     * Post-workflow execution hook.
     * 
     * @param session the session
     * @param result the execution result
     * @param exception any exception that occurred
     * @since 0.1.7
     */
    public abstract void postWorkflowExecute(BaseSession session, Object result, Exception exception);

    /**
     * Pre-agent execution hook.
     * 
     * @param session the session
     * @param inputs the input data
     * @since 0.1.7
     */
    public abstract void preAgentExecute(BaseSession session, Object inputs);

    /**
     * Interrupt agent execution for interaction.
     * 
     * @param session the session
     * @since 0.1.7
     */
    public abstract void interruptAgentExecute(BaseSession session);

    /**
     * Post-agent execution hook.
     * 
     * @param session the session
     * @since 0.1.7
     */
    public abstract void postAgentExecute(BaseSession session);

    /**
     * Check whether a session exists.
     * 
     * @param sessionId the session ID
     * @return true if the session isExists
     * @since 0.1.7
     */
    public abstract boolean sessionExists(String sessionId);

    /**
     * Release (clear) all checkpoints for a session.
     * 
     * @param sessionId the session ID
     * @since 0.1.7
     */
    public abstract void release(String sessionId);

    /**
     * Get the graph store used by this checkpointer.
     * 
     * @return the graph store for workflow graph checkpoints
     * @since 0.1.7
     */
    public abstract Store graphStore();

    // ---- Utility ----

    /**
     * getWorkflowId.
     * 
     * @param session session
     * @return the result
     * @since 0.1.7
     */
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
     * @since 0.1.7
     */
    public static String buildKey(String... parts) {
        return String.join(":", parts);
    }

    /**
     * Build a key with namespace structure: session:namespace:entity_id:suffixes.
     * 
     * @param sessionId the session ID
     * @param namespace the namespace
     * @param entityId the entity ID
     * @param suffixes additional key suffixes
     * @return the built key
     * @since 0.1.7
     */
    public static String buildKeyWithNamespace(String sessionId, String namespace, String entityId,
            String... suffixes) {
        StringBuilder sb = new StringBuilder();
        sb.append(sessionId).append(':').append(namespace).append(':').append(entityId);
        for (String suffix : suffixes) {
            sb.append(':').append(suffix);
        }
        return sb.toString();
    }

    /**
     * Build a namespaced key and resolve it against the current tenant context.
     * Convenience wrapper around {@link #buildKeyWithNamespace} and
     * {@link TenantKVStoreKeyResolver#resolveKey(String)}.
     *
     * @param sessionId sessionId
     * @param namespace namespace
     * @param entityId entityId
     * @param suffixes suffixes
     * @return the tenant-resolved key
     * @since 0.1.7
     */
    public static String resolveNsKey(String sessionId, String namespace, String entityId,
            String... suffixes) {
        return TenantKVStoreKeyResolver.resolveKey(
            buildKeyWithNamespace(sessionId, namespace, entityId, suffixes));
    }

    /**
     * Build a namespaced prefix and resolve it against the current tenant context.
     *
     * @param sessionId sessionId
     * @param namespace namespace
     * @param entityId entityId
     * @param suffixes suffixes
     * @return the tenant-resolved prefix
     * @since 0.1.7
     */
    public static String resolveNsPrefix(String sessionId, String namespace, String entityId,
            String... suffixes) {
        return TenantKVStoreKeyResolver.resolvePrefix(
            buildKeyWithNamespace(sessionId, namespace, entityId, suffixes));
    }

    /**
     * Build a tenant-namespaced key from parts using the default namespace factory.
     * 
     * @param ctx the tenant context
     * @param parts the key parts
     * @return the tenant-namespaced key
     * @since 0.1.7
     */
    public static String buildKeyWithTenant(TenantContext ctx, String... parts) {
        return buildKeyWithTenant(TenantNamespaceFactories.KV_STORE_DEFAULT, ctx, parts);
    }

    /**
     * Build a tenant-namespaced key from parts using the given namespace factory.
     * 
     * @param factory the namespace factory, or null to use the default
     * @param ctx the tenant context
     * @param parts the key parts
     * @return the tenant-namespaced key
     * @since 0.1.7
     */
    public static String buildKeyWithTenant(TenantNamespaceFactory factory, TenantContext ctx, String... parts) {
        TenantNamespaceFactory nsFactory = factory != null ? factory : TenantNamespaceFactories.KV_STORE_DEFAULT;
        String rawKey = buildKey(parts);
        return nsFactory.namespace(ctx, rawKey);
    }

    /**
     * Build a tenant-namespaced key from parts using this instance's namespace factory.
     * 
     * @param ctx the tenant context
     * @param parts the key parts
     * @return the tenant-namespaced key
     * @since 0.1.7
     */
    public String buildKeyWithTenantInstance(TenantContext ctx, String... parts) {
        String rawKey = buildKey(parts);
        return namespaceFactory.namespace(ctx, rawKey);
    }
}
