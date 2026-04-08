/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.graph.store;

import java.util.Optional;

/**
 * Abstract interface for graph state persistence.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.store.base.Store}.
 */
public interface Store {

    /**
     * Get the stored graph state for a given session and namespace.
     *
     * @param sessionId the session identifier
     * @param ns        the namespace (workflow ID)
     * @return the stored state, or empty if not found
     */
    Optional<GraphStoreState> get(String sessionId, String ns);

    /**
     * Save graph state.
     *
     * @param sessionId the session identifier
     * @param ns        the namespace
     * @param state     the graph state to save
     */
    void save(String sessionId, String ns, GraphStoreState state);

    /**
     * Delete graph state.
     *
     * @param sessionId the session identifier
     * @param ns        the namespace to delete, or null to delete all namespaces
     */
    void delete(String sessionId, String ns);
}
