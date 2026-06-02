package com.openjiuwen.unit_tests.core.foundation.store.graph;

import com.openjiuwen.core.foundation.store.graph.BM25Config;
import com.openjiuwen.core.foundation.store.graph.GraphStoreIndexConfig;
import com.openjiuwen.core.foundation.store.graph.GraphStoreStorageConfig;
import com.openjiuwen.core.foundation.store.vector_fields.MilvusAUTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestDatabaseConfig {

    @Test
    void testDefaultValues() {
        BM25Config config = new BM25Config();
        assertEquals(0.75, config.getBm25B());
        assertEquals(1.2, config.getBm25K1());
    }

    @Test
    void testBm25BInRange() {
        assertEquals(0.0, new BM25Config(0.0, 1.2).getBm25B());
        assertEquals(1.0, new BM25Config(1.0, 1.2).getBm25B());
    }

    @Test
    void testBm25BOutOfRangeRaises() {
        assertThrows(IllegalArgumentException.class, () -> new BM25Config(1.5, 1.2));
    }

    @Test
    void testBm25K1GeZero() {
        assertEquals(0.0, new BM25Config(0.75, 0.0).getBm25K1());
    }

    @Test
    void testBm25K1NegativeRaises() {
        assertThrows(IllegalArgumentException.class, () -> new BM25Config(0.75, -0.1));
    }

    @Test
    void testRequiredFields() {
        GraphStoreIndexConfig config = new GraphStoreIndexConfig(new MilvusAUTO(), "cosine", null, null, null);
        assertEquals("cosine", config.getDistanceMetric());
        assertNotNull(config.getIndexType());
    }

    @Test
    void testDefaults() {
        GraphStoreIndexConfig config = new GraphStoreIndexConfig(new MilvusAUTO(), "euclidean", null, null, null);
        assertTrue(config.getExtraConfigs().isEmpty());
        assertNotNull(config.getBm25Config());
    }

    @Test
    void testDistanceMetricCosine() {
        assertEquals("cosine",
                new GraphStoreIndexConfig(new MilvusAUTO(), "cosine", null, null, null).getDistanceMetric());
    }

    @Test
    void testDistanceMetricEuclidean() {
        assertEquals("euclidean",
                new GraphStoreIndexConfig(new MilvusAUTO(), "euclidean", null, null, null).getDistanceMetric());
    }

    @Test
    void testDistanceMetricDot() {
        assertEquals("dot",
                new GraphStoreIndexConfig(new MilvusAUTO(), "dot", null, null, null).getDistanceMetric());
    }

    @Test
    void testDistanceMetricInvalidRaises() {
        assertThrows(IllegalArgumentException.class,
                () -> new GraphStoreIndexConfig(new MilvusAUTO(), "l2", null, null, null));
    }

    @Test
    void testStorageDefaultValues() {
        GraphStoreStorageConfig config = new GraphStoreStorageConfig();
        assertEquals(32, config.getUuid());
        assertEquals(500, config.getName());
        assertEquals(65535, config.getContent());
        assertEquals(10, config.getLanguage());
        assertEquals(32, config.getUserId());
        assertEquals(4096, config.getEntities());
        assertEquals(4096, config.getRelations());
        assertEquals(4096, config.getEpisodes());
        assertEquals(20, config.getObjType());
    }

    @Test
    void testVarcharFieldOutOfRangeRaises() {
        assertThrows(IllegalArgumentException.class, () -> GraphStoreStorageConfig.builder().uuid(1).build());
        assertThrows(IllegalArgumentException.class, () -> GraphStoreStorageConfig.builder().uuid(0).build());
        assertThrows(IllegalArgumentException.class, () -> GraphStoreStorageConfig.builder().name(65536).build());
    }

    @Test
    void testArrayLimitOutOfRangeRaises() {
        assertThrows(IllegalArgumentException.class, () -> GraphStoreStorageConfig.builder().entities(1).build());
        assertThrows(IllegalArgumentException.class, () -> GraphStoreStorageConfig.builder().relations(5000).build());
    }
}
