  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.session.state;

import java.util.Map;

/**
 * State interface with commit/rollback capabilities.
 * <p>
 * Mirrors Python's {@code CommitStateLike}.
 */
public interface CommitStateLike extends StateLike {

    /**
     * Update state by node id.
     */
    void updateById(String nodeId, Map<String, Object> data);

    /**
     * Commit all pending updates, or only for a specific node.
     */
    void commit(String nodeId);

    /**
     * Commit all pending updates.
     */
    default void commit() {
        commit(null);
    }

    /**
     * Rollback pending updates for a specific node.
     */
    void rollback(String nodeId);

    /**
     * Get pending updates.
     */
    Map<String, Object> getUpdates();

    /**
     * Set pending updates.
     */
    void setUpdates(Map<String, Object> updates);
}
