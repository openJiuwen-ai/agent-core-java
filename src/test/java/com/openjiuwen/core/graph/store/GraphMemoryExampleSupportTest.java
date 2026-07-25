package com.openjiuwen.core.graph.store;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GraphMemoryExampleSupportTest {

    @Test
    void shouldSeedSaveLoadAndSummarizeCheckpoint() {
        InMemoryStore store = new InMemoryStore();
        GraphStoreState checkpoint = GraphMemoryExampleSupport.seedCheckpoint("memory_ns", 2, "user", "hello");

        GraphMemoryExampleSupport.saveCheckpoint(store, "session-1", checkpoint);
        var loaded = GraphMemoryExampleSupport.loadCheckpoint(store, "session-1", "memory_ns");

        assertThat(loaded).isPresent();
        assertThat(loaded.get().getStep()).isEqualTo(2);
        assertThat(GraphMemoryExampleSupport.summarize(loaded.get()))
                .contains("ns=memory_ns")
                .contains("step=2")
                .contains("pending=1");
    }
}
