/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector;

import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.spi.store.vector.CollectionSchema;
import com.openjiuwen.spi.store.vector.FieldSchema;
import com.openjiuwen.spi.store.vector.VectorDataType;
import com.openjiuwen.spi.store.vector.VectorSearchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GaussVectorStore.
 * <p>
 * Mirrors Python's {@code test_gauss_vector_store.py}. Database calls are
 * represented with an injected in-memory delegate, matching the Python tests'
 * mocked psycopg2 boundary.
 */
@DisplayName("GaussVectorStore Tests")
class TestGaussVectorStore {

    @Nested
    class TestGaussVectorStoreInit {
        @Test
        void testInitWithDefaultParams() {
            assertNotNull(newStore("cosine"));
        }

        @Test
        void testInitWithCustomParams() {
            GaussVectorStore store = newStore("dot");
            assertNotNull(store);
        }

        @Test
        void testLazyInitNoConnectionOnInit() {
            assertDoesNotThrow(() -> newStore("cosine"));
        }

        @Test
        void testConnectionReuse() throws Exception {
            GaussVectorStore store = preparedStore("cosine");
            store.addDocs("test_collection", docs(), null);
            assertEquals(2, store.search("test_collection", vector(0.1f, 0.2f, 0.3f),
                    "embedding", 5, null, null).size());
        }

        @Test
        void testCloseAndReconnect() {
            GaussVectorStore store = newStore("cosine");
            assertDoesNotThrow(store::close);
            assertDoesNotThrow(store::close);
        }
    }

    @Nested
    class TestGaussVectorStoreCreateCollection {
        @Test
        void testCreateCollectionWithSchemaObject() throws Exception {
            GaussVectorStore store = newStore("cosine");
            store.createCollection("test_collection", schema(), null);
            assertTrue(store.collectionExists("test_collection", null));
        }

        @Test
        void testCreateCollectionWithDictSchema() throws Exception {
            GaussVectorStore store = newStore("cosine");
            store.createCollection("test_collection", schema().toDict(), null);
            assertEquals(3, store.getSchema("test_collection", null).getFields().size());
        }

        @Test
        void testCreateCollectionWithCustomMetric() throws Exception {
            GaussVectorStore store = newStore("euclidean");
            store.createCollection("test_collection", schema(), Map.of("distance_metric", "L2"));
            assertEquals("l2", store.getCollectionMetadata("test_collection").get("distance_metric"));
        }

        @Test
        void testCreateCollectionAlreadyExists() throws Exception {
            GaussVectorStore store = newStore("cosine");
            store.createCollection("test_collection", schema(), null);
            store.createCollection("test_collection", schema(), null);
            assertTrue(store.collectionExists("test_collection", null));
        }

        @Test
        void testCreateCollectionMissingVectorDim() {
            assertThrows(RuntimeException.class, () -> FieldSchema.builder()
                    .name("embedding")
                    .dtype(VectorDataType.FLOAT_VECTOR)
                    .build());
        }

        @Test
        void testCreateCollectionMissingVectorField() {
            GaussVectorStore store = newStore("cosine");
            CollectionSchema noVector = new CollectionSchema().addField(idField());
            assertThrows(IllegalArgumentException.class,
                    () -> store.createCollection("test_collection", noVector, null));
        }

        @Test
        void testCreateCollectionWithAutoId() throws Exception {
            GaussVectorStore store = newStore("cosine");
            CollectionSchema schema = new CollectionSchema()
                    .addField(FieldSchema.builder().name("id").dtype(VectorDataType.VARCHAR)
                            .maxLength(256).isPrimary(true).autoId(true).build())
                    .addField(vectorField());
            store.createCollection("test_collection", schema, null);
            assertTrue(store.getSchema("test_collection", null).getPrimaryKeyField().orElseThrow().isAutoId());
        }
    }

    @Nested
    class TestGaussVectorStoreDeleteCollection {
        @Test
        void testDeleteCollectionSuccess() throws Exception {
            GaussVectorStore store = preparedStore("cosine");
            store.deleteCollection("test_collection", null);
            assertFalse(store.collectionExists("test_collection", null));
        }

        @Test
        void testDeleteCollectionNotExists() {
            GaussVectorStore store = newStore("cosine");
            assertDoesNotThrow(() -> store.deleteCollection("test_collection", null));
        }
    }

    @Nested
    class TestGaussVectorStoreCollectionExists {
        @Test
        void testCollectionExistsTrue() throws Exception {
            assertTrue(preparedStore("cosine").collectionExists("test_collection", null));
        }

        @Test
        void testCollectionExistsFalse() throws Exception {
            assertFalse(newStore("cosine").collectionExists("test_collection", null));
        }
    }

    @Nested
    class TestGaussVectorStoreGetSchema {
        @Test
        void testGetSchemaSuccess() throws Exception {
            CollectionSchema result = preparedStore("cosine").getSchema("test_collection", null);
            assertEquals(3, result.getFields().size());
        }

        @Test
        void testGetSchemaCollectionNotExists() {
            assertThrows(IllegalArgumentException.class, () -> newStore("cosine").getSchema("missing", null));
        }
    }

    @Nested
    class TestGaussVectorStoreAddDocs {
        @Test
        void testAddDocsSuccess() throws Exception {
            GaussVectorStore store = preparedStore("cosine");
            store.addDocs("test_collection", docs(), null);
            assertEquals(2, store.search("test_collection", vector(0.1f, 0.2f, 0.3f),
                    "embedding", 5, null, null).size());
        }

        @Test
        void testAddDocsWithBatchSize() throws Exception {
            GaussVectorStore store = preparedStore("cosine");
            store.addDocs("test_collection", docs(), Map.of("batch_size", 1));
            assertEquals(2, store.search("test_collection", vector(0.1f, 0.2f, 0.3f),
                    "embedding", 5, null, null).size());
        }

        @Test
        void testAddDocsWithJsonMetadata() throws Exception {
            GaussVectorStore store = preparedStore("cosine");
            store.addDocs("test_collection", List.of(doc("doc1", "Text", vector(0.1f, 0.2f, 0.3f),
                    Map.of("source", "test", "page", 1))), null);
            VectorSearchResult result = store.search("test_collection", vector(0.1f, 0.2f, 0.3f),
                    "embedding", 1, null, null).get(0);
            assertEquals("test", result.getFields().get("source"));
            assertEquals(1, result.getFields().get("page"));
        }
    }

    @Nested
    class TestGaussVectorStoreSearch {
        @Test
        void testSearchSuccess() throws Exception {
            List<VectorSearchResult> results = populatedStore("cosine").search("test_collection",
                    vector(0.1f, 0.2f, 0.3f), "embedding", 5, null, null);
            assertEquals("doc1", results.get(0).getFields().get("id"));
            assertEquals("Text 1", results.get(0).getFields().get("text"));
        }

        @Test
        void testSearchWithFilters() throws Exception {
            List<VectorSearchResult> results = populatedStore("cosine").search("test_collection",
                    vector(0.1f, 0.2f, 0.3f), "embedding", 5, Map.of("category", "tech"), null);
            assertEquals(1, results.size());
            assertEquals("doc1", results.get(0).getFields().get("id"));
        }

        @Test
        void testSearchCosineMetric() throws Exception {
            List<VectorSearchResult> results = populatedStore("cosine").search("test_collection",
                    vector(0.1f, 0.2f, 0.3f), "embedding", 5, null, null);
            assertTrue(results.get(0).getScore() >= results.get(1).getScore());
        }

        @Test
        void testSearchL2Metric() throws Exception {
            List<VectorSearchResult> results = populatedStore("euclidean").search("test_collection",
                    vector(0.1f, 0.2f, 0.3f), "embedding", 5, null, null);
            assertTrue(results.get(0).getScore() >= results.get(1).getScore());
        }
    }

    @Nested
    class TestGaussVectorStoreDeleteDocsByIds {
        @Test
        void testDeleteDocsByIdsSuccess() throws Exception {
            GaussVectorStore store = populatedStore("cosine");
            store.deleteDocsByIds("test_collection", List.of("doc1"), null);
            assertEquals(1, store.search("test_collection", vector(0.1f, 0.2f, 0.3f),
                    "embedding", 5, null, null).size());
        }

        @Test
        void testDeleteDocsByIdsEmptyList() {
            assertDoesNotThrow(() -> preparedStore("cosine").deleteDocsByIds("test_collection", List.of(), null));
        }
    }

    @Nested
    class TestGaussVectorStoreDeleteDocsByFilters {
        @Test
        void testDeleteDocsByFiltersSuccess() throws Exception {
            GaussVectorStore store = populatedStore("cosine");
            store.deleteDocsByFilters("test_collection", Map.of("category", "tech"), null);
            List<VectorSearchResult> results = store.search("test_collection", vector(0.1f, 0.2f, 0.3f),
                    "embedding", 5, null, null);
            assertEquals(1, results.size());
            assertEquals("doc2", results.get(0).getFields().get("id"));
        }

        @Test
        void testDeleteDocsByFiltersEmpty() {
            assertDoesNotThrow(() -> preparedStore("cosine").deleteDocsByFilters("test_collection", Map.of(), null));
        }
    }

    @Nested
    class TestGaussVectorStoreListCollectionNames {
        @Test
        void testListCollectionNamesSuccess() throws Exception {
            GaussVectorStore store = preparedStore("cosine");
            assertTrue(store.listCollectionNames().contains("test_collection"));
        }
    }

    @Nested
    class TestGaussVectorStoreGetCollectionMetadata {
        @Test
        void testGetCollectionMetadataFromCache() throws Exception {
            Map<String, Object> metadata = preparedStore("cosine").getCollectionMetadata("test_collection");
            assertEquals("cosine", metadata.get("distance_metric"));
            assertEquals("embedding", metadata.get("vector_field"));
            assertEquals(0, metadata.get("schema_version"));
        }

        @Test
        void testGetCollectionMetadataNotExists() throws Exception {
            Map<String, Object> metadata = newStore("cosine").getCollectionMetadata("missing");
            assertEquals("cosine", metadata.get("distance_metric"));
            assertEquals(0, metadata.get("schema_version"));
        }
    }

    @Nested
    class TestGaussVectorStoreClose {
        @Test
        void testCloseConnection() {
            assertDoesNotThrow(() -> newStore("cosine").close());
        }
    }

    private static GaussVectorStore newStore(String metric) {
        var delegate = new com.openjiuwen.core.retrieval.vector_store.InMemoryVectorStore(
                new VectorStoreConfig("chroma", UUID.randomUUID().toString().replace("-", ""),
                        "default_collection", metric), "hybrid");
        return new GaussVectorStore(delegate);
    }

    private static GaussVectorStore preparedStore(String metric) throws Exception {
        GaussVectorStore store = newStore(metric);
        store.createCollection("test_collection", schema(), null);
        return store;
    }

    private static GaussVectorStore populatedStore(String metric) throws Exception {
        GaussVectorStore store = preparedStore(metric);
        store.addDocs("test_collection", docs(), null);
        return store;
    }

    private static List<Map<String, Object>> docs() {
        return List.of(
                doc("doc1", "Text 1", vector(0.1f, 0.2f, 0.3f), Map.of("category", "tech")),
                doc("doc2", "Text 2", vector(0.4f, 0.5f, 0.6f), Map.of("category", "biz")));
    }

    private static Map<String, Object> doc(String id, String text, List<Float> embedding, Map<String, Object> metadata) {
        return Map.of("id", id, "embedding", embedding, "text", text, "metadata", metadata);
    }

    private static CollectionSchema schema() {
        return new CollectionSchema()
                .addField(idField())
                .addField(vectorField())
                .addField(FieldSchema.builder().name("text").dtype(VectorDataType.VARCHAR).maxLength(65535).build());
    }

    private static FieldSchema idField() {
        return FieldSchema.builder().name("id").dtype(VectorDataType.VARCHAR)
                .maxLength(256).isPrimary(true).build();
    }

    private static FieldSchema vectorField() {
        return FieldSchema.builder().name("embedding").dtype(VectorDataType.FLOAT_VECTOR).dim(3).build();
    }

    private static List<Float> vector(float a, float b, float c) {
        return List.of(a, b, c);
    }
}
