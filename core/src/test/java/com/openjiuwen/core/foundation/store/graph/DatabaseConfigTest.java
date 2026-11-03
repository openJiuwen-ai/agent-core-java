/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import com.openjiuwen.core.foundation.store.vector_fields.VectorField;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's graph database configuration tests in
 * {@code tests/unit_tests/core/foundation/store/graph/test_database_config.py}.
 *
 * <p>Also exercises Java types that mirror Python's configuration models in
 * {@code openjiuwen/core/foundation/store/graph/database_config.py}.</p>
 */
class DatabaseConfigTest {

    @Test
    void bm25DefaultsMatchPythonModel() {
        BM25Config config = new BM25Config();

        assertEquals(0.75d, config.getBm25B());
        assertEquals(1.2d, config.getBm25K1());
    }

    @Test
    void bm25BAcceptsBoundaryValues() {
        assertEquals(0.0d, new BM25Config(0.0d, 1.2d).getBm25B());
        assertEquals(1.0d, new BM25Config(1.0d, 1.2d).getBm25B());
    }

    @Test
    void bm25BRejectsValuesAboveOne() {
        assertThrows(IllegalArgumentException.class, () -> new BM25Config(1.5d, 1.2d));
    }

    @Test
    void bm25K1AcceptsZero() {
        assertEquals(0.0d, new BM25Config(0.75d, 0.0d).getBm25K1());
    }

    @Test
    void bm25K1RejectsNegativeValues() {
        assertThrows(IllegalArgumentException.class, () -> new BM25Config(0.75d, -0.1d));
    }

    @Test
    void indexConfigRequiresIndexTypeAndDistanceMetric() {
        GraphStoreIndexConfig config = new GraphStoreIndexConfig(new FakeVectorField(), "cosine");

        assertEquals("cosine", config.getDistanceMetric());
        assertNotNull(config.getIndexType());
    }

    @Test
    void indexConfigDefaultsExtraConfigsAndBm25Config() {
        GraphStoreIndexConfig config = new GraphStoreIndexConfig(new FakeVectorField(), "euclidean");

        assertTrue(config.getExtraConfigs().isEmpty());
        assertInstanceOf(BM25Config.class, config.getBm25Config());
    }

    @Test
    void indexConfigAcceptsCosineDistanceMetric() {
        assertEquals("cosine", new GraphStoreIndexConfig(new FakeVectorField(), "cosine").getDistanceMetric());
    }

    @Test
    void indexConfigAcceptsEuclideanDistanceMetric() {
        assertEquals("euclidean", new GraphStoreIndexConfig(new FakeVectorField(), "euclidean").getDistanceMetric());
    }

    @Test
    void indexConfigAcceptsDotDistanceMetric() {
        assertEquals("dot", new GraphStoreIndexConfig(new FakeVectorField(), "dot").getDistanceMetric());
    }

    @Test
    void indexConfigRejectsUnknownDistanceMetric() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new GraphStoreIndexConfig(new FakeVectorField(), "l2"));
    }

    @Test
    void storageConfigDefaultsMatchPythonModel() {
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
    void storageConfigRejectsOutOfRangeVarcharValues() {
        assertThrows(IllegalArgumentException.class, () -> GraphStoreStorageConfig.builder().uuid(1).build());
        assertThrows(IllegalArgumentException.class, () -> GraphStoreStorageConfig.builder().uuid(0).build());
        assertThrows(IllegalArgumentException.class, () -> GraphStoreStorageConfig.builder().name(65536).build());
    }

    @Test
    void storageConfigRejectsOutOfRangeArrayValues() {
        assertThrows(IllegalArgumentException.class, () -> GraphStoreStorageConfig.builder().entities(1).build());
        assertThrows(IllegalArgumentException.class, () -> GraphStoreStorageConfig.builder().relations(5000).build());
    }

    private static final class FakeVectorField extends VectorField {

        @Override
        public String getDatabaseType() {
            return "milvus";
        }

        @Override
        public String getIndexType() {
            return "auto";
        }

        @Override
        public Map<String, Object> toDict(String stage) {
            return Map.of(
                    "database_type", getDatabaseType(),
                    "index_type", getIndexType(),
                    "vector_field", getVectorField(),
                    "stage", stage);
        }
    }
}
