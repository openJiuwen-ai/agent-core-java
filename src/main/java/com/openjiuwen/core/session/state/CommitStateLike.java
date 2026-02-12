/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.state;

import java.util.Map;
import java.util.List;

/**
 * Interface for commit-based state operations.
 * 
 * <p>Extends StateLike with commit/rollback semantics for transaction-like state management.
 * Updates are staged by node ID and only applied to the stable state upon commit.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public interface CommitStateLike extends StateLike {
    
    /**
     * Updates the state for a specific node.
     * 
     * <p>Updates are staged and not immediately applied to the stable state.
     * Call {@link #commit(String)} to apply updates.
     * 
     * @param nodeId the node identifier
     * @param data the update data
     */
    void updateById(String nodeId, Map<String, Object> data);
    
    /**
     * Commits all staged updates to the stable state.
     * 
     * <p>All updates from all nodes are applied and the update lists are cleared.
     */
    void commit();
    
    /**
     * Commits staged updates for a specific node to the stable state.
     * 
     * @param nodeId the node identifier, or null to commit all nodes
     */
    void commit(String nodeId);
    
    /**
     * Rolls back (discards) staged updates for a specific node.
     * 
     * @param nodeId the node identifier
     */
    void rollback(String nodeId);
    
    /**
     * Gets all staged updates.
     * 
     * @return map of node ID to list of updates
     */
    Map<String, List<Map<String, Object>>> getUpdates();
    
    /**
     * Sets the staged updates.
     * 
     * @param updates the updates to set
     */
    void setUpdates(Map<String, List<Map<String, Object>>> updates);
}

