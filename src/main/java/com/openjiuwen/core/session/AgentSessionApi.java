/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import com.openjiuwen.core.multitenant.TenantContext;

import java.util.Iterator;
import java.util.Map;

/**
 * Agent-facing product session.
 *
 * <p>Mirrors Python's {@code Controller} session dependency in
 * {@code openjiuwen/core/controller/base.py}.</p>
 *
 * <p>Tenant and lifecycle methods are defaults so lightweight test mocks need
 * not implement them; production sessions ({@link AgentSession},
 * {@link AgentGroupSession}, {@link WorkflowSession}, DeepAgentSession)
 * override as needed.</p>
 */
public interface AgentSessionApi {

    String getSessionId();

    Object getState(String key);

    void updateState(Map<String, Object> data);

    void writeStream(Object data);

    Iterator<Object> streamIterator();

    default AgentSessionApi preRun(Map<String, Object> kwargs) {
        return this;
    }

    default void closeStream() {
    }

    default void commit() {
    }

    /**
     * Get the tenant context associated with this session.
     *
     * @return the tenant context, or null if not set
     * @since 0.1.7
     */
    default TenantContext getTenantContext() {
        return null;
    }

    /**
     * Set the tenant context for this session (chain-style).
     *
     * @param ctx the tenant context to associate (nullable)
     * @return this session for chaining
     * @since 0.1.7
     */
    default AgentSessionApi withTenantContext(TenantContext ctx) {
        return this;
    }
}
