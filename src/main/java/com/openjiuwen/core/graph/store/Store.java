/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.store;

import java.util.Optional;

/**
 * Abstract interface for graph state persistence.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.store.base.Store}.
 * 
 * @since 0.1.7
 */
public interface Store {
    /**
     * get.
     * 
     * @param sessionId sessionId
     * @param ns ns
     * @return the result
     * @since 0.1.7
     */
    Optional<GraphStoreState> get(String sessionId, String ns);

    /**
     * Save graph state.
     * 
     * @param sessionId the session identifier
     * @param ns the namespace
     * @param state the graph state to save
     * @since 0.1.7
     */
    void save(String sessionId, String ns, GraphStoreState state);

    /**
     * Delete graph state.
     * 
     * @param sessionId the session identifier
     * @param ns the namespace to delete, or null to delete all namespaces
     * @since 0.1.7
     */
    void delete(String sessionId, String ns);
}
