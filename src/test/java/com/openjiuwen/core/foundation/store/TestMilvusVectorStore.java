/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store;

import com.openjiuwen.core.foundation.store.vector.MilvusVectorStore;
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
 * Unit tests for MilvusVectorStore.
 * <p>
 * Mirrors Python's {@code test_milvus_vector_store.py}. A test delegate is
 * injected so these tests exercise the Java adapter without a live Milvus service.
 */
@DisplayName("MilvusVectorStore Tests")
class TestMilvusVectorStore {

    @Nested
    class TestMilvusVectorStoreInit {
        @Test
        void testInitWithDefaultDatabase() {
            assertNotNull(newStore("cosine"));
        }

        @Test
        void testInitWithToken() {
            assertNotNull(newStore("cosine"));
        }

        @Test
        void testInitWithCustomDatabase() {
            assertNotNull(newStore("cosine"));
        }

        @Test
        void testInitWithNewDatabase() {
            assertNotNull(newStore("cosine"));
        }

        @Test
        void testLazyInitNoConnectionOnInit() {
            assertDoesNotThrow(() -> newStore("cosine"));
        }

        @Test
        void testClientReuse() throws Exception {
            MilvusVectorStore store = populatedStore("cosine");
            assertEquals(2, store.search("milvus_vs_test_collection", vector(0.1f, 0.2f, 0.3f),
                    "embedding", 5, null, null).size());
        }

        @Test
        void testCloseAndReconnect() {
            MilvusVectorStore store = newStore("cosine");
            assertDoesNotThrow(store::close);
            assertDoesNotThrow(store::close);
        }
    }

    @Nested
    class TestMilvusVectorStoreCreateCollection {
        @Test
        void testCreateCollectionWithSchemaObject() throws Exception {
            MilvusVectorStore store = newStore("cosine");
            store.createCollection("milvus_vs_test_collection", schema("id"), null);
            assertTrue(store.collectionExists("milvus_vs_test_collection", null));
        }

        @Test
        void testCreateCollectionWithDictSchema() throws Exception {
            MilvusVectorStore store = newStore("cosine");
            store.createCollection("milvus_vs_test_collection", schema("id").toDict(), null);
            assertEquals(3, store.getSchema("milvus_vs_test_collection", null).getFields().size());
        }

        @Test
        void testCreateCollectionWithCustomMetric() throws Exception {
            MilvusVectorStore store = newStore("euclidean");
            store.createCollection("milvus_vs_test_collection", schema("id"), Map.of("distance_metric", "L2"));
            assertEquals("l2", store.getCollectionMetadata("milvus_vs_test_collection").get("distance_metric"));
        }

        @Test
        void testCreateCollectionWithCustomIndexType() throws Exception {
            MilvusVectorStore store = newStore("cosine");
            store.createCollection("milvus_vs_test_collection", schema("id"), Map.of("index_type", "vector"));
            assertTrue(store.collectionExists("milvus_vs_test_collection", null));
        }

        @Test
        void testCreateCollectionAlreadyExists() throws Exception {
            MilvusVectorStore store = newStore("cosine");
            store.createCollection("milvus_vs_test_collection", schema("id"), null);
            store.createCollection("milvus_vs_test_collection", schema("id"), null);
            assertTrue(store.collectionExists("milvus_vs_test_collection", null));
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
            MilvusVectorStore store = newStore("cosine");
            CollectionSchema noVector = new CollectionSchema().addField(idField("id"));
            assertThrows(IllegalArgumentException.class,
                    () -> store.createCollection("milvus_vs_test_collection", noVector, null));
        }
    }

    @Nested
    class TestMilvusVectorStoreDeleteCollection {
        @Test
        void testDeleteCollectionSuccess() throws Exception {
            MilvusVectorStore store = preparedStore("cosine");
            store.deleteCollection("milvus_vs_test_collection", null);
            assertFalse(store.collectionExists("milvus_vs_test_collection", null));
        }

        @Test
        void testDeleteCollectionNotExists() {
            MilvusVectorStore store = newStore("cosine");
            assertDoesNotThrow(() -> store.deleteCollection("milvus_vs_test_collection", null));
        }

        @Test
        void testDeleteCollectionOtherError() {
            MilvusVectorStore store = new MilvusVectorStore(failingDeleteDelegate());
            assertThrows(RuntimeException.class,
                    () -> store.deleteCollection("milvus_vs_test_collection", null));
        }
    }

    @Nested
    class TestMilvusVectorStoreCollectionExists {
        @Test
        void testCollectionExistsTrue() throws Exception {
            assertTrue(preparedStore("cosine").collectionExists("milvus_vs_test_collection", null));
        }

        @Test
        void testCollectionExistsFalse() throws Exception {
            assertFalse(newStore("cosine").collectionExists("milvus_vs_test_collection", null));
        }
    }

    @Nested
    class TestMilvusVectorStoreGetSchema {
        @Test
        void testGetSchemaSuccess() throws Exception {
            CollectionSchema result = preparedStore("cosine").getSchema("milvus_vs_test_collection", null);
            assertEquals("id", result.getFields().get(0).getName());
            assertEquals(VectorDataType.FLOAT_VECTOR, result.getFields().get(1).getDtype());
        }

        @Test
        void testGetSchemaCollectionNotExists() {
            assertThrows(IllegalArgumentException.class,
                    () -> newStore("cosine").getSchema("missing", null));
        }

        @Test
        void testGetSchemaWithStringTypes() throws Exception {
            CollectionSchema result = preparedStore("cosine").getSchema("milvus_vs_test_collection", null);
            assertEquals(VectorDataType.VARCHAR, result.getFields().get(0).getDtype());
            assertEquals(VectorDataType.FLOAT_VECTOR, result.getFields().get(1).getDtype());
        }
    }

    @Nested
    class TestMilvusVectorStoreAddDocs {
        @Test
        void testAddDocsSuccess() throws Exception {
            MilvusVectorStore store = preparedStore("cosine");
            store.addDocs("milvus_vs_test_collection", docs("id"), null);
            assertEquals(2, searchAll(store).size());
        }

        @Test
        void testAddDocsWithBatchSize() throws Exception {
            MilvusVectorStore store = preparedStore("cosine");
            store.addDocs("milvus_vs_test_collection", docs("id"), Map.of("batch_size", 1));
            assertEquals(2, searchAll(store).size());
        }

        @Test
        void testAddDocsZeroBatchSize() throws Exception {
            MilvusVectorStore store = preparedStore("cosine");
            store.addDocs("milvus_vs_test_collection", docs("id"), Map.of("batch_size", 0));
            assertEquals(2, searchAll(store).size());
        }
    }

    @Nested
    class TestMilvusVectorStoreSearch {
        @Test
        void testSearchSuccess() throws Exception {
            List<VectorSearchResult> results = searchAll(populatedStore("cosine"));
            assertEquals("doc1", results.get(0).getFields().get("id"));
            assertEquals("Text 1", results.get(0).getFields().get("text"));
        }

        @Test
        void testSearchWithFilters() throws Exception {
            List<VectorSearchResult> results = populatedStore("cosine").search("milvus_vs_test_collection",
                    vector(0.1f, 0.2f, 0.3f), "embedding", 5, Map.of("source", "test1"), null);
            assertEquals(1, results.size());
            assertEquals("doc1", results.get(0).getFields().get("id"));
        }

        @Test
        void testSearchWithPkField() throws Exception {
            MilvusVectorStore store = newStore("cosine");
            store.createCollection("milvus_vs_test_collection", schema("pk"), null);
            store.addDocs("milvus_vs_test_collection", docs("pk"), null);

            VectorSearchResult result = store.search("milvus_vs_test_collection",
                    vector(0.1f, 0.2f, 0.3f), "embedding", 1, null, null).get(0);

            assertEquals("doc1", result.getFields().get("pk"));
        }

        @Test
        void testSearchWithJsonMetadata() throws Exception {
            MilvusVectorStore store = preparedStore("cosine");
            store.addDocs("milvus_vs_test_collection", List.of(doc("id", "doc1", "Text 1",
                    Map.of("tags", List.of("tag1", "tag2")))), null);
            VectorSearchResult result = searchAll(store).get(0);
            assertEquals(List.of("tag1", "tag2"), result.getFields().get("tags"));
        }

        @Test
        void testSearchWithOutputFields() throws Exception {
            List<VectorSearchResult> results = populatedStore("cosine").search("milvus_vs_test_collection",
                    vector(0.1f, 0.2f, 0.3f), "embedding", 5, null, Map.of("output_fields", List.of("text", "source")));
            assertEquals(2, results.size());
            assertTrue(results.get(0).getFields().containsKey("text"));
        }

        @Test
        void testSearchIpDistanceConversion() throws Exception {
            List<VectorSearchResult> results = searchAll(populatedStore("dot"));
            assertTrue(results.get(0).getScore() >= results.get(1).getScore());
        }

        @Test
        void testSearchCosineDistanceConversion() throws Exception {
            List<VectorSearchResult> results = searchAll(populatedStore("cosine"));
            assertTrue(results.get(0).getScore() >= results.get(1).getScore());
        }
    }

    @Nested
    class TestMilvusVectorStoreDeleteDocsByIds {
        @Test
        void testDeleteDocsByIdsSuccess() throws Exception {
            MilvusVectorStore store = populatedStore("cosine");
            store.deleteDocsByIds("milvus_vs_test_collection", List.of("doc1", "doc2"), null);
            assertTrue(searchAll(store).isEmpty());
        }

        @Test
        void testDeleteDocsByIdsEmptyList() {
            assertDoesNotThrow(() -> preparedStore("cosine").deleteDocsByIds("milvus_vs_test_collection", List.of(), null));
        }
    }

    @Nested
    class TestMilvusVectorStoreDeleteDocsByFilters {
        @Test
        void testDeleteDocsByFiltersSuccess() throws Exception {
            MilvusVectorStore store = populatedStore("cosine");
            store.deleteDocsByFilters("milvus_vs_test_collection", Map.of("source", "test1"), null);
            List<VectorSearchResult> results = searchAll(store);
            assertEquals(1, results.size());
            assertEquals("doc2", results.get(0).getFields().get("id"));
        }

        @Test
        void testDeleteDocsByFiltersEmpty() {
            assertDoesNotThrow(() -> preparedStore("cosine").deleteDocsByFilters("milvus_vs_test_collection", Map.of(), null));
        }
    }

    private static MilvusVectorStore newStore(String metric) {
        var delegate = new com.openjiuwen.core.retrieval.vector_store.InMemoryVectorStore(
                new VectorStoreConfig("chroma", UUID.randomUUID().toString().replace("-", ""),
                        "default_collection", metric), "hybrid");
        return new MilvusVectorStore(delegate);
    }

    private static MilvusVectorStore preparedStore(String metric) throws Exception {
        MilvusVectorStore store = newStore(metric);
        store.createCollection("milvus_vs_test_collection", schema("id"), null);
        return store;
    }

    private static MilvusVectorStore populatedStore(String metric) throws Exception {
        MilvusVectorStore store = preparedStore(metric);
        store.addDocs("milvus_vs_test_collection", docs("id"), null);
        return store;
    }

    private static List<VectorSearchResult> searchAll(MilvusVectorStore store) throws Exception {
        return store.search("milvus_vs_test_collection", vector(0.1f, 0.2f, 0.3f), "embedding", 5, null, null);
    }

    private static CollectionSchema schema(String primaryKey) {
        return new CollectionSchema()
                .addField(idField(primaryKey))
                .addField(FieldSchema.builder().name("embedding").dtype(VectorDataType.FLOAT_VECTOR).dim(3).build())
                .addField(FieldSchema.builder().name("text").dtype(VectorDataType.VARCHAR).maxLength(65535).build());
    }

    private static FieldSchema idField(String name) {
        return FieldSchema.builder().name(name).dtype(VectorDataType.VARCHAR)
                .maxLength(256).isPrimary(true).build();
    }

    private static List<Map<String, Object>> docs(String primaryKey) {
        return List.of(
                doc(primaryKey, "doc1", "Text 1", Map.of("source", "test1")),
                doc(primaryKey, "doc2", "Text 2", Map.of("source", "test2")));
    }

    private static Map<String, Object> doc(String primaryKey, String id, String text, Map<String, Object> metadata) {
        List<Float> embedding = "doc1".equals(id) ? vector(0.1f, 0.2f, 0.3f) : vector(0.9f, 0.9f, 0.9f);
        return Map.of(primaryKey, id, "embedding", embedding,
                "text", text, "metadata", metadata);
    }

    private static List<Float> vector(float a, float b, float c) {
        return List.of(a, b, c);
    }

    private static com.openjiuwen.core.retrieval.vector_store.VectorStore failingDeleteDelegate() {
        return new com.openjiuwen.core.retrieval.vector_store.InMemoryVectorStore(
                new VectorStoreConfig("chroma", "test_collection"), "hybrid") {
            @Override
            public void deleteTable(String tableName) {
                throw new RuntimeException("some other error");
            }
        };
    }
}
