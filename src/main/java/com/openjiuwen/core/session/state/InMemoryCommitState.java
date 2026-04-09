  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.session.state;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * In-memory commit state with pending updates and commit/rollback support.
 * <p>
 * Mirrors Python's {@code InMemoryCommitState}.
 */
public class InMemoryCommitState implements CommitStateLike {

    private final StateLike state;
    private Map<String, List<Map<String, Object>>> updates;

    public InMemoryCommitState() {
        this(new InMemoryStateLike());
    }

    public InMemoryCommitState(StateLike state) {
        this.state = state != null ? state : new InMemoryStateLike();
        this.updates = new HashMap<>();
    }

    @Override
    public synchronized void update(Map<String, Object> data) {
        throw ErrorHelper.buildError(StatusCode.ERROR, "msg", "commit state update must support node_id");
    }

    @Override
    public synchronized void updateById(String nodeId, Map<String, Object> data) {
        if (nodeId == null) {
            throw ErrorHelper.buildError(StatusCode.ERROR, "msg", "can not update state by none node_id");
        }
        updates.computeIfAbsent(nodeId, k -> new ArrayList<>()).add(InMemoryStateLike.deepCopyMap(data));
    }

    @Override
    public synchronized void commit(String nodeId) {
        if (nodeId == null) {
            for (var entry : updates.entrySet()) {
                for (var update : entry.getValue()) {
                    state.update(update);
                }
            }
            updates.clear();
        } else {
            List<Map<String, Object>> nodeUpdates = updates.get(nodeId);
            if (nodeUpdates == null || nodeUpdates.isEmpty()) {
                return;
            }
            for (var update : nodeUpdates) {
                state.update(update);
            }
            updates.put(nodeId, new ArrayList<>());
        }
    }

    @Override
    public synchronized void rollback(String nodeId) {
        updates.put(nodeId, new ArrayList<>());
    }

    @Override
    public Object getByTransformer(Function<Object, Object> transformer) {
        return state.getByTransformer(transformer);
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
    @SuppressWarnings("unchecked")
    public synchronized Map<String, Object> getUpdates() {
        return (Map<String, Object>) (Map<?, ?>) new HashMap<>(updates);
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized void setUpdates(Map<String, Object> newUpdates) {
        if (newUpdates != null) {
            this.updates = new HashMap<>((Map<String, List<Map<String, Object>>>) (Map<?, ?>) newUpdates);
        }
    }

    @Override
    public Map<String, Object> getState() {
        return state.getState();
    }

    @Override
    public void setState(Map<String, Object> newState) {
        state.setState(newState);
    }
}
