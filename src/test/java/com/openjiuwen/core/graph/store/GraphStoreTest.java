/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/
package com.openjiuwen.core.graph.store;

import com.openjiuwen.core.graph.pregel.Message;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link InMemoryStore}, {@link GraphStore}, {@link GraphStoreState}, {@link PendingNode}.
 * <p>
 * Ported from Python's {@code test_graph_store.py}.
 */
class GraphStoreTest {

    // ---------- InMemoryStore basic tests ----------

    @Nested
    @DisplayName("InMemoryStore basic CRUD")
    class InMemoryStoreBasicTests {

        @Test
        @DisplayName("save, get, and delete checkpoint")
        void testSaveGetDelete() {
            InMemoryStore saver = new InMemoryStore();

            String conversationId = "conv_123";
            String ns = "default";

            // Create a mock checkpoint
            GraphStoreState checkpoint = GraphStoreState.create(
                    ns,
                    1,
                    Map.of("ch1", 42),
                    List.of(new Message("pending", "msg")),
                    Map.of("node1", new PendingNode("n1", "running")),
                    new HashMap<>()
            );

            // Save
            saver.save(conversationId, ns, checkpoint);

            // Get
            Optional<GraphStoreState> loaded = saver.get(conversationId, ns);
            assertTrue(loaded.isPresent());
            assertEquals(1, loaded.get().getStep());
            assertEquals(42, loaded.get().getChannelValues().get("ch1"));
            assertEquals("pending", loaded.get().getPendingBuffer().get(0).getSender());
            assertEquals("running", loaded.get().getPendingNode().get("node1").getStatus());
            assertEquals("n1", loaded.get().getPendingNode().get("node1").getNodeName());

            // Delete
            saver.delete(conversationId, null);
            Optional<GraphStoreState> deleted = saver.get(conversationId, ns);
            assertTrue(deleted.isEmpty());
        }

        @Test
        @DisplayName("get returns empty when not found")
        void testGetNotFound() {
            InMemoryStore saver = new InMemoryStore();
            Optional<GraphStoreState> result = saver.get("nonexistent", "ns");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("delete non-existent session is no-op")
        void testDeleteNonExistent() {
            InMemoryStore saver = new InMemoryStore();
            assertDoesNotThrow(() -> saver.delete("nonexistent", null));
        }

        @Test
        @DisplayName("overwrite existing checkpoint")
        void testOverwrite() {
            InMemoryStore saver = new InMemoryStore();
            String convId = "conv1";
            String ns = "ns1";

            saver.save(convId, ns, GraphStoreState.create(ns, 1, Map.of("k", 1),
                    List.of(), Map.of(), new HashMap<>()));
            saver.save(convId, ns, GraphStoreState.create(ns, 2, Map.of("k", 2),
                    List.of(), Map.of(), new HashMap<>()));

            Optional<GraphStoreState> loaded = saver.get(convId, ns);
            assertTrue(loaded.isPresent());
            assertEquals(2, loaded.get().getStep());
            assertEquals(2, loaded.get().getChannelValues().get("k"));
        }

        @Test
        @DisplayName("get returns deep copy (isolation)")
        void testIsolation() {
            InMemoryStore saver = new InMemoryStore();
            String convId = "conv1";
            String ns = "ns1";

            Map<String, Object> channelVals = new HashMap<>();
            channelVals.put("key", "original");
            saver.save(convId, ns, GraphStoreState.create(ns, 1, channelVals,
                    List.of(), Map.of(), new HashMap<>()));

            Optional<GraphStoreState> loaded = saver.get(convId, ns);
            assertTrue(loaded.isPresent());
            // Mutate the returned copy
            loaded.get().getChannelValues().put("key", "modified");

            // Original should be unaffected
            Optional<GraphStoreState> reloaded = saver.get(convId, ns);
            assertEquals("original", reloaded.get().getChannelValues().get("key"));
        }
    }

    // ---------- Delete by NS prefix ----------

    @Nested
    @DisplayName("Delete checkpoint by NS prefix")
    class DeleteByNsPrefixTests {

        @Test
        @DisplayName("delete by ns prefix removes matching namespaces")
        void testDeleteByNsPrefix() {
            InMemoryStore saver = new InMemoryStore();
            String conversationId = "conv_123";

            String ns1 = "apple";
            GraphStoreState checkpoint1 = GraphStoreState.create(
                    ns1, 1, Map.of("ch1", 12),
                    List.of(new Message("pending", "msg")),
                    Map.of("node1", new PendingNode("n1", "running1")),
                    new HashMap<>()
            );

            String ns2 = "apple:orange";
            GraphStoreState checkpoint2 = GraphStoreState.create(
                    ns2, 2, Map.of("ch2", 123),
                    List.of(new Message("pending", "msg")),
                    Map.of("node1", new PendingNode("n2", "running2")),
                    new HashMap<>()
            );

            // Save both
            saver.save(conversationId, ns1, checkpoint1);
            saver.save(conversationId, ns2, checkpoint2);

            // Verify both exist
            assertTrue(saver.get(conversationId, ns1).isPresent());
            assertTrue(saver.get(conversationId, ns2).isPresent());

            // Delete by prefix "apple" — should remove both "apple" and "apple:orange"
            saver.delete(conversationId, ns1);

            assertTrue(saver.get(conversationId, ns1).isEmpty());
            assertTrue(saver.get(conversationId, ns2).isEmpty());
        }

        @Test
        @DisplayName("delete by specific ns leaves other namespaces intact")
        void testDeleteSpecificNs() {
            InMemoryStore saver = new InMemoryStore();
            String convId = "conv1";

            saver.save(convId, "ns1", GraphStoreState.create("ns1", 1, Map.of(),
                    List.of(), Map.of(), new HashMap<>()));
            saver.save(convId, "ns2", GraphStoreState.create("ns2", 2, Map.of(),
                    List.of(), Map.of(), new HashMap<>()));

            // Delete only ns1
            saver.delete(convId, "ns1");

            assertTrue(saver.get(convId, "ns1").isEmpty());
            assertTrue(saver.get(convId, "ns2").isPresent());
        }
    }

    // ---------- GraphStore (decorator) tests ----------

    @Nested
    @DisplayName("GraphStore decorator")
    class GraphStoreDecoratorTests {

        @Test
        @DisplayName("GraphStore delegates to underlying store")
        void testGraphStoreDelegate() {
            InMemoryStore inner = new InMemoryStore();
            GraphStore graphStore = new GraphStore(inner);

            String convId = "conv_321";
            String ns = "default_ns";

            GraphStoreState checkpoint = GraphStoreState.create(
                    ns, 2, Map.of("ch1", 25),
                    List.of(new Message("pending", "msg2")),
                    Map.of("node1", new PendingNode("n2", "running2")),
                    new HashMap<>()
            );

            graphStore.save(convId, ns, checkpoint);

            // Read through inner store directly
            Optional<GraphStoreState> loaded = inner.get(convId, ns);
            assertTrue(loaded.isPresent());
            assertEquals(2, loaded.get().getStep());
            assertEquals(25, loaded.get().getChannelValues().get("ch1"));

            // Read through graph store
            Optional<GraphStoreState> loadedViaGraph = graphStore.get(convId, ns);
            assertTrue(loadedViaGraph.isPresent());
            assertEquals(2, loadedViaGraph.get().getStep());

            // Delete
            graphStore.delete(convId, ns);
            assertTrue(graphStore.get(convId, ns).isEmpty());
        }
    }

    // ---------- GraphStoreState tests ----------

    @Nested
    @DisplayName("GraphStoreState")
    class GraphStoreStateTests {

        @Test
        @DisplayName("create with all fields")
        void testCreate() {
            GraphStoreState state = GraphStoreState.create(
                    "ns1", 5,
                    Map.of("ch", "val"),
                    List.of(new Message("s", "t")),
                    Map.of("n1", new PendingNode("n1", "err")),
                    Map.of("n1", 3)
            );
            assertEquals("ns1", state.getNs());
            assertEquals(5, state.getStep());
            assertEquals("val", state.getChannelValues().get("ch"));
            assertEquals(1, state.getPendingBuffer().size());
            assertEquals("err", state.getPendingNode().get("n1").getStatus());
            assertEquals(3, state.getNodeVersion().get("n1"));
        }

        @Test
        @DisplayName("create with null fields uses defaults")
        void testCreateNullDefaults() {
            GraphStoreState state = GraphStoreState.create("ns", 0, null, null, null, null);
            assertNotNull(state.getChannelValues());
            assertNotNull(state.getPendingBuffer());
            assertNotNull(state.getPendingNode());
            assertNotNull(state.getNodeVersion());
        }
    }

    // ---------- PendingNode tests ----------

    @Nested
    @DisplayName("PendingNode")
    class PendingNodeTests {

        @Test
        @DisplayName("construct with name and status")
        void testBasicConstruction() {
            PendingNode pn = new PendingNode("node1", "running");
            assertEquals("node1", pn.getNodeName());
            assertEquals("running", pn.getStatus());
            assertNull(pn.getExceptions());
        }

        @Test
        @DisplayName("construct with exceptions")
        void testWithExceptions() {
            Exception ex = new RuntimeException("test error");
            PendingNode pn = new PendingNode("node1", "__error__", List.of(ex));
            assertEquals("__error__", pn.getStatus());
            assertEquals(1, pn.getExceptions().size());
            assertInstanceOf(RuntimeException.class, pn.getExceptions().get(0));
        }
    }
}
