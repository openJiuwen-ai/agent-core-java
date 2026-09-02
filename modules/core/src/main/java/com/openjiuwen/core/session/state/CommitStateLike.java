/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.state;

import java.util.Map;

/**
 * State interface with commit/rollback capabilities.
 * <p>
 * Mirrors Python's {@code CommitStateLike}.
 * 
 * @since 0.1.7
 */
public interface CommitStateLike extends StateLike {
    /**
     * updateById.
     * 
     * @param nodeId nodeId
     * @param data data
     * @since 0.1.7
     */
    void updateById(String nodeId, Map<String, Object> data);

    /**
     * Commit all pending updates, or only for a specific node.
     * 
     * @param nodeId nodeId
     * @since 0.1.7
     */
    void commit(String nodeId);

    /**
     * Commit all pending updates.
     * 
     * @since 0.1.7
     */
    default void commit() {
        commit(null);
    }

    /**
     * Rollback pending updates for a specific node.
     * 
     * @param nodeId nodeId
     * @since 0.1.7
     */
    void rollback(String nodeId);

    /**
     * getUpdates.
     * 
     * @return the result
     * @since 0.1.7
     */
    Map<String, Object> getUpdates();

    /**
     * Set pending updates.
     * 
     * @param updates updates
     * @since 0.1.7
     */
    void setUpdates(Map<String, Object> updates);
}
