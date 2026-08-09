/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

import com.openjiuwen.core.graph.store.Store;
import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantKVStoreKeyResolver;
import com.openjiuwen.core.multitenant.TenantNamespaceFactories;
import com.openjiuwen.core.multitenant.TenantNamespaceFactory;
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
        return String.join(":", session.sessionId(), workflowId(session));
    }

    public static String workflowId(BaseSession session) {
        return stringIdentity(session, "workflowId");
    }

    public static String agentId(BaseSession session) {
        return stringIdentity(session, "agentId");
    }

    public static String teamId(BaseSession session) {
        return stringIdentity(session, "teamId");
    }

    public static BaseSession parent(BaseSession session) {
        Object value = invokeIdentity(session, "parent");
        return value instanceof BaseSession parent ? parent : null;
    }

    private static String stringIdentity(BaseSession session, String methodName) {
        Object value = invokeIdentity(session, methodName);
        return value == null ? "" : String.valueOf(value);
    }

    private static Object invokeIdentity(BaseSession session, String methodName) {
        if (session == null) {
            return null;
        }
        try {
            return session.getClass().getMethod(methodName).invoke(session);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
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

    /**
     * Build a namespaced key and resolve it against the current tenant context.
     */
    public static String resolveNsKey(String sessionId, String namespace, String entityId,
            String... suffixes) {
        return TenantKVStoreKeyResolver.resolveKey(
                buildKeyWithNamespace(sessionId, namespace, entityId, suffixes));
    }

    /**
     * Build a namespaced prefix and resolve it against the current tenant context.
     */
    public static String resolveNsPrefix(String sessionId, String namespace, String entityId,
            String... suffixes) {
        return TenantKVStoreKeyResolver.resolvePrefix(
                buildKeyWithNamespace(sessionId, namespace, entityId, suffixes));
    }

    /**
     * Build a tenant-namespaced key from parts using the default namespace factory.
     */
    public static String buildKeyWithTenant(TenantContext ctx, String... parts) {
        return buildKeyWithTenant(TenantNamespaceFactories.KV_STORE_DEFAULT, ctx, parts);
    }

    /**
     * Build a tenant-namespaced key from parts using the given namespace factory.
     */
    public static String buildKeyWithTenant(TenantNamespaceFactory factory, TenantContext ctx, String... parts) {
        TenantNamespaceFactory nsFactory = factory != null ? factory : TenantNamespaceFactories.KV_STORE_DEFAULT;
        String rawKey = buildKey(parts);
        return nsFactory.namespace(ctx, rawKey);
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
