/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.graph.pregel.Message;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's graph-store base behavior in
 * {@code openjiuwen/core/graph/store/base.py}.
 *
 * <p>Mirrors Python's {@code test_memory_checkpoint_saver_basic} in
 * {@code tests/unit_tests/core/graph/test_graph_store.py}.</p>
 */
class GraphStoreTest {

    @Test
    void createStateUsesPythonDefaultCollections() {
        GraphStoreState state = GraphStoreState.create(
                "graph-a",
                3,
                Map.of("channel", "value"),
                null,
                null,
                null
        );

        assertThat(state.getNs()).isEqualTo("graph-a");
        assertThat(state.getStep()).isEqualTo(3);
        assertThat(state.getChannelValues()).containsEntry("channel", "value");
        assertThat(state.getPendingBuffer()).isEmpty();
        assertThat(state.getPendingNode()).isEmpty();
        assertThat(state.getNodeVersion()).isEmpty();
    }

    @Test
    void graphStoreDelegatesAsyncOperations() {
        RecordingStore delegate = new RecordingStore();
        GraphStore store = new GraphStore(delegate);
        GraphStoreState state = GraphStoreState.create(
                "graph-a",
                1,
                Map.of("count", 1),
                List.of(new Message("a", "b", "payload")),
                Map.of("node-a", new PendingNode("node-a", "running")),
                Map.of("node-a", 2)
        );
        delegate.state = Optional.of(state);

        Optional<GraphStoreState> loaded = store.get("session-a", "graph-a").toCompletableFuture().join();
        store.save("session-a", "graph-a", state).toCompletableFuture().join();
        store.delete("session-a", null).toCompletableFuture().join();

        assertThat(loaded).contains(state);
        assertThat(delegate.lastSaveSessionId).isEqualTo("session-a");
        assertThat(delegate.lastSaveNs).isEqualTo("graph-a");
        assertThat(delegate.lastDeleteSessionId).isEqualTo("session-a");
        assertThat(delegate.lastDeleteNs).isNull();
    }

    @Test
    void inMemoryStoreSavesLoadsAndDeletesCheckpoint() {
        InMemoryStore saver = new InMemoryStore();
        String conversationId = "conv_123";
        String ns = "default";
        GraphStoreState checkpoint = GraphStoreState.create(
                ns,
                1,
                Map.of("ch1", 42),
                List.of(new Message("", "", "pending msg")),
                Map.of("node1", new PendingNode("n1", "running")),
                null
        );

        saver.save(conversationId, ns, checkpoint).toCompletableFuture().join();

        Optional<GraphStoreState> loaded = saver.get(conversationId, ns).toCompletableFuture().join();
        assertThat(loaded).isPresent();
        GraphStoreState loadedState = loaded.orElseThrow();
        assertThat(loadedState.getStep()).isEqualTo(1);
        assertThat(loadedState.getChannelValues()).containsEntry("ch1", 42);
        assertThat(loadedState.getPendingBuffer().get(0).getPayload()).isEqualTo("pending msg");
        assertThat(loadedState.getPendingNode().get("node1").getStatus()).isEqualTo("running");
        assertThat(loadedState.getPendingNode().get("node1").getNodeName()).isEqualTo("n1");

        saver.delete(conversationId, null).toCompletableFuture().join();

        assertThat(saver.get(conversationId, ns).toCompletableFuture().join()).isEmpty();
    }

    @Test
    void graphStoreWrapperSavesThroughInMemoryStore() {
        InMemoryStore saver = new InMemoryStore();
        GraphStore graphStore = new GraphStore(saver);
        String conversationId = "conv_321";
        String ns = "default_ns";
        GraphStoreState checkpoint = GraphStoreState.create(
                ns,
                2,
                Map.of("ch1", 25),
                List.of(new Message("", "", "pending msg2")),
                Map.of("node1", new PendingNode("n2", "running2")),
                null
        );

        graphStore.save(conversationId, ns, checkpoint).toCompletableFuture().join();

        Optional<GraphStoreState> loaded = saver.get(conversationId, ns).toCompletableFuture().join();
        assertThat(loaded).isPresent();
        GraphStoreState loadedState = loaded.orElseThrow();
        assertThat(loadedState.getStep()).isEqualTo(2);
        assertThat(loadedState.getChannelValues()).containsEntry("ch1", 25);
        assertThat(loadedState.getPendingBuffer().get(0).getPayload()).isEqualTo("pending msg2");
        assertThat(loadedState.getPendingNode().get("node1").getStatus()).isEqualTo("running2");
        assertThat(loadedState.getPendingNode().get("node1").getNodeName()).isEqualTo("n2");

        saver.delete(conversationId, ns).toCompletableFuture().join();

        assertThat(saver.get(conversationId, ns).toCompletableFuture().join()).isEmpty();
    }

    @Test
    void inMemoryStoreDeletesNamespaceAndChildrenByPrefix() {
        InMemoryStore saver = new InMemoryStore();
        String conversationId = "conv_123";
        GraphStoreState checkpoint1 = GraphStoreState.create(
                "apple",
                1,
                Map.of("ch1", 12),
                List.of(new Message("", "", "pending msg")),
                Map.of("node1", new PendingNode("n1", "running1")),
                null
        );
        GraphStoreState checkpoint2 = GraphStoreState.create(
                "apple:orange",
                2,
                Map.of("ch2", 123),
                List.of(new Message("", "", "pending msg")),
                Map.of("node1", new PendingNode("n2", "running2")),
                null
        );

        saver.save(conversationId, "apple", checkpoint1).toCompletableFuture().join();
        saver.save(conversationId, "apple:orange", checkpoint2).toCompletableFuture().join();

        assertThat(saver.get(conversationId, "apple").toCompletableFuture().join()).isPresent();
        assertThat(saver.get(conversationId, "apple:orange").toCompletableFuture().join()).isPresent();

        saver.delete(conversationId, "apple").toCompletableFuture().join();

        assertThat(saver.get(conversationId, "apple").toCompletableFuture().join()).isEmpty();
        assertThat(saver.get(conversationId, "apple:orange").toCompletableFuture().join()).isEmpty();
    }

    @Test
    void graphStorePropagatesDelegateFailures() {
        RecordingStore delegate = new RecordingStore();
        delegate.saveError = new IllegalStateException("boom");
        GraphStore store = new GraphStore(delegate);
        GraphStoreState state = GraphStoreState.create("graph-a", 1, Map.of(), null, null, null);

        assertThatThrownBy(() -> store.save("session-a", "graph-a", state).toCompletableFuture().join())
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("boom");
    }

    private static final class RecordingStore implements Store {

        private Optional<GraphStoreState> state = Optional.empty();
        private Throwable saveError;
        private String lastSaveSessionId;
        private String lastSaveNs;
        private String lastDeleteSessionId;
        private String lastDeleteNs;

        @Override
        public CompletionStage<Optional<GraphStoreState>> get(String sessionId, String ns) {
            return CompletableFuture.completedFuture(state);
        }

        @Override
        public CompletionStage<Void> save(String sessionId, String ns, GraphStoreState state) {
            if (saveError != null) {
                return CompletableFuture.failedFuture(saveError);
            }
            lastSaveSessionId = sessionId;
            lastSaveNs = ns;
            this.state = Optional.of(state);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> delete(String sessionId, String ns) {
            lastDeleteSessionId = sessionId;
            lastDeleteNs = ns;
            state = Optional.empty();
            return CompletableFuture.completedFuture(null);
        }
    }
}
