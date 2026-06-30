package examples.graph_memory;

import com.openjiuwen.core.graph.store.GraphMemoryExampleSupport;
import com.openjiuwen.core.graph.store.InMemoryStore;

/**
 * Thin entry point for the Java graph-memory example baseline.
 */
public final class GraphMemoryExample {

    private GraphMemoryExample() {
    }

    public static void main(String[] args) {
        InMemoryStore store = new InMemoryStore();
        var checkpoint = GraphMemoryExampleSupport.seedCheckpoint("memory_ns", 1, "user", "hello");
        GraphMemoryExampleSupport.saveCheckpoint(store, "session-1", checkpoint);
        var restored = GraphMemoryExampleSupport.loadCheckpoint(store, "session-1", "memory_ns");
        restored.ifPresent(value -> System.out.println(GraphMemoryExampleSupport.summarize(value)));
    }
}
