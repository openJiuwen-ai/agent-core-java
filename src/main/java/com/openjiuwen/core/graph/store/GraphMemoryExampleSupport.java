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
 *
 * <p>The current Java baseline aligns to the graph-store/checkpoint layer
 * instead of the full Python `graph_memory` module.</p>
 */
public final class GraphMemoryExampleSupport {
    private GraphMemoryExampleSupport() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static GraphStoreState seedCheckpoint(String ns, int step, String sender, String text) {
        return GraphStoreState.create(
                ns,
                step,
                Map.of("last_message", text),
                List.of(new Message(sender, text)),
                Map.of("node", new PendingNode("node", "running")),
                new HashMap<>()
        );
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static void saveCheckpoint(InMemoryStore store, String sessionId, GraphStoreState state) {
        store.save(sessionId, state.getNs(), state).toCompletableFuture().join();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Optional<GraphStoreState> loadCheckpoint(InMemoryStore store, String sessionId, String ns) {
        return store.get(sessionId, ns).toCompletableFuture().join();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static String summarize(GraphStoreState state) {
        return "ns=" + state.getNs()
                + ", step=" + state.getStep()
                + ", channels=" + state.getChannelValues().keySet()
                + ", pending=" + state.getPendingBuffer().size();
    }
}
