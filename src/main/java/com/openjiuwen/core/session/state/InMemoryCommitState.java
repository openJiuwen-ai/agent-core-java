/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.state;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.session.SessionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory implementation of CommitStateLike with commit/rollback semantics.
 * 
 * <p>Updates are staged per node and only applied to the stable state upon commit.
 * This provides transaction-like behavior for state management.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class InMemoryCommitState implements CommitStateLike {
    
    private static final LoggerProtocol logger = LogManager.getLogger("session");
    
    /**
     * Underlying stable state.
     */
    private final StateLike state;
    
    /**
     * Staged updates per node.
     */
    private Map<String, List<Map<String, Object>>> updates;
    
    /**
     * Creates a new InMemoryCommitState with a new InMemoryStateLike.
     */
    public InMemoryCommitState() {
        this(null);
    }
    
    /**
     * Creates a new InMemoryCommitState with the given underlying state.
     * 
     * @param state the underlying state, or null to create a new InMemoryStateLike
     */
    public InMemoryCommitState(StateLike state) {
        this.state = state != null ? state : new InMemoryStateLike();
        this.updates = new HashMap<>();
    }
    
    @Override
    public void update(Map<String, Object> data) {
        throw new JiuWenBaseException(-1, "commit state update must support node_id");
    }
    
    @Override
    public void updateById(String nodeId, Map<String, Object> data) {
        if (nodeId == null) {
            throw new JiuWenBaseException(1, "can not update state by none node_id");
        }
        
        updates.computeIfAbsent(nodeId, k -> new ArrayList<>())
               .add(SessionUtils.deepCopyMap(data));
    }
    
    @Override
    public void commit() {
        commit(null);
    }
    
    @Override
    public void commit(String nodeId) {
        if (nodeId == null) {
            // Commit all nodes
            for (Map.Entry<String, List<Map<String, Object>>> entry : updates.entrySet()) {
                for (Map<String, Object> update : entry.getValue()) {
                    state.update(update);
                }
            }
            updates.clear();
        } else {
            // Commit specific node
            List<Map<String, Object>> nodeUpdates = updates.get(nodeId);
            if (nodeUpdates == null || nodeUpdates.isEmpty()) {
                logger.debug("node [{}] outputs has no updates", nodeId);
                return;
            }
            
            for (Map<String, Object> update : nodeUpdates) {
                state.update(update);
            }
            updates.put(nodeId, new ArrayList<>());
        }
    }
    
    @Override
    public void rollback(String nodeId) {
        updates.put(nodeId, new ArrayList<>());
    }
    
    @Override
    public <T> T getByTransformer(Transformer<T> transformer) {
        return transformer.transform(new StateLikeReadableWrapper(state));
    }
    
    @Override
    public Object get(Object key) {
        return state.get(key);
    }
    
    @Override
    public Object getByPrefix(Object key, String nestedPrefix) {
        return state.getByPrefix(key, nestedPrefix);
    }
    
    @Override
    public Map<String, List<Map<String, Object>>> getUpdates() {
        return updates;
    }
    
    @Override
    public void setUpdates(Map<String, List<Map<String, Object>>> updates) {
        if (updates != null) {
            this.updates = updates;
        }
    }
    
    @Override
    public Map<String, Object> getState() {
        return state.getState();
    }
    
    @Override
    public void setState(Map<String, Object> state) {
        this.state.setState(state);
    }
    
    /**
     * Wrapper to provide ReadableStateLike interface over a StateLike.
     */
    private static class StateLikeReadableWrapper implements ReadableStateLike {
        private final StateLike delegate;
        
        StateLikeReadableWrapper(StateLike delegate) {
            this.delegate = delegate;
        }
        
        @Override
        public Object get(Object key) {
            return delegate.get(key);
        }
        
        @Override
        public Object getByPrefix(Object key, String nestedPrefix) {
            return delegate.getByPrefix(key, nestedPrefix);
        }
    }
}

