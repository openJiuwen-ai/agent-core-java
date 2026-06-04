/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.foundation.store;

import com.openjiuwen.core.foundation.store.vector.ChromaVectorStore;

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
 * Unit tests for ChromaVectorStore.
 * <p>
 * Mirrors Python's {@code test_chroma_vector_store.py}.
 */
@DisplayName("ChromaVectorStore Tests")
class TestChromaVectorStore {

    @Nested
    @DisplayName("Create Collection Tests")
    class TestChromaVectorStoreCreateCollection {

        @Test
        void testCreateCollectionWithSchemaObject() throws Exception {
            ChromaVectorStore store = newStore("cosine");
            store.createCollection("test_collection", schema(), null);

            assertTrue(store.collectionExists("test_collection", null));
            assertEquals("embedding", store.getCollectionMetadata("test_collection").get("vector_field"));
        }

        @Test
        void testCreateCollectionWithDictSchema() throws Exception {
            ChromaVectorStore store = newStore("cosine");
            store.createCollection("test_collection", schema().toDict(), null);

            assertEquals(3, store.getSchema("test_collection", null).getFields().size());
        }

        @Test
        void testCreateCollectionWithCustomDistanceMetric() throws Exception {
            ChromaVectorStore store = newStore("euclidean");
            store.createCollection("test_collection", schema(), Map.of("distance_metric", "l2"));

            assertEquals("l2", store.getCollectionMetadata("test_collection").get("distance_metric"));
        }

        @Test
        void testCreateCollectionWithDotMetric() throws Exception {
            ChromaVectorStore store = newStore("dot");
            store.createCollection("test_collection", schema(), Map.of("distance_metric", "dot"));

            assertEquals("dot", store.getCollectionMetadata("test_collection").get("distance_metric"));
        }

        @Test
        void testCreateCollectionMissingPrimaryKey() {
            ChromaVectorStore store = newStore("cosine");
            CollectionSchema noPrimary = new CollectionSchema()
                    .addField(vectorField());

            assertThrows(IllegalArgumentException.class,
                    () -> store.createCollection("test_collection", noPrimary, null));
        }

        @Test
        void testCreateCollectionMissingVectorField() {
            ChromaVectorStore store = newStore("cosine");
            CollectionSchema noVector = new CollectionSchema()
                    .addField(idField());

            assertThrows(IllegalArgumentException.class,
                    () -> store.createCollection("test_collection", noVector, null));
        }
    }

    @Nested
    @DisplayName("Delete Collection Tests")
    class TestChromaVectorStoreDeleteCollection {

        @Test
        void testDeleteCollectionSuccess() throws Exception {
            ChromaVectorStore store = newStore("cosine");
            store.createCollection("test_collection", schema(), null);

            store.deleteCollection("test_collection", null);

            assertFalse(store.collectionExists("test_collection", null));
        }

        @Test
        void testDeleteCollectionFailure() {
            ChromaVectorStore store = new ChromaVectorStore(failingDeleteDelegate());

            assertThrows(RuntimeException.class, () -> store.deleteCollection("test_collection", null));
        }
    }

    @Nested
    @DisplayName("Collection Exists Tests")
    class TestChromaVectorStoreCollectionExists {

        @Test
        void testCollectionExistsTrue() throws Exception {
            ChromaVectorStore store = newStore("cosine");
            store.createCollection("test_collection", schema(), null);

            assertTrue(store.collectionExists("test_collection", null));
        }

        @Test
        void testCollectionExistsFalse() throws Exception {
            ChromaVectorStore store = newStore("cosine");

            assertFalse(store.collectionExists("test_collection", null));
        }
    }

    @Nested
    @DisplayName("Get Schema Tests")
    class TestChromaVectorStoreGetSchema {

        @Test
        void testGetSchemaFromMetadata() throws Exception {
            ChromaVectorStore store = newStore("cosine");
            store.createCollection("test_collection", schema(), null);

            CollectionSchema result = store.getSchema("test_collection", null);

            assertEquals("id", result.getFields().get(0).getName());
            assertTrue(result.getFields().get(0).isPrimary());
        }

        @Test
        void testGetSchemaDefaultFallback() throws Exception {
            ChromaVectorStore store = newStore("cosine");

            CollectionSchema result = store.getSchema("default_collection", null);

            assertTrue(result.hasField("id"));
            assertTrue(result.hasField("embedding"));
            assertTrue(result.hasField("text"));
        }

        @Test
        void testGetSchemaCollectionNotExists() {
            ChromaVectorStore store = newStore("cosine");

            assertThrows(IllegalArgumentException.class, () -> store.getSchema("missing", null));
        }
    }

    @Nested
    @DisplayName("Add Docs Tests")
    class TestChromaVectorStoreAddDocs {

        @Test
        void testAddDocsSuccess() throws Exception {
            ChromaVectorStore store = preparedStore("cosine");
            store.addDocs("test_collection", docs(), null);

            assertEquals(2, store.search("test_collection", vector(0.1f, 0.2f, 0.3f), "embedding", 5, null, null).size());
        }

        @Test
        void testAddDocsMissingId() throws Exception {
            ChromaVectorStore store = preparedStore("cosine");
            Map<String, Object> doc = Map.of("embedding", vector(0.1f, 0.2f, 0.3f), "text", "Missing id");

            assertThrows(IllegalArgumentException.class, () -> store.addDocs("test_collection", List.of(doc), null));
        }

        @Test
        void testAddDocsMissingEmbedding() throws Exception {
            ChromaVectorStore store = preparedStore("cosine");
            Map<String, Object> doc = Map.of("id", "doc1", "text", "Missing embedding");

            assertThrows(IllegalArgumentException.class, () -> store.addDocs("test_collection", List.of(doc), null));
        }

        @Test
        void testAddDocsWithBatchSize() throws Exception {
            ChromaVectorStore store = preparedStore("cosine");

            store.addDocs("test_collection", docs(), Map.of("batch_size", 1));

            assertEquals(2, store.search("test_collection", vector(0.1f, 0.2f, 0.3f), "embedding", 5, null, null).size());
        }

        @Test
        void testAddDocsWithListMetadata() throws Exception {
            ChromaVectorStore store = preparedStore("cosine");
            store.addDocs("test_collection", List.of(doc("doc1", "Text 1", vector(0.1f, 0.2f, 0.3f),
                    Map.of("tags", List.of("tag1", "tag2")))), null);

            VectorSearchResult result = store.search("test_collection", vector(0.1f, 0.2f, 0.3f),
                    "embedding", 1, null, null).get(0);

            assertEquals(List.of("tag1", "tag2"), result.getFields().get("tags"));
        }

        @Test
        void testAddDocsZeroBatchSize() throws Exception {
            ChromaVectorStore store = preparedStore("cosine");

            store.addDocs("test_collection", docs(), Map.of("batch_size", 0));

            assertEquals(2, store.search("test_collection", vector(0.1f, 0.2f, 0.3f), "embedding", 5, null, null).size());
        }
    }

    @Nested
    @DisplayName("Search Tests")
    class TestChromaVectorStoreSearch {

        @Test
        void testSearchSuccess() throws Exception {
            ChromaVectorStore store = populatedStore("cosine");

            List<VectorSearchResult> results = store.search("test_collection",
                    vector(0.1f, 0.2f, 0.3f), "embedding", 5, null, null);

            assertEquals("doc1", results.get(0).getFields().get("id"));
            assertEquals("Text 1", results.get(0).getFields().get("text"));
            assertTrue(results.get(0).getScore() > 0);
        }

        @Test
        void testSearchWithFilters() throws Exception {
            ChromaVectorStore store = populatedStore("cosine");

            List<VectorSearchResult> results = store.search("test_collection",
                    vector(0.1f, 0.2f, 0.3f), "embedding", 5, Map.of("source", "test2"), null);

            assertEquals(1, results.size());
            assertEquals("doc2", results.get(0).getFields().get("id"));
        }

        @Test
        void testSearchCosineDistanceConversion() throws Exception {
            ChromaVectorStore store = populatedStore("cosine");

            List<VectorSearchResult> results = store.search("test_collection",
                    vector(0.1f, 0.2f, 0.3f), "embedding", 2, null, null);

            assertTrue(results.get(0).getScore() >= results.get(1).getScore());
        }

        @Test
        void testSearchL2DistanceConversion() throws Exception {
            ChromaVectorStore store = populatedStore("euclidean");

            List<VectorSearchResult> results = store.search("test_collection",
                    vector(0.1f, 0.2f, 0.3f), "embedding", 2, null, null);

            assertTrue(results.get(0).getScore() >= results.get(1).getScore());
        }

        @Test
        void testSearchIpDistanceConversion() throws Exception {
            ChromaVectorStore store = populatedStore("dot");

            List<VectorSearchResult> results = store.search("test_collection",
                    vector(0.1f, 0.2f, 0.3f), "embedding", 2, null, null);

            assertTrue(results.get(0).getScore() >= results.get(1).getScore());
        }

        @Test
        void testSearchEmptyResults() throws Exception {
            ChromaVectorStore store = preparedStore("cosine");

            assertTrue(store.search("test_collection", vector(0.1f, 0.2f, 0.3f),
                    "embedding", 5, null, null).isEmpty());
        }

        @Test
        void testSearchWithJsonMetadata() throws Exception {
            ChromaVectorStore store = preparedStore("cosine");
            store.addDocs("test_collection", List.of(doc("doc1", "Text 1", vector(0.1f, 0.2f, 0.3f),
                    Map.of("tags", List.of("tag1", "tag2")))), null);

            VectorSearchResult result = store.search("test_collection", vector(0.1f, 0.2f, 0.3f),
                    "embedding", 1, null, null).get(0);

            assertEquals(List.of("tag1", "tag2"), result.getFields().get("tags"));
        }

        @Test
        void testSearchWithInvalidJsonMetadata() throws Exception {
            ChromaVectorStore store = preparedStore("cosine");
            store.addDocs("test_collection", List.of(doc("doc1", "Text 1", vector(0.1f, 0.2f, 0.3f),
                    Map.of("tags", "invalid json"))), null);

            VectorSearchResult result = store.search("test_collection", vector(0.1f, 0.2f, 0.3f),
                    "embedding", 1, null, null).get(0);

            assertEquals("invalid json", result.getFields().get("tags"));
        }
    }

    @Nested
    @DisplayName("Delete Docs By IDs Tests")
    class TestChromaVectorStoreDeleteDocsByIds {

        @Test
        void testDeleteDocsByIdsSuccess() throws Exception {
            ChromaVectorStore store = populatedStore("cosine");

            store.deleteDocsByIds("test_collection", List.of("doc1"), null);

            List<VectorSearchResult> results = store.search("test_collection",
                    vector(0.1f, 0.2f, 0.3f), "embedding", 5, null, null);
            assertEquals(1, results.size());
            assertEquals("doc2", results.get(0).getFields().get("id"));
        }
    }

    @Nested
    @DisplayName("Delete Docs By Filters Tests")
    class TestChromaVectorStoreDeleteDocsByFilters {

        @Test
        void testDeleteDocsByFiltersSuccess() throws Exception {
            ChromaVectorStore store = populatedStore("cosine");

            store.deleteDocsByFilters("test_collection", Map.of("source", "test1"), null);

            assertEquals(1, store.search("test_collection", vector(0.1f, 0.2f, 0.3f),
                    "embedding", 5, null, null).size());
        }
    }

    private static ChromaVectorStore newStore(String metric) {
        return new ChromaVectorStore(Map.of(
                "database_name", UUID.randomUUID().toString().replace("-", ""),
                "collection_name", "default_collection",
                "distance_metric", metric));
    }

    private static ChromaVectorStore preparedStore(String metric) throws Exception {
        ChromaVectorStore store = newStore(metric);
        store.createCollection("test_collection", schema(), null);
        return store;
    }

    private static ChromaVectorStore populatedStore(String metric) throws Exception {
        ChromaVectorStore store = preparedStore(metric);
        store.addDocs("test_collection", docs(), null);
        return store;
    }

    private static List<Map<String, Object>> docs() {
        return List.of(
                doc("doc1", "Text 1", vector(0.1f, 0.2f, 0.3f), Map.of("source", "test1")),
                doc("doc2", "Text 2", vector(0.4f, 0.5f, 0.6f), Map.of("source", "test2")));
    }

    private static Map<String, Object> doc(String id, String text, List<Float> embedding, Map<String, Object> extra) {
        return Map.of(
                "id", id,
                "embedding", embedding,
                "text", text,
                "metadata", extra);
    }

    private static CollectionSchema schema() {
        return new CollectionSchema()
                .addField(idField())
                .addField(vectorField())
                .addField(FieldSchema.builder()
                        .name("text")
                        .dtype(VectorDataType.VARCHAR)
                        .maxLength(65535)
                        .build());
    }

    private static FieldSchema idField() {
        return FieldSchema.builder()
                .name("id")
                .dtype(VectorDataType.VARCHAR)
                .maxLength(256)
                .isPrimary(true)
                .build();
    }

    private static FieldSchema vectorField() {
        return FieldSchema.builder()
                .name("embedding")
                .dtype(VectorDataType.FLOAT_VECTOR)
                .dim(3)
                .build();
    }

    private static List<Float> vector(float a, float b, float c) {
        return List.of(a, b, c);
    }

    private static com.openjiuwen.core.retrieval.vector_store.VectorStore failingDeleteDelegate() {
        return new com.openjiuwen.core.retrieval.vector_store.InMemoryVectorStore(
                new VectorStoreConfig("chroma", "test_collection"), "hybrid") {
            @Override
            public void deleteTable(String tableName) {
                throw new RuntimeException("Delete failed");
            }
        };
    }
}
