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
 * 
 * @since 0.1.7
 */
public interface Session {
    /**
     * getSessionId.
     * 
     * @return the result
     * @since 0.1.7
     */
    String getSessionId();

    /**
     * Retrieve a named state block (e.g., "context") from session storage.
     * 
     * @param key the state key
     * @return the stored state, or null if not present
     * @since 0.1.7
     */
    Object getState(String key);

    /**
     * Merge the given state map into session storage.
     * 
     * @param state map of state keys to their values
     * @since 0.1.7
     */
    void updateState(Map<String, Object> state);

    /**
     * Write a streaming payload to the session output channel.
     * 
     * @param data stream payload
     * @since 0.1.7
     */
    default void writeStream(Object data) {
    }

    /**
     * Set the current operator id for tracing and attribution.
     * 
     * @param operatorId operator id, or null to clear it
     * @since 0.1.7
     */
    default void setCurrentOperatorId(String operatorId) {
    }

    /**
     * Get the current operator id used by the active execution span.
     * 
     * @return current operator id, or null if not set
     * @since 0.1.7
     */
    default String getCurrentOperatorId() {
        return null;
    }
}
