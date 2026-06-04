/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.checkpointer;

import com.openjiuwen.core.graph.pregel.Message;
import com.openjiuwen.core.graph.store.GraphStoreState;
import com.openjiuwen.core.graph.store.PendingNode;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.extensions.checkpointer.redis.storage.GraphStore;
import com.openjiuwen.extensions.store.kv.RedisStore;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Redis graph checkpoint storage.
 *
 * <p>Mirrors Python's {@code test_graph_store.py} in
 * {@code tests/unit_tests/extensions/checkpointer/test_graph_store.py}.</p>
 */
class TestGraphStore {

    @Test
    void testGraphStoreSaveAndGet() {
        GraphStore store = new GraphStore(redisStore(), null);
        GraphStoreState graphState = state("test_namespace", Map.of("ch1", 42, "ch2", "test_value"));

        store.save("test_session", "test_namespace", graphState).join();
        GraphStoreState loaded = (GraphStoreState) store.get("test_session", "test_namespace").join();

        assertNotNull(loaded);
        assertEquals("test_namespace", loaded.getNs());
        assertEquals(1, loaded.getStep());
        assertEquals(42, loaded.getChannelValues().get("ch1"));
        assertEquals("test_value", loaded.getChannelValues().get("ch2"));
        assertEquals(1, loaded.getNodeVersion().get("node1"));
    }

    @Test
    void testGraphStoreGetNonexistent() {
        GraphStore store = new GraphStore(redisStore(), null);

        assertNull(store.get("test_session", "nonexistent").join());
    }

    @Test
    void testGraphStoreDelete() {
        GraphStore store = new GraphStore(redisStore(), null);
        store.save("test_session", "test_namespace", state("test_namespace", Map.of("ch1", 42))).join();
        assertNotNull(store.get("test_session", "test_namespace").join());

        store.delete("test_session", "test_namespace").join();

        assertNull(store.get("test_session", "test_namespace").join());
    }

    @Test
    void testGraphStoreDeleteWithPattern() {
        GraphStore store = new GraphStore(redisStore(), null);
        for (int i = 0; i < 3; i++) {
            store.save("test_session", "ns_" + i, state("ns_" + i, Map.of("ch", i))).join();
        }

        store.delete("test_session", null).join();

        for (int i = 0; i < 3; i++) {
            assertNull(store.get("test_session", "ns_" + i).join());
        }
    }

    @Test
    void testGraphStoreTtlRefreshOnRead() {
        TestAgentStorage.FakeRedisClient fakeRedis = new TestAgentStorage.FakeRedisClient();
        GraphStore store = new GraphStore(
                new RedisStore(fakeRedis), Map.of("default_ttl", 1, "refresh_on_read", true));
        store.save("test_session_ttl", "test_namespace", state("test_namespace", Map.of("ch1", 42))).join();
        String keyType = Checkpointer.buildKeyWithNamespace(
                "test_session_ttl",
                Checkpointer.WORKFLOW_NAMESPACE_GRAPH,
                "test_namespace",
                "checkpoint_data_type");

        sleep(20);
        store.get("test_session_ttl", "test_namespace").join();

        assertTrue(fakeRedis.ttl(keyType) >= 59);
    }

    @Test
    void testGraphStoreSaveWithTtl() {
        TestAgentStorage.FakeRedisClient fakeRedis = new TestAgentStorage.FakeRedisClient();
        GraphStore store = new GraphStore(new RedisStore(fakeRedis), Map.of("default_ttl", 1));
        store.save("test_session_ttl_save", "test_namespace", state("test_namespace", Map.of("ch1", 42))).join();
        String keyType = Checkpointer.buildKeyWithNamespace(
                "test_session_ttl_save",
                Checkpointer.WORKFLOW_NAMESPACE_GRAPH,
                "test_namespace",
                "checkpoint_data_type");

        long ttl = fakeRedis.ttl(keyType);

        assertTrue(ttl >= 59);
        assertTrue(ttl <= 60);
    }

    @Test
    void testIntegrationRedisCheckpointSaverBasic() {
        GraphStore saver = new GraphStore(redisStore(), null);
        String namespace = "default";
        for (int i = 0; i < 10; i++) {
            namespace = namespace + "_" + i;
            saver.save("conv_redis_123", namespace, GraphStoreState.create(
                    namespace,
                    1,
                    Map.of("ch1", 42, "ch2", "test_value"),
                    List.of(
                            new Message("node1", "node2", "pending msg1"),
                            new Message("node1", "node2", "pending msg2")),
                    Map.of(
                            "node1", new PendingNode("n1", "running"),
                            "node2", new PendingNode("n2", "completed")),
                    Map.of())).join();
        }

        GraphStoreState loaded = (GraphStoreState) saver.get("conv_redis_123", namespace).join();

        assertNotNull(loaded);
        assertEquals(1, loaded.getStep());
        assertEquals(42, loaded.getChannelValues().get("ch1"));
        assertEquals("test_value", loaded.getChannelValues().get("ch2"));
        assertEquals("pending msg1", loaded.getPendingBuffer().get(0).getPayload());
        assertEquals("pending msg2", loaded.getPendingBuffer().get(1).getPayload());
        assertEquals("running", loaded.getPendingNode().get("node1").getStatus());
        assertEquals("n1", loaded.getPendingNode().get("node1").getNodeName());
        assertEquals("completed", loaded.getPendingNode().get("node2").getStatus());
        assertEquals("n2", loaded.getPendingNode().get("node2").getNodeName());
        saver.delete("conv_redis_123", "default").join();
        assertNull(saver.get("conv_redis_123", "default").join());
    }

    private GraphStoreState state(String namespace, Map<String, Object> channels) {
        return GraphStoreState.create(
                namespace,
                1,
                channels,
                List.of(),
                Map.of(),
                Map.of("node1", 1));
    }

    private RedisStore redisStore() {
        return new RedisStore(new TestAgentStorage.FakeRedisClient());
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
