package com.openjiuwen.unit_tests.core.foundation.store.graph;

import com.openjiuwen.core.foundation.store.base_embedding.Embedding;
import com.openjiuwen.core.foundation.store.graph.GraphConfig;
import com.openjiuwen.core.foundation.store.graph.GraphStore;
import com.openjiuwen.core.foundation.store.graph.GraphStoreFactory;
import com.openjiuwen.spi.store.query.QueryExpr;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestBase {

    @Test
    void testInstantiateRaisesRuntimeError() throws Exception {
        Constructor<GraphStoreFactory> constructor = GraphStoreFactory.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThrows(Exception.class, () -> constructor.newInstance());
    }

    @Test
    void testEmptyNameRaisesError() {
        assertThrows(IllegalArgumentException.class,
                () -> GraphStoreFactory.registerBackend("", DummyGraphStore.class, false));
    }

    @Test
    void testDuplicateNameWithoutForceRaisesError() {
        String backend = "graph-test-dup";
        GraphStoreFactory.registerBackend(backend, DummyGraphStore.class, true);
        try {
            assertThrows(IllegalStateException.class,
                    () -> GraphStoreFactory.registerBackend(backend, AlternateDummyGraphStore.class, false));
        } finally {
            GraphStoreFactory.registerBackend(backend, DummyGraphStore.class, true);
        }
    }

    @Test
    void testDuplicateNameWithForceOverwrites() {
        String backend = "graph-test-force";
        GraphStoreFactory.registerBackend(backend, DummyGraphStore.class, true);
        GraphStoreFactory.registerBackend(backend, AlternateDummyGraphStore.class, true);
        assertSame(AlternateDummyGraphStore.class, GraphStoreFactory.getBackendClass(backend));
    }

    @Test
    void testHappyPathRegisterAndRetrieve() {
        String backend = "graph-test-happy";
        GraphStoreFactory.registerBackend(backend, DummyGraphStore.class, true);
        assertSame(DummyGraphStore.class, GraphStoreFactory.getBackendClass(backend));
    }

    @Test
    void testUnknownBackendRaisesError() throws Exception {
        GraphConfig config = tempConfig("graph-test-unknown");
        assertThrows(IllegalArgumentException.class,
                () -> GraphStoreFactory.fromConfig(config, "unknown_backend_xyz"));
    }

    @Test
    void testKnownBackendReturnsFromConfig() throws Exception {
        String backend = "graph-test-known";
        GraphStoreFactory.registerBackend(backend, DummyGraphStore.class, true);
        GraphConfig config = tempConfig(backend);
        GraphStore store = GraphStoreFactory.fromConfig(config, backend);
        assertSame(DummyGraphStore.class, store.getClass());
    }

    @Test
    void testBackendOverrideUsesExplicitName() throws Exception {
        GraphStoreFactory.registerBackend("graph-test-default", AlternateDummyGraphStore.class, true);
        GraphStoreFactory.registerBackend("graph-test-override", DummyGraphStore.class, true);
        GraphConfig config = tempConfig("graph-test-default");
        GraphStore store = GraphStoreFactory.fromConfig(config, "graph-test-override");
        assertSame(DummyGraphStore.class, store.getClass());
    }

    @Test
    void testDefaultBackendRegistered() {
        assertTrue(GraphStoreFactory.isRegistered("in_memory"));
    }

    @Test
    void testUnknownBackendIsNotRegistered() {
        assertFalse(GraphStoreFactory.isRegistered("definitely_missing_graph_backend"));
    }

    private static GraphConfig tempConfig(String backend) throws Exception {
        Path tempDir = Files.createTempDirectory("graph-store-factory-");
        return GraphConfig.builder()
                .uri(tempDir.resolve("graph.db").toString())
                .backend(backend)
                .build();
    }

    public static class DummyGraphStore implements GraphStore {
        public static DummyGraphStore fromConfig(GraphConfig config) {
            return new DummyGraphStore();
        }

        @Override
        public GraphConfig getConfig() {
            return null;
        }

        @Override
        public ExecutorService getEmbedExecutor() {
            return null;
        }

        @Override
        public Embedding getEmbedder() {
            return null;
        }

        @Override
        public void refresh() {
        }

        @Override
        public void addData(String collection, Iterable<Map<String, Object>> data, boolean flush, boolean upsert) {
        }

        @Override
        public void addEntity(Iterable<?> entities, boolean flush, boolean upsert, boolean noEmbed) {
        }

        @Override
        public void addRelation(Iterable<?> relations, boolean flush, boolean upsert, boolean noEmbed) {
        }

        @Override
        public void addEpisode(Iterable<?> episodes, boolean flush, boolean upsert, boolean noEmbed) {
        }

        @Override
        public boolean isEmpty(String collection) {
            return true;
        }

        @Override
        public List<Map<String, Object>> query(String collection, List<Object> ids, QueryExpr expr,
                                               boolean silenceErrors) {
            return List.of();
        }

        @Override
        public Map<String, Object> delete(String collection, List<Object> ids, QueryExpr expr) {
            return Map.of();
        }

        @Override
        public Map<String, List<Map<String, Object>>> search(String queryText, int k, String collection,
                                                             Object rankerConfig, int bfsDepth, int bfsK,
                                                             QueryExpr filterExpr, List<String> outputFields,
                                                             List<Float> queryEmbedding,
                                                             Map<String, Object> kwargs) {
            return Map.of();
        }

        @Override
        public void attachEmbedder(Embedding embedder) {
        }

        @Override
        public void close() {
        }
    }

    public static final class AlternateDummyGraphStore extends DummyGraphStore {
        public static AlternateDummyGraphStore fromConfig(GraphConfig config) {
            return new AlternateDummyGraphStore();
        }
    }
}
