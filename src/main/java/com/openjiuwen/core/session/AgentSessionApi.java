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

    /**
     * Mark that this session has already completed {@code preRun} for the current invocation.
     */
    default void markPreRunDone() {
    }

    /**
     * Mark that this session has already completed {@code postRun} for the current invocation.
     */
    default void markPostRunDone() {
    }

    /**
     * Whether {@code preRun} has already completed for the current invocation.
     *
     * @return {@code true} if pre-run already completed
     * @since 0.1.15
     */
    default boolean isPreRunDone() {
        return false;
    }

    /**
     * Whether {@code postRun} has already completed for the current invocation.
     *
     * @return {@code true} if post-run already completed
     * @since 0.1.15
     */
    default boolean isPostRunDone() {
        return false;
    }

    /**
     * Copy only the pre-run flag from {@code source}. Leaves any post-run flag
     * on this session untouched so a reused upstream session cannot skip
     * {@code postRun} on the downstream/effective session.
     *
     * @param source the session whose pre-run flag to copy; ignored when null
     * @since 0.1.15
     */
    default void copyPreRunState(AgentSessionApi source) {
        if (source != null && source.isPreRunDone()) {
            markPreRunDone();
        }
    }

    /**
     * Copy pre-run and post-run flags from {@code source}. Used after
     * {@code postRun} so the upstream session skips a second checkpoint.
     *
     * @param source the session whose run-state to copy; ignored when null
     * @since 0.1.15
     */
    default void copyRunState(AgentSessionApi source) {
        if (source == null) {
            return;
        }
        if (source.isPreRunDone()) {
            markPreRunDone();
        }
        if (source.isPostRunDone()) {
            markPostRunDone();
        }
    }

    /**
     * Clear the post-run flag so a reused session still runs {@code postRun}
     * on the next invocation. Copying a stale POST-done flag into the next
     * run skips stream close and checkpoint commit.
     */
    default void resetPostRunState() {
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
