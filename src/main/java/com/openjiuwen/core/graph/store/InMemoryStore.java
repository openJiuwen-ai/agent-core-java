/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.graph.store;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of the graph state store.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.store.inmemory.InMemoryStore}.
 * Stores the latest graph state per session ID and namespace.
 */
public class InMemoryStore implements Store {

    /** Nested map: sessionId → (ns → state). */
    private final Map<String, Map<String, GraphStoreState>> storeCk = new ConcurrentHashMap<>();

    @Override
    public Optional<GraphStoreState> get(String sessionId, String ns) {
        Map<String, GraphStoreState> sessionMap = storeCk.get(sessionId);
        if (sessionMap == null) {
            return Optional.empty();
        }
        GraphStoreState state = sessionMap.get(ns);
        return Optional.ofNullable(deepCopy(state));
    }

    @Override
    public void save(String sessionId, String ns, GraphStoreState state) {
        storeCk.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>())
                .put(ns, deepCopy(state));
    }

    @Override
    public void delete(String sessionId, String ns) {
        Map<String, GraphStoreState> sessionMap = storeCk.get(sessionId);
        if (sessionMap == null) {
            return;
        }

        if (ns == null) {
            storeCk.remove(sessionId);
        } else {
            deleteNsByPrefix(sessionMap, ns);
            if (sessionMap.isEmpty()) {
                storeCk.remove(sessionId);
            }
        }
    }

    private static void deleteNsByPrefix(Map<String, GraphStoreState> subMap, String prefix) {
        Iterator<String> it = subMap.keySet().iterator();
        while (it.hasNext()) {
            if (it.next().startsWith(prefix)) {
                it.remove();
            }
        }
    }

    /**
     * Deep copy of state for isolation.
     * In production, a proper serialization-based copy should be used.
     */
    private static GraphStoreState deepCopy(GraphStoreState state) {
        if (state == null) {
            return null;
        }
        return GraphStoreState.create(
                state.getNs(),
                state.getStep(),
                new HashMap<>(state.getChannelValues()),
                new java.util.ArrayList<>(state.getPendingBuffer()),
                new HashMap<>(state.getPendingNode()),
                new HashMap<>(state.getNodeVersion())
        );
    }
}
