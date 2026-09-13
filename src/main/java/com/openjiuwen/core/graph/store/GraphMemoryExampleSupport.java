/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.store;

import com.openjiuwen.core.graph.pregel.Message;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Shared helper for Java graph-memory style examples.
 * <p>
 * The current Java baseline aligns to the graph-store/checkpoint layer
 * instead of the full Python `graph_memory` module.
 * </p>
 * 
 * @since 0.1.7
 */
public final class GraphMemoryExampleSupport {
    /**
     * GraphMemoryExampleSupport.
     * 
     * @since 0.1.7
     */
    private GraphMemoryExampleSupport() {
    }

    /**
     * seedCheckpoint.
     * 
     * @param ns ns
     * @param step step
     * @param sender sender
     * @param text text
     * @return the result
     * @since 0.1.7
     */
    public static GraphStoreState seedCheckpoint(String ns, int step, String sender, String text) {
        return GraphStoreState.create(ns, step, Map.of("last_message", text), List.of(new Message(sender, text)),
                Map.of("node", new PendingNode("node", "running")), new HashMap<>());
    }

    /**
     * saveCheckpoint.
     * 
     * @param store store
     * @param sessionId sessionId
     * @param state state
     * @since 0.1.7
     */
    public static void saveCheckpoint(InMemoryStore store, String sessionId, GraphStoreState state) {
        store.save(sessionId, state.getNs(), state);
    }

    /**
     * loadCheckpoint.
     * 
     * @param store store
     * @param sessionId sessionId
     * @param ns ns
     * @return the result
     * @since 0.1.7
     */
    public static Optional<GraphStoreState> loadCheckpoint(InMemoryStore store, String sessionId, String ns) {
        return store.get(sessionId, ns);
    }

    /**
     * summarize.
     * 
     * @param state state
     * @return the result
     * @since 0.1.7
     */
    public static String summarize(GraphStoreState state) {
        return "ns=" + state.getNs() + ", step=" + state.getStep() + ", channels=" + state.getChannelValues().keySet()
                + ", pending=" + state.getPendingBuffer().size();
    }
}
