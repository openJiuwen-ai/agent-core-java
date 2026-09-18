/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.state;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Mirrors Python's {@code InMemoryCommitState} in
 * {@code openjiuwen/core/session/state/base.py}.
 */
public class InMemoryCommitState implements CommitStateLike {

    private final StateLike state;
    private Map<String, List<Map<String, Object>>> updates;

    public InMemoryCommitState() {
        this(null);
    }

    public InMemoryCommitState(StateLike state) {
        this.state = state == null ? new InMemoryStateLike() : state;
        this.updates = new LinkedHashMap<>();
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
        updates.computeIfAbsent(nodeId, ignored -> new ArrayList<>()).add(InMemoryStateLike.deepCopyMap(data));
    }

    @Override
    public synchronized void commit(String nodeId) {
        if (nodeId == null) {
            for (List<Map<String, Object>> nodeUpdates : updates.values()) {
                for (Map<String, Object> update : nodeUpdates) {
                    state.update(update);
                }
            }
            updates.clear();
            return;
        }
        List<Map<String, Object>> nodeUpdates = updates.get(nodeId);
        if (nodeUpdates == null || nodeUpdates.isEmpty()) {
            return;
        }
        for (Map<String, Object> update : nodeUpdates) {
            state.update(update);
        }
        updates.put(nodeId, new ArrayList<>());
    }

    @Override
    public synchronized void rollback(String nodeId) {
        updates.put(nodeId, new ArrayList<>());
    }

    @Override
    public synchronized Object getByTransformer(Function<Object, Object> transformer) {
        return transformer.apply(state);
    }

    @Override
    public synchronized Object get(Object key) {
        return state.get(key);
    }

    @Override
    public synchronized Object getByPrefix(Object key, String nestedPrefix) {
        return state.getByPrefix(key, nestedPrefix);
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized Map<String, Object> getUpdates() {
        return (Map<String, Object>) (Map<?, ?>) updates;
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized void setUpdates(Map<String, Object> newUpdates) {
        if (newUpdates != null && !newUpdates.isEmpty()) {
            this.updates = (Map<String, List<Map<String, Object>>>) (Map<?, ?>) newUpdates;
        }
    }

    @Override
    public synchronized Map<String, Object> getState() {
        return state.getState();
    }

    @Override
    public synchronized void setState(Map<String, Object> newState) {
        state.setState(newState);
    }
}
