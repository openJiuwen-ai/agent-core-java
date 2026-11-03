/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.store.CollectionSchema;
import com.openjiuwen.core.foundation.store.FieldSchema;
import com.openjiuwen.core.foundation.store.VectorDataType;
import com.openjiuwen.core.foundation.store.VectorSearchResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <p>Mirrors Python's Chroma vector-store unit tests in
 * {@code tests/unit_tests/core/foundation/store/test_chroma_vector_store.py}.</p>
 */
class ChromaVectorStorePythonParityTest {

    @Test
    void testCreateCollectionWithSchemaObject() {
        ChromaVectorStore store = new ChromaVectorStore();

        assertDoesNotThrow(() -> store.createCollection("test_collection", fullSchema(), Map.of()).join());

        assertTrue(store.collectionExists("test_collection", Map.of()).join());
    }

    @Test
    void testCreateCollectionWithDictSchema() {
        ChromaVectorStore store = new ChromaVectorStore();
        Map<String, Object> schema = Map.of(
                "fields", List.of(
                        Map.of("name", "id", "type", "VARCHAR", "max_length", 256, "is_primary", true),
                        Map.of("name", "embedding", "type", "FLOAT_VECTOR", "dim", 768),
                        Map.of("name", "text", "type", "VARCHAR", "max_length", 65535)
                ),
                "description", "Test collection",
                "enable_dynamic_field", false
        );

        assertDoesNotThrow(() -> store.createCollection("test_collection", schema, Map.of()).join());

        assertTrue(store.collectionExists("test_collection", Map.of()).join());
    }

    @Test
    void testCreateCollectionWithCustomDistanceMetric() {
        ChromaVectorStore store = new ChromaVectorStore();

        store.createCollection("test_collection", minimalSchema(), Map.of("distance_metric", "l2")).join();

        assertEquals("l2", store.getCollectionMetadata("test_collection").join().get("distance_metric"));
    }

    @Test
    void testCreateCollectionWithDotMetric() {
        ChromaVectorStore store = new ChromaVectorStore();

        store.createCollection("test_collection", minimalSchema(), Map.of("distance_metric", "dot")).join();

        assertEquals("ip", store.getCollectionMetadata("test_collection").join().get("distance_metric"));
    }

    @Test
    void testCreateCollectionMissingPrimaryKey() {
        ChromaVectorStore store = new ChromaVectorStore();

        BaseError error = assertBaseError(
                () -> store.createCollection("test_collection", schemaWithoutPrimary(), Map.of()).join());

        assertEquals(StatusCode.STORE_VECTOR_SCHEMA_INVALID, error.getStatus());
    }

    @Test
    void testCreateCollectionMissingVectorField() {
        ChromaVectorStore store = new ChromaVectorStore();

        BaseError error = assertBaseError(
                () -> store.createCollection("test_collection", schemaWithoutVector(), Map.of()).join());

        assertEquals(StatusCode.STORE_VECTOR_SCHEMA_INVALID, error.getStatus());
    }

    @Test
    void testDeleteCollectionSuccess() {
        ChromaVectorStore store = new ChromaVectorStore();
        store.createCollection("test_collection", minimalSchema(), Map.of()).join();

        assertDoesNotThrow(() -> store.deleteCollection("test_collection", Map.of()).join());

        assertFalse(store.collectionExists("test_collection", Map.of()).join());
    }

    @Test
    void testDeleteCollectionFailure() {
        ChromaVectorStore store = new ChromaVectorStore(new ThrowingDeleteClientAdapter());

        CompletionException exception = assertThrows(CompletionException.class,
                () -> store.deleteCollection("test_collection", Map.of()).join());

        assertInstanceOf(RuntimeException.class, exception.getCause());
        assertEquals("Delete failed", exception.getCause().getMessage());
    }

    @Test
    void testCollectionExistsTrue() {
        ChromaVectorStore store = new ChromaVectorStore();
        store.createCollection("test_collection", minimalSchema(), Map.of()).join();

        boolean result = store.collectionExists("test_collection", Map.of()).join();

        assertTrue(result);
    }

    @Test
    void testCollectionExistsFalse() {
        ChromaVectorStore store = new ChromaVectorStore();

        boolean result = store.collectionExists("test_collection", Map.of()).join();

        assertFalse(result);
    }

    @Test
    void testGetSchemaFromMetadata() {
        ChromaVectorStore store = new ChromaVectorStore();
        store.createCollection("test_collection", fullSchema(), Map.of()).join();

        CollectionSchema schema = store.getSchema("test_collection", Map.of()).join();

        assertEquals(3, schema.getFields().size());
        assertEquals("id", schema.getFields().get(0).getName());
        assertEquals(VectorDataType.VARCHAR, schema.getFields().get(0).getDtype());
        assertTrue(schema.getFields().get(0).isPrimary());
    }

    @Test
    void testGetSchemaDefaultFallback() {
        ChromaVectorStore store = new ChromaVectorStore(new MetadataOnlyClientAdapter(Map.of()));

        CollectionSchema schema = store.getSchema("test_collection", Map.of()).join();

        assertTrue(schema.getFields().size() >= 3);
        assertTrue(schema.hasField("id"));
        assertTrue(schema.hasField("embedding"));
        assertTrue(schema.hasField("text"));
    }

    @Test
    void testGetSchemaCollectionNotExists() {
        ChromaVectorStore store = new ChromaVectorStore();

        BaseError error = assertBaseError(() -> store.getSchema("non_existent_collection", Map.of()).join());

        assertEquals(StatusCode.STORE_VECTOR_COLLECTION_NOT_FOUND, error.getStatus());
    }

    @Test
    void testAddDocsSuccess() {
        ChromaVectorStore store = storeWithFullSchema("test_collection");

        store.addDocs("test_collection", List.of(doc("doc1", List.of(0.1d, 0.2d, 0.3d), "Test document")),
                Map.of()).join();

        List<VectorSearchResult> results = store.search(
                "test_collection", List.of(0.1d, 0.2d, 0.3d), "embedding", 5, null, Map.of()).join();
        assertEquals(1, results.size());
        assertEquals("doc1", results.get(0).getFields().get("id"));
    }

    @Test
    void testAddDocsMissingId() {
        ChromaVectorStore store = storeWithFullSchema("test_collection");

        BaseError error = assertBaseError(() -> store.addDocs(
                "test_collection",
                List.of(Map.of("embedding", List.of(0.1d, 0.2d, 0.3d), "text", "Test document")),
                Map.of()).join());

        assertEquals(StatusCode.STORE_VECTOR_DOC_INVALID, error.getStatus());
    }

    @Test
    void testAddDocsMissingEmbedding() {
        ChromaVectorStore store = storeWithFullSchema("test_collection");

        BaseError error = assertBaseError(() -> store.addDocs(
                "test_collection",
                List.of(Map.of("id", "doc1", "text", "Test document")),
                Map.of()).join());

        assertEquals(StatusCode.STORE_VECTOR_DOC_INVALID, error.getStatus());
    }

    @Test
    void testAddDocsWithBatchSize() {
        ChromaVectorStore store = storeWithFullSchema("test_collection");

        store.addDocs("test_collection", List.of(
                doc("doc1", List.of(1.0d, 0.0d, 0.0d), "Text 1"),
                doc("doc2", List.of(0.0d, 1.0d, 0.0d), "Text 2")
        ), Map.of("batch_size", 1)).join();

        List<VectorSearchResult> results = store.search(
                "test_collection", List.of(1.0d, 0.0d, 0.0d), "embedding", 5, null, Map.of()).join();
        assertEquals(2, results.size());
    }

    @Test
    void testAddDocsWithListMetadata() {
        ChromaVectorStore store = storeWithFullSchema("test_collection");
        Map<String, Object> doc = doc("doc1", List.of(0.1d, 0.2d, 0.3d), "Test document");
        doc.put("tags", List.of("tag1", "tag2"));

        store.addDocs("test_collection", List.of(doc), Map.of()).join();

        List<VectorSearchResult> results = store.search(
                "test_collection", List.of(0.1d, 0.2d, 0.3d), "embedding", 5, null, Map.of()).join();
        assertIterableEquals(List.of("tag1", "tag2"), (List<?>) results.get(0).getFields().get("tags"));
    }

    @Test
    void testAddDocsZeroBatchSize() {
        ChromaVectorStore store = storeWithFullSchema("test_collection");

        assertDoesNotThrow(() -> store.addDocs(
                "test_collection",
                List.of(doc("doc1", List.of(0.1d, 0.2d, 0.3d), "Test document")),
                Map.of("batch_size", 0)).join());

        assertEquals(1, store.search("test_collection", List.of(0.1d, 0.2d, 0.3d),
                "embedding", 5, null, Map.of()).join().size());
    }

    @Test
    void testSearchSuccess() {
        ChromaVectorStore store = storeWithFullSchema("test_collection");
        store.addDocs("test_collection", List.of(
                docWithSource("doc1", List.of(1.0d, 0.0d, 0.0d), "Text 1", "test1"),
                docWithSource("doc2", List.of(0.0d, 1.0d, 0.0d), "Text 2", "test2")
        ), Map.of()).join();

        List<VectorSearchResult> results = store.search(
                "test_collection", List.of(1.0d, 0.0d, 0.0d), "embedding", 5, null, Map.of()).join();

        assertEquals(2, results.size());
        assertEquals("doc1", results.get(0).getFields().get("id"));
        assertEquals("Text 1", results.get(0).getFields().get("text"));
        assertTrue(results.get(0).getScore() > 0.0d);
    }

    @Test
    void testSearchWithFilters() {
        ChromaVectorStore store = storeWithFullSchema("test_collection");
        store.addDocs("test_collection", List.of(
                docWithSource("doc1", List.of(1.0d, 0.0d, 0.0d), "Text 1", "test1"),
                docWithSource("doc2", List.of(0.0d, 1.0d, 0.0d), "Text 2", "test2")
        ), Map.of()).join();

        List<VectorSearchResult> results = store.search(
                "test_collection",
                List.of(1.0d, 0.0d, 0.0d),
                "embedding",
                5,
                Map.of("source", "test1"),
                Map.of()).join();

        assertEquals(1, results.size());
    }

    @Test
    void testSearchCosineDistanceConversion() {
        ChromaVectorStore store = new ChromaVectorStore();
        store.createCollection("test_collection", fullSchema(), Map.of("distance_metric", "cosine")).join();
        store.addDocs("test_collection", List.of(
                doc("doc1", List.of(1.0d, 0.0d), "Text 1"),
                doc("doc2", List.of(-1.0d, 0.0d), "Text 2")
        ), Map.of()).join();

        List<VectorSearchResult> results = store.search(
                "test_collection", List.of(1.0d, 0.0d), "embedding", 5, null, Map.of()).join();

        assertEquals(1.0d, results.get(0).getScore());
        assertEquals(0.0d, results.get(1).getScore());
    }

    @Test
    void testSearchL2DistanceConversion() {
        ChromaVectorStore store = new ChromaVectorStore();
        store.createCollection("test_collection", fullSchema(), Map.of("distance_metric", "l2")).join();
        store.addDocs("test_collection", List.of(doc("doc1", List.of(0.1d, 0.2d, 0.3d), "Text 1")),
                Map.of()).join();

        List<VectorSearchResult> results = store.search(
                "test_collection", List.of(0.1d, 0.2d, 0.3d), "embedding", 5, null, Map.of()).join();

        assertEquals(1.0d, results.get(0).getScore());
    }

    @Test
    void testSearchIpDistanceConversion() {
        ChromaVectorStore store = new ChromaVectorStore();
        store.createCollection("test_collection", fullSchema(), Map.of("distance_metric", "ip")).join();
        store.addDocs("test_collection", List.of(doc("doc1", List.of(0.5d, 0.0d, 0.0d), "Text 1")),
                Map.of()).join();

        List<VectorSearchResult> results = store.search(
                "test_collection", List.of(1.0d, 0.0d, 0.0d), "embedding", 5, null, Map.of()).join();

        assertTrue(results.get(0).getScore() >= 0.0d);
        assertTrue(results.get(0).getScore() <= 1.0d);
    }

    @Test
    void testSearchEmptyResults() {
        ChromaVectorStore store = storeWithFullSchema("test_collection");

        List<VectorSearchResult> results = store.search(
                "test_collection", List.of(0.1d, 0.2d, 0.3d), "embedding", 5, null, Map.of()).join();

        assertEquals(0, results.size());
    }

    @Test
    void testSearchWithJsonMetadata() {
        ChromaVectorStore store = storeWithFullSchema("test_collection");
        Map<String, Object> doc = doc("doc1", List.of(0.1d, 0.2d, 0.3d), "Text 1");
        doc.put("tags", List.of("tag1", "tag2"));
        store.addDocs("test_collection", List.of(doc), Map.of()).join();

        List<VectorSearchResult> results = store.search(
                "test_collection", List.of(0.1d, 0.2d, 0.3d), "embedding", 5, null, Map.of()).join();

        assertEquals(List.of("tag1", "tag2"), results.get(0).getFields().get("tags"));
    }

    @Test
    void testSearchWithInvalidJsonMetadata() {
        ChromaVectorStore store = storeWithFullSchema("test_collection");
        Map<String, Object> doc = doc("doc1", List.of(0.1d, 0.2d, 0.3d), "Text 1");
        doc.put("tags", "invalid json");
        store.addDocs("test_collection", List.of(doc), Map.of()).join();

        List<VectorSearchResult> results = store.search(
                "test_collection", List.of(0.1d, 0.2d, 0.3d), "embedding", 5, null, Map.of()).join();

        assertEquals("invalid json", results.get(0).getFields().get("tags"));
    }

    @Test
    void testDeleteDocsByIdsSuccess() {
        ChromaVectorStore store = storeWithFullSchema("test_collection");
        store.addDocs("test_collection", List.of(
                doc("doc1", List.of(1.0d, 0.0d, 0.0d), "Text 1"),
                doc("doc2", List.of(0.0d, 1.0d, 0.0d), "Text 2")
        ), Map.of()).join();

        store.deleteDocsByIds("test_collection", List.of("doc1", "doc2"), Map.of()).join();

        assertTrue(store.search("test_collection", List.of(1.0d, 0.0d, 0.0d),
                "embedding", 5, null, Map.of()).join().isEmpty());
    }

    @Test
    void testDeleteDocsByFiltersSuccess() {
        ChromaVectorStore store = storeWithFullSchema("test_collection");
        store.addDocs("test_collection", List.of(
                docWithSource("doc1", List.of(1.0d, 0.0d, 0.0d), "Text 1", "test"),
                docWithSource("doc2", List.of(0.0d, 1.0d, 0.0d), "Text 2", "other")
        ), Map.of()).join();

        store.deleteDocsByFilters("test_collection", Map.of("source", "test"), Map.of()).join();

        List<VectorSearchResult> results = store.search(
                "test_collection", List.of(1.0d, 0.0d, 0.0d), "embedding", 5, null, Map.of()).join();
        assertEquals(1, results.size());
        assertEquals("doc2", results.get(0).getFields().get("id"));
    }

    private ChromaVectorStore storeWithFullSchema(String collectionName) {
        ChromaVectorStore store = new ChromaVectorStore();
        store.createCollection(collectionName, fullSchema(), Map.of()).join();
        return store;
    }

    private CollectionSchema fullSchema() {
        CollectionSchema schema = new CollectionSchema(new ArrayList<>(), "Test collection", false);
        schema.addField(new FieldSchema("id", VectorDataType.VARCHAR, true, false, 256, null,
                null, null, null, null));
        schema.addField(new FieldSchema("embedding", VectorDataType.FLOAT_VECTOR, false, false, 65535, 768,
                null, null, null, null));
        schema.addField(new FieldSchema("text", VectorDataType.VARCHAR, false, false, 65535, null,
                null, null, null, null));
        return schema;
    }

    private CollectionSchema minimalSchema() {
        CollectionSchema schema = new CollectionSchema(new ArrayList<>(), null, false);
        schema.addField(new FieldSchema("id", VectorDataType.VARCHAR, true, false, 256, null,
                null, null, null, null));
        schema.addField(new FieldSchema("embedding", VectorDataType.FLOAT_VECTOR, false, false, 65535, 768,
                null, null, null, null));
        return schema;
    }

    private CollectionSchema schemaWithoutPrimary() {
        CollectionSchema schema = new CollectionSchema(new ArrayList<>(), null, false);
        schema.addField(new FieldSchema("embedding", VectorDataType.FLOAT_VECTOR, false, false, 65535, 768,
                null, null, null, null));
        return schema;
    }

    private CollectionSchema schemaWithoutVector() {
        CollectionSchema schema = new CollectionSchema(new ArrayList<>(), null, false);
        schema.addField(new FieldSchema("id", VectorDataType.VARCHAR, true, false, 256, null,
                null, null, null, null));
        return schema;
    }

    private Map<String, Object> doc(String id, List<Double> embedding, String text) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", id);
        doc.put("embedding", embedding);
        doc.put("text", text);
        return doc;
    }

    private Map<String, Object> docWithSource(String id, List<Double> embedding, String text, String source) {
        Map<String, Object> doc = doc(id, embedding, text);
        doc.put("source", source);
        return doc;
    }

    private BaseError assertBaseError(Runnable action) {
        CompletionException exception = assertThrows(CompletionException.class, action::run);
        assertInstanceOf(BaseError.class, exception.getCause());
        return (BaseError) exception.getCause();
    }

    private static final class ThrowingDeleteClientAdapter implements ChromaVectorStore.ChromaClientAdapter {
        @Override
        public ChromaVectorStore.ChromaCollectionAdapter getCollection(String name) {
            throw new IllegalArgumentException("Collection does not exist: " + name);
        }

        @Override
        public ChromaVectorStore.ChromaCollectionAdapter getOrCreateCollection(String name, Map<String, Object> metadata,
                Map<String, Object> configuration) {
            return new TestCollectionAdapter(name, metadata);
        }

        @Override
        public void deleteCollection(String name) {
            throw new RuntimeException("Delete failed");
        }

        @Override
        public List<String> listCollectionNames() {
            return List.of();
        }
    }

    private static final class MetadataOnlyClientAdapter implements ChromaVectorStore.ChromaClientAdapter {
        private final Map<String, Object> metadata;

        private MetadataOnlyClientAdapter(Map<String, Object> metadata) {
            this.metadata = metadata;
        }

        @Override
        public ChromaVectorStore.ChromaCollectionAdapter getCollection(String name) {
            return new TestCollectionAdapter(name, metadata);
        }

        @Override
        public ChromaVectorStore.ChromaCollectionAdapter getOrCreateCollection(String name, Map<String, Object> metadata,
                Map<String, Object> configuration) {
            return new TestCollectionAdapter(name, metadata);
        }

        @Override
        public void deleteCollection(String name) {
        }

        @Override
        public List<String> listCollectionNames() {
            return List.of("test_collection");
        }
    }

    private static final class TestCollectionAdapter implements ChromaVectorStore.ChromaCollectionAdapter {
        private final String name;
        private Map<String, Object> metadata;

        private TestCollectionAdapter(String name, Map<String, Object> metadata) {
            this.name = name;
            this.metadata = new LinkedHashMap<>(metadata);
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Map<String, Object> metadata() {
            return metadata;
        }

        @Override
        public void add(List<String> ids, List<List<Double>> embeddings, List<String> documents,
                List<Map<String, Object>> metadatas) {
        }

        @Override
        public Map<String, Object> query(List<Double> queryEmbedding, int nResults, Map<String, Object> where) {
            return Map.of("ids", List.of(), "documents", List.of(), "metadatas", List.of(), "distances", List.of());
        }

        @Override
        public void deleteByIds(List<String> ids) {
        }

        @Override
        public void deleteByWhere(Map<String, Object> where) {
        }

        @Override
        public Map<String, Object> get(List<String> include) {
            return Map.of("ids", List.of(), "documents", List.of(), "metadatas", List.of(), "embeddings", List.of());
        }

        @Override
        public void modify(String name, Map<String, Object> metadata) {
            this.metadata = new LinkedHashMap<>(metadata);
        }
    }
}
