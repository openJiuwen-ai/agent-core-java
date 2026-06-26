/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.graph.store;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of the graph state store.
 * <p>
 * Mirrors Python's {@code InMemoryStore} in
 * {@code openjiuwen/core/graph/store/inmemory.py}.
 * </p>
 */
public class InMemoryStore implements Store {

    private final Map<String, Map<String, GraphStoreState>> storeCk = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<Optional<GraphStoreState>> get(String sessionId, String ns) {
        Map<String, GraphStoreState> sessionMap = storeCk.get(sessionId);
        if (sessionMap == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        GraphStoreState state = sessionMap.get(ns);
        return CompletableFuture.completedFuture(Optional.ofNullable(deepCopy(state)));
    }

    @Override
    public CompletionStage<Void> save(String sessionId, String ns, GraphStoreState state) {
        storeCk.computeIfAbsent(sessionId, ignored -> new ConcurrentHashMap<>())
                .put(ns, deepCopy(state));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> delete(String sessionId, String ns) {
        Map<String, GraphStoreState> sessionMap = storeCk.get(sessionId);
        if (sessionMap == null) {
            return CompletableFuture.completedFuture(null);
        }

        if (ns == null) {
            storeCk.remove(sessionId);
            return CompletableFuture.completedFuture(null);
        }

        deleteNsByPrefix(sessionMap, ns);
        if (sessionMap.isEmpty()) {
            storeCk.remove(sessionId);
        }
        return CompletableFuture.completedFuture(null);
    }

    private static void deleteNsByPrefix(Map<String, GraphStoreState> subMap, String prefix) {
        Iterator<String> iterator = subMap.keySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().startsWith(prefix)) {
                iterator.remove();
            }
        }
    }

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
