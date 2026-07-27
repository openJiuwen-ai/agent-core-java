/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.systemtest;

import com.openjiuwen.core.graph.store.GraphStoreState;
import com.openjiuwen.core.graph.store.InMemoryStore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the Graph module's InMemoryStore.
 * Tests key-value storage used for graph state persistence.
 */
@Tag("system-test")
class GraphStoreSystemTest {

    private GraphStoreState createState(String ns, int step) {
        return new GraphStoreState(ns, step,
                Map.of("data", "value_" + step),
                Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap());
    }

    @Test
    @DisplayName("InMemoryStore save and get")
    void testInMemoryStoreSaveGet() {
        InMemoryStore store = new InMemoryStore();

        GraphStoreState state1 = createState("ns1", 1);
        store.save("session1", "ns1", state1).toCompletableFuture().join();

        GraphStoreState state2 = createState("ns2", 2);
        store.save("session1", "ns2", state2).toCompletableFuture().join();

        Optional<GraphStoreState> result1 = store.get("session1", "ns1").toCompletableFuture().join();
        assertTrue(result1.isPresent(), "State should be present");
        assertEquals(1, result1.get().getStep());
        System.out.println("[GraphStore Save/Get] ns1 step=" + result1.get().getStep());

        Optional<GraphStoreState> result2 = store.get("session1", "ns2").toCompletableFuture().join();
        assertTrue(result2.isPresent());
        assertEquals(2, result2.get().getStep());
    }

    @Test
    @DisplayName("InMemoryStore get missing key returns empty")
    void testInMemoryStoreGetMissing() {
        InMemoryStore store = new InMemoryStore();
        Optional<GraphStoreState> result = store.get("missing_session", "missing_ns").toCompletableFuture().join();
        assertFalse(result.isPresent(), "Missing key should return empty Optional");
    }

    @Test
    @DisplayName("InMemoryStore overwrite existing state")
    void testInMemoryStoreOverwrite() {
        InMemoryStore store = new InMemoryStore();

        store.save("sess", "ns", createState("ns", 1)).toCompletableFuture().join();
        store.save("sess", "ns", createState("ns", 5)).toCompletableFuture().join();

        Optional<GraphStoreState> result = store.get("sess", "ns").toCompletableFuture().join();
        assertTrue(result.isPresent());
        assertEquals(5, result.get().getStep(), "Should have overwritten with step=5");
        System.out.println("[GraphStore Overwrite] step=" + result.get().getStep());
    }

    @Test
    @DisplayName("InMemoryStore delete by sessionId and namespace")
    void testInMemoryStoreDelete() {
        InMemoryStore store = new InMemoryStore();

        store.save("sess_del", "ns_a", createState("ns_a", 1)).toCompletableFuture().join();
        store.save("sess_del", "ns_b", createState("ns_b", 2)).toCompletableFuture().join();
        assertTrue(store.get("sess_del", "ns_a").toCompletableFuture().join().isPresent());

        store.delete("sess_del", "ns_a").toCompletableFuture().join();
        assertFalse(store.get("sess_del", "ns_a").toCompletableFuture().join().isPresent(),
                "Deleted namespace should return empty");
        assertTrue(store.get("sess_del", "ns_b").toCompletableFuture().join().isPresent(),
                "Other ns should not be affected");
        System.out.println("[GraphStore Delete] ns_a deleted, ns_b still present");
    }

    @Test
    @DisplayName("InMemoryStore delete entire session")
    void testInMemoryStoreDeleteSession() {
        InMemoryStore store = new InMemoryStore();

        store.save("sess_all", "ns1", createState("ns1", 1)).toCompletableFuture().join();
        store.save("sess_all", "ns2", createState("ns2", 2)).toCompletableFuture().join();

        store.delete("sess_all", null).toCompletableFuture().join();
        assertFalse(store.get("sess_all", "ns1").toCompletableFuture().join().isPresent());
        assertFalse(store.get("sess_all", "ns2").toCompletableFuture().join().isPresent());
        System.out.println("[GraphStore DeleteSession] All state removed");
    }
}
