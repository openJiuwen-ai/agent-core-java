/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import java.util.Map;

/**
 * Minimal session interface required by ContextEngine.
 * <p>
 * This is a partial interface covering only the methods needed
 * for context state persistence. The full implementation will be
 * provided by the session module.
 * <p>
 * Mirrors the subset of Python's {@code Session} used by {@code ContextEngine}.
 */
public interface Session {

    /**
     * Return the unique session identifier.
     */
    String getSessionId();

    /**
     * Retrieve a named state block (e.g., "context") from session storage.
     *
     * @param key the state key
     * @return the stored state, or null if not present
     */
    Object getState(String key);

    /**
     * Merge the given state map into session storage.
     *
     * @param state map of state keys to their values
     */
    void updateState(Map<String, Object> state);

    /**
     * Write a streaming payload to the session output channel.
     *
     * @param data stream payload
     */
    default void writeStream(Object data) {
    }

    /**
     * Set the current operator id for tracing and attribution.
     *
     * @param operatorId operator id, or null to clear it
     */
    default void setCurrentOperatorId(String operatorId) {
    }

    /**
     * Get the current operator id used by the active execution span.
     *
     * @return current operator id, or null if not set
     */
    default String getCurrentOperatorId() {
        return null;
    }
}
