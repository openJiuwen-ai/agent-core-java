package com.openjiuwen.unit_tests.core.foundation.store.graph.milvus;

import com.openjiuwen.core.foundation.store.base_embedding.Embedding;
import com.openjiuwen.core.foundation.store.graph.GraphConfig;
import com.openjiuwen.core.foundation.store.graph.GraphStoreIndexConfig;
import com.openjiuwen.core.foundation.store.graph.GraphStoreStorageConfig;
import com.openjiuwen.core.foundation.store.graph.WeightedRankConfig;
import com.openjiuwen.core.foundation.store.graph.milvus.MilvusGraphStore;
import com.openjiuwen.core.foundation.store.vector_fields.MilvusAUTO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestMilvusSupport {

    @Test
    void testFromConfigReturnsInstance() throws Exception {
        assertTrue(MilvusGraphStore.fromConfig(config("milvus", "cosine")) instanceof MilvusGraphStore);
    }

    @Test
    void testConfigReturnsConfig() throws Exception {
        GraphConfig config = config("milvus", "cosine");
        MilvusGraphStore store = new MilvusGraphStore(config);
        assertSame(config, store.getConfig());
    }

    @Test
    void testEmbedderReturnsNullByDefault() throws Exception {
        assertNull(new MilvusGraphStore(config("milvus", "cosine")).getEmbedder());
    }

    @Test
    void testExecutorIsCreated() throws Exception {
        ExecutorService executor = new MilvusGraphStore(config("milvus", "cosine")).getEmbedExecutor();
        assertNotNull(executor);
    }

    @Test
    void testMetricMapsDotToIp() throws Exception {
        assertEquals("IP", readField(new MilvusGraphStore(config("milvus", "dot")), "metric"));
    }

    @Test
    void testMetricMapsEuclideanToL2() throws Exception {
        assertEquals("L2", readField(new MilvusGraphStore(config("milvus", "euclidean")), "metric"));
    }

    @Test
    void testDenseSearchParamsUseMetric() throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) readField(new MilvusGraphStore(config("milvus", "dot")),
                "denseSearchParams");
        assertEquals("IP", params.get("metric_type"));
    }

    @Test
    void testFullTextSearchParamsUseBm25() throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) readField(new MilvusGraphStore(config("milvus", "cosine")),
                "fullTextSearchParams");
        assertEquals("BM25", params.get("metric_type"));
    }

    @Test
    void testAliasGenerated() throws Exception {
        String alias = (String) readField(new MilvusGraphStore(config("milvus", "cosine")), "alias");
        assertTrue(alias.startsWith("graph-store-"));
    }

    @Test
    void testAttachEmbedderStoresEmbedder() throws Exception {
        MilvusGraphStore store = new MilvusGraphStore(config("milvus", "cosine"));
        StubEmbedding embedding = new StubEmbedding(64);
        store.attachEmbedder(embedding);
        assertSame(embedding, store.getEmbedder());
    }

    @Test
    void testAttachEmbedderRedefine() throws Exception {
        MilvusGraphStore store = new MilvusGraphStore(config("milvus", "cosine"));
        store.attachEmbedder(new StubEmbedding(64));
        StubEmbedding replacement = new StubEmbedding(64);
        store.attachEmbedder(replacement);
        assertSame(replacement, store.getEmbedder());
    }

    @Test
    void testRefreshDoesNotThrow() throws Exception {
        assertDoesNotThrow(() -> new MilvusGraphStore(config("milvus", "cosine")).refresh());
    }

    @Test
    void testAddDataDoesNotThrow() throws Exception {
        MilvusGraphStore store = new MilvusGraphStore(config("milvus", "cosine"));
        assertDoesNotThrow(() -> store.addData("entity", List.of(Map.of("uuid", "1")), true, false));
    }

    @Test
    void testAddEntityDoesNotThrow() throws Exception {
        MilvusGraphStore store = new MilvusGraphStore(config("milvus", "cosine"));
        assertDoesNotThrow(() -> store.addEntity(List.of(), true, false, true));
    }

    @Test
    void testAddRelationDoesNotThrow() throws Exception {
        MilvusGraphStore store = new MilvusGraphStore(config("milvus", "cosine"));
        assertDoesNotThrow(() -> store.addRelation(List.of(), true, false, true));
    }

    @Test
    void testAddEpisodeDoesNotThrow() throws Exception {
        MilvusGraphStore store = new MilvusGraphStore(config("milvus", "cosine"));
        assertDoesNotThrow(() -> store.addEpisode(List.of(), true, false, true));
    }

    @Test
    void testIsEmptyReturnsTruePlaceholder() throws Exception {
        assertTrue(new MilvusGraphStore(config("milvus", "cosine")).isEmpty("entity"));
    }

    @Test
    void testQueryReturnsEmptyList() throws Exception {
        assertTrue(new MilvusGraphStore(config("milvus", "cosine")).query("entity", null, null, false).isEmpty());
    }

    @Test
    void testDeleteReturnsDeletedZero() throws Exception {
        assertEquals(0, new MilvusGraphStore(config("milvus", "cosine"))
                .delete("entity", null, null)
                .get("deleted"));
    }

    @Test
    void testSearchReturnsResultsKey() throws Exception {
        assertTrue(new MilvusGraphStore(config("milvus", "cosine"))
                .search("q", 5, "entity", new WeightedRankConfig(), 0, 0, null, List.of(), List.of(), Map.of())
                .containsKey("results"));
    }

    @Test
    void testFieldDefInitialized() throws Exception {
        assertNotNull(readField(new MilvusGraphStore(config("milvus", "cosine")), "fieldDef"));
    }

    @Test
    void testCloseShutsExecutorDown() throws Exception {
        MilvusGraphStore store = new MilvusGraphStore(config("milvus", "cosine"));
        store.close();
        assertTrue(store.getEmbedExecutor().isShutdown());
    }

    private static GraphConfig config(String backend, String metric) throws Exception {
        Path tempDir = Files.createTempDirectory("milvus-support-");
        return GraphConfig.builder()
                .uri(tempDir.resolve("graph.db").toString())
                .backend(backend)
                .dbStorageConfig(new GraphStoreStorageConfig())
                .dbEmbedConfig(new GraphStoreIndexConfig(new MilvusAUTO(), metric, null, null, null))
                .build();
    }

    private static Object readField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    static final class StubEmbedding extends Embedding {
        private final int dimension;

        StubEmbedding(int dimension) {
            this.dimension = dimension;
        }

        @Override
        public List<Float> embedQuery(String text) {
            return java.util.Collections.nCopies(dimension, 0.0f);
        }

        @Override
        public List<List<Float>> embedDocuments(List<String> texts, Integer batchSize) {
            return texts.stream()
                    .map(text -> java.util.Collections.nCopies(dimension, 0.0f))
                    .toList();
        }

        @Override
        public int getDimension() {
            return dimension;
        }
    }
}
