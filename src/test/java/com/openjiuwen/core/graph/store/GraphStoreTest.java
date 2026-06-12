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
