/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.store.vector;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.store.CollectionSchema;
import com.openjiuwen.core.foundation.store.FieldSchema;
import com.openjiuwen.core.foundation.store.VectorDataType;
import com.openjiuwen.core.foundation.store.VectorSearchResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <p>Mirrors Python's {@code tests.unit_tests.extensions.store.test_es_vector_store} in
 * {@code tests/unit_tests/extensions/store/test_es_vector_store.py}.</p>
 */
class ElasticsearchVectorStorePythonParityTest {

    @Test
    void initWithDefaults() {
        FakeElasticsearchClient client = new FakeElasticsearchClient();
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(client);

        assertSame(client, privateField(store, "es"));
        assertEquals("agent_vector", privateField(store, "indexPrefix"));
        assertTrue(mapField(store, "metadataCache").isEmpty());
    }

    @Test
    void initWithCustomPrefix() {
        FakeElasticsearchClient client = new FakeElasticsearchClient();
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(client, "custom_prefix");

        assertSame(client, privateField(store, "es"));
        assertEquals("custom_prefix", privateField(store, "indexPrefix"));
    }

    @Test
    void indexName() {
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(new FakeElasticsearchClient(), "my_prefix");

        assertEquals("my_prefix__test_coll", store.indexName("test_coll"));
    }

    @Test
    void mapEsTypeVector() {
        Map<String, Object> result = invokeMapEsType(field("embedding", VectorDataType.FLOAT_VECTOR, false, 768));

        assertEquals("dense_vector", result.get("type"));
        assertEquals(768, result.get("dims"));
        assertEquals(true, result.get("index"));
        assertEquals("cosine", result.get("similarity"));
    }

    @Test
    void mapEsTypeVarchar() {
        assertEquals("keyword", invokeMapEsType(field("text", VectorDataType.VARCHAR)).get("type"));
    }

    @Test
    void mapEsTypeInt64() {
        assertEquals("long", invokeMapEsType(field("count", VectorDataType.INT64)).get("type"));
    }

    @Test
    void mapEsTypeInt32() {
        assertEquals("integer", invokeMapEsType(field("age", VectorDataType.INT32)).get("type"));
    }

    @Test
    void mapEsTypeFloat() {
        assertEquals("float", invokeMapEsType(field("score", VectorDataType.FLOAT)).get("type"));
    }

    @Test
    void mapEsTypeDouble() {
        assertEquals("double", invokeMapEsType(field("value", VectorDataType.DOUBLE)).get("type"));
    }

    @Test
    void mapEsTypeBool() {
        assertEquals("boolean", invokeMapEsType(field("is_active", VectorDataType.BOOL)).get("type"));
    }

    @Test
    void mapEsTypeJson() {
        Map<String, Object> result = invokeMapEsType(field("metadata", VectorDataType.JSON));

        assertEquals("object", result.get("type"));
        assertEquals(true, result.get("enabled"));
    }

    @Test
    void createCollectionWithSchemaObject() {
        FakeElasticsearchClient client = new FakeElasticsearchClient();
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(client);

        store.createCollection("test_collection", schema768(), Map.of()).join();

        assertEquals(List.of("agent_vector__test_collection"), client.indices.existsCalls);
        assertEquals(1, client.indices.createCalls);
    }

    @Test
    void createCollectionWithDictSchema() {
        FakeElasticsearchClient client = new FakeElasticsearchClient();
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(client);

        store.createCollection("test_collection", schemaDict768(), Map.of()).join();

        assertEquals(1, client.indices.existsCalls.size());
        assertEquals(1, client.indices.createCalls);
    }

    @Test
    void createCollectionAlreadyExists() {
        FakeElasticsearchClient client = new FakeElasticsearchClient();
        client.indices.create("agent_vector__test_collection", Map.of("mappings", Map.of()));
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(client);

        store.createCollection("test_collection", schema768(), Map.of()).join();

        assertEquals(1, client.indices.createCalls);
    }

    @Test
    void createCollectionMissingVectorField() {
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(new FakeElasticsearchClient());
        CollectionSchema schema = new CollectionSchema(new ArrayList<>(), null, false);
        schema.addField(field("id", VectorDataType.VARCHAR, true, null));

        CompletionException thrown = assertThrows(CompletionException.class,
                () -> store.createCollection("test_collection", schema, Map.of()).join());

        assertInstanceOf(BaseError.class, thrown.getCause());
        assertTrue(thrown.getCause().getMessage().contains("must contain at least one FLOAT_VECTOR field"));
    }

    @Test
    void createCollectionWithCustomMetric() {
        FakeElasticsearchClient client = new FakeElasticsearchClient();
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(client);

        store.createCollection("test_collection", schema768(), Map.of("distance_metric", "L2")).join();

        Map<String, Object> body = client.mappings.get("agent_vector__test_collection");
        Map<String, Object> embedding = mapAt(mapAt(mapAt(body, "mappings"), "properties"), "embedding");
        assertEquals("l2_norm", embedding.get("similarity"));
    }

    @Test
    void deleteCollectionSuccess() {
        FakeElasticsearchClient client = new FakeElasticsearchClient();
        client.indices.create("agent_vector__test_collection", Map.of());
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(client);

        store.deleteCollection("test_collection", Map.of()).join();

        assertEquals(List.of("agent_vector__test_collection"), client.indices.existsCalls);
        assertEquals(List.of("agent_vector__test_collection"), client.indices.deleteCalls);
    }

    @Test
    void deleteCollectionNotExists() {
        FakeElasticsearchClient client = new FakeElasticsearchClient();
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(client);

        store.deleteCollection("test_collection", Map.of()).join();

        assertTrue(client.indices.deleteCalls.isEmpty());
    }

    @Test
    void deleteCollectionError() {
        FakeElasticsearchClient client = new FakeElasticsearchClient();
        client.indices.create("agent_vector__test_collection", Map.of());
        client.indices.failDelete = true;
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(client);

        CompletionException thrown = assertThrows(CompletionException.class,
                () -> store.deleteCollection("test_collection", Map.of()).join());

        assertTrue(thrown.getCause().getMessage().contains("Delete failed"));
    }

    @Test
    void collectionExistsTrue() {
        FakeElasticsearchClient client = new FakeElasticsearchClient();
        client.indices.create("agent_vector__test_collection", Map.of());
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(client);

        assertTrue(store.collectionExists("test_collection", Map.of()).join());
    }

    @Test
    void collectionExistsFalse() {
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(new FakeElasticsearchClient());

        assertFalse(store.collectionExists("test_collection", Map.of()).join());
    }

    @Test
    void collectionExistsError() {
        FakeElasticsearchClient client = new FakeElasticsearchClient();
        client.indices.failExists = true;
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(client);

        assertFalse(store.collectionExists("test_collection", Map.of()).join());
    }

    @Test
    void getSchemaFromMetadata() {
        FakeElasticsearchClient client = new FakeElasticsearchClient();
        client.putMetadata("agent_vector__test_collection", Map.of("schema", schemaDict768()));
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(client);

        CollectionSchema schema = store.getSchema("test_collection", Map.of()).join();

        assertEquals(3, schema.getFields().size());
        assertEquals("id", schema.getFields().get(0).getName());
        assertEquals(VectorDataType.VARCHAR, schema.getFields().get(0).getDtype());
        assertEquals("embedding", schema.getFields().get(1).getName());
        assertEquals(VectorDataType.FLOAT_VECTOR, schema.getFields().get(1).getDtype());
    }

    @Test
    void getSchemaFromMapping() {
        FakeElasticsearchClient client = new FakeElasticsearchClient();
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", Map.of("type", "keyword"));
        properties.put("embedding", Map.of("type", "dense_vector", "dims", 768));
        properties.put("text", Map.of("type", "keyword"));
        client.indices.create("agent_vector__test_collection",
                Map.of("mappings", Map.of("properties", properties)));
        client.documents.get("agent_vector__test_collection").remove(ElasticsearchVectorStore.METADATA_DOC_ID);
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(client);

        CollectionSchema schema = store.getSchema("test_collection", Map.of()).join();

        assertEquals(3, schema.getFields().size());
        assertEquals("id", schema.getFields().get(0).getName());
        assertEquals(VectorDataType.VARCHAR, schema.getFields().get(0).getDtype());
        assertEquals("embedding", schema.getFields().get(1).getName());
        assertEquals(VectorDataType.FLOAT_VECTOR, schema.getFields().get(1).getDtype());
    }

    @Test
    void getSchemaNotFound() {
        FakeElasticsearchClient client = new FakeElasticsearchClient();
        client.indices.failGetMapping = true;
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(client);

        CompletionException thrown = assertThrows(CompletionException.class,
                () -> store.getSchema("non_existent_collection", Map.of()).join());

        assertInstanceOf(BaseError.class, thrown.getCause());
    }

    @Test
    void addDocsSuccess() {
        FakeElasticsearchClient client = new FakeElasticsearchClient();
        client.putMetadata("agent_vector__test_collection", Map.of("schema", schemaDict3()));
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(client);
        List<Map<String, Object>> docs = List.of(
                doc("doc1", List.of(0.1d, 0.2d, 0.3d), "Test 1"),
                doc("doc2", List.of(0.4d, 0.5d, 0.6d), "Test 2")
        );

        store.addDocs("test_collection", docs, Map.of()).join();

        assertEquals(1, client.bulkCalls);
        assertEquals(List.of("agent_vector__test_collection"), client.indices.refreshCalls);
    }

    @Test
    void addDocsWithBatchSize() {
        FakeElasticsearchClient client = new FakeElasticsearchClient();
        client.putMetadata("agent_vector__test_collection", Map.of("schema", schemaDict3()));
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(client);
        List<Map<String, Object>> docs = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            docs.add(Map.of("id", "doc" + index, "text", "Text " + index));
        }

        store.addDocs("test_collection", docs, Map.of("batch_size", 3)).join();

        assertEquals(4, client.bulkCalls);
    }

    @Test
    void addDocsEmptyList() {
        FakeElasticsearchClient client = new FakeElasticsearchClient();
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(client);

        store.addDocs("test_collection", List.of(), Map.of()).join();

        assertEquals(0, client.bulkCalls);
    }

    @Test
    void searchSuccess() {
        FakeElasticsearchClient client = new FakeElasticsearchClient();
        client.putMetadata("agent_vector__test_collection", Map.of("distance_metric", "COSINE"));
        client.nextSearchHits.add(hit("doc1", 0.95d, Map.of("text", "Text 1", "source", "test1")));
        client.nextSearchHits.add(hit("doc2", 0.85d, Map.of("text", "Text 2", "source", "test2")));
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(client);

        List<VectorSearchResult> results = store.search(
                "test_collection", List.of(0.1d, 0.2d, 0.3d), "embedding", 5, Map.of(), Map.of()).join();

        assertEquals(2, results.size());
        assertEquals("doc1", results.get(0).getFields().get("id"));
        assertEquals("Text 1", results.get(0).getFields().get("text"));
        assertEquals(0.95d, results.get(0).getScore());
    }

    @Test
    void searchWithFilters() {
        FakeElasticsearchClient client = new FakeElasticsearchClient();
        client.putMetadata("agent_vector__test_collection", Map.of());
        client.nextSearchHits.add(hit("doc1", 0.9d, Map.of("category", "tech")));
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(client);

        List<VectorSearchResult> results = store.search("test_collection", List.of(0.1d, 0.2d, 0.3d),
                "embedding", 5, Map.of("category", "tech"), Map.of()).join();

        assertEquals(1, results.size());
        assertTrue(mapAt(client.lastSearchBody, "knn").containsKey("filter"));
    }

    @Test
    void searchWithListFilters() {
        FakeElasticsearchClient client = new FakeElasticsearchClient();
        client.putMetadata("agent_vector__test_collection", Map.of());
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(client);

        store.search("test_collection", List.of(0.1d, 0.2d, 0.3d), "embedding", 5,
                Map.of("category", List.of("tech", "science")), Map.of()).join();

        Map<String, Object> filter = mapAt(mapAt(client.lastSearchBody, "knn"), "filter");
        Map<String, Object> firstClause = mapAt(listAt(mapAt(filter, "bool"), "filter").get(0), "terms");
        assertEquals(List.of("tech", "science"), firstClause.get("category"));
    }

    @Test
    void searchError() {
        FakeElasticsearchClient client = new FakeElasticsearchClient();
        client.putMetadata("agent_vector__test_collection", Map.of());
        client.failSearch = true;
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(client);

        CompletionException thrown = assertThrows(CompletionException.class,
                () -> store.search("test_collection", List.of(0.1d, 0.2d, 0.3d), "embedding", 10,
                        Map.of(), Map.of()).join());

        assertTrue(thrown.getCause().getMessage().contains("Search failed"));
    }

    @Test
    void deleteDocsByIdsSuccess() {
        FakeElasticsearchClient client = new FakeElasticsearchClient();
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(client);

        store.deleteDocsByIds("test_collection", List.of("doc1", "doc2"), Map.of()).join();

        assertEquals(1, client.bulkCalls);
        assertEquals("delete", client.lastBulkActions.get(0).get("_op_type"));
    }

    @Test
    void deleteDocsByIdsEmptyList() {
        FakeElasticsearchClient client = new FakeElasticsearchClient();
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(client);

        store.deleteDocsByIds("test_collection", List.of(), Map.of()).join();

        assertEquals(0, client.bulkCalls);
    }

    @Test
    void deleteDocsByFiltersSuccess() {
        FakeElasticsearchClient client = new FakeElasticsearchClient();
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(client);

        store.deleteDocsByFilters("test_collection", Map.of("source", "test"), Map.of()).join();

        assertEquals(1, client.deleteByQueryCalls);
    }

    @Test
    void deleteDocsByFiltersEmpty() {
        FakeElasticsearchClient client = new FakeElasticsearchClient();
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(client);

        store.deleteDocsByFilters("test_collection", Map.of(), Map.of()).join();

        assertEquals(0, client.deleteByQueryCalls);
    }

    @Test
    void deleteDocsByFiltersWithList() {
        FakeElasticsearchClient client = new FakeElasticsearchClient();
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(client);

        store.deleteDocsByFilters("test_collection", Map.of("category", List.of("tech", "science")), Map.of())
                .join();

        Map<String, Object> query = mapAt(client.lastDeleteByQuery, "query");
        Map<String, Object> firstClause = mapAt(listAt(mapAt(query, "bool"), "filter").get(0), "terms");
        assertEquals(List.of("tech", "science"), firstClause.get("category"));
    }

    @Test
    void listCollectionNamesSuccess() {
        FakeElasticsearchClient client = new FakeElasticsearchClient();
        client.indices.create("agent_vector__test1", Map.of());
        client.indices.create("agent_vector__test2", Map.of());
        client.indices.create("other_index", Map.of());
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(client);

        List<String> names = store.listCollectionNames().join();

        assertEquals(2, names.size());
        assertTrue(names.contains("test1"));
        assertTrue(names.contains("test2"));
    }

    @Test
    void listCollectionNamesEmpty() {
        FakeElasticsearchClient client = new FakeElasticsearchClient();
        client.indices.failGet = true;
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(client);

        assertEquals(List.of(), store.listCollectionNames().join());
    }

    @Test
    void getCollectionMetadataSuccess() {
        FakeElasticsearchClient client = new FakeElasticsearchClient();
        client.putMetadata("agent_vector__test_collection", Map.of("distance_metric", "L2", "schema_version", 1));
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(client);

        Map<String, Object> metadata = store.getCollectionMetadata("test_collection").join();

        assertEquals("L2", metadata.get("distance_metric"));
        assertEquals(1, metadata.get("schema_version"));
    }

    @Test
    void getCollectionMetadataDefaults() {
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(new FakeElasticsearchClient());

        Map<String, Object> metadata = store.getCollectionMetadata("test_collection").join();

        assertEquals("COSINE", metadata.get("distance_metric"));
        assertEquals(0, metadata.get("schema_version"));
    }

    @Test
    void updateCollectionMetadataSuccess() {
        FakeElasticsearchClient client = new FakeElasticsearchClient();
        client.putMetadata("agent_vector__test_collection", Map.of("schema_version", 0));
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(client);

        store.updateCollectionMetadata("test_collection", Map.of("schema_version", 1)).join();

        assertEquals(1, client.indexCalls);
        assertEquals(1, store.getCollectionMetadata("test_collection").join().get("schema_version"));
    }

    @Test
    void updateCollectionMetadataEmpty() {
        FakeElasticsearchClient client = new FakeElasticsearchClient();
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(client);

        store.updateCollectionMetadata("test_collection", Map.of()).join();

        assertEquals(0, client.indexCalls);
    }

    @Test
    void updateCollectionMetadataInvalidVersion() {
        FakeElasticsearchClient client = new FakeElasticsearchClient();
        client.putMetadata("agent_vector__test_collection", Map.of());
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(client);

        CompletionException thrown = assertThrows(CompletionException.class,
                () -> store.updateCollectionMetadata("test_collection", Map.of("schema_version", -1)).join());

        assertInstanceOf(BaseError.class, thrown.getCause());
        assertTrue(thrown.getCause().getMessage().contains("schema_version must be a non-negative integer"));
    }

    @Test
    void updateSchemaEmptyOperations() {
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(new FakeElasticsearchClient());

        assertDoesNotThrow(() -> store.updateSchema("test_collection", List.of()).join());
    }

    @Test
    void getPrimaryKeyFieldFound() {
        assertEquals("id", invokePrimaryKeyField(Map.of("fields", List.of(
                Map.of("name", "id", "type", "VARCHAR", "is_primary", true),
                Map.of("name", "embedding", "type", "FLOAT_VECTOR")
        ))));
    }

    @Test
    void getPrimaryKeyFieldNotFound() {
        assertNull(invokePrimaryKeyField(Map.of("fields", List.of(
                Map.of("name", "embedding", "type", "FLOAT_VECTOR")
        ))));
    }

    @Test
    void closeConnection() {
        FakeElasticsearchClient client = new FakeElasticsearchClient();
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(client);
        client.putMetadata("agent_vector__test_collection", Map.of("distance_metric", "COSINE"));
        store.getCollectionMetadata("test_collection").join();

        store.close();

        assertEquals(1, client.closeCalls);
        assertTrue(mapField(store, "metadataCache").isEmpty());
    }

    @Test
    void closeLogging() {
        FakeElasticsearchClient client = new FakeElasticsearchClient();
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(client);

        store.close();

        assertEquals(1, client.closeCalls);
    }

    private static CollectionSchema schema768() {
        return new CollectionSchema(List.of(
                field("id", VectorDataType.VARCHAR, true, null),
                field("embedding", VectorDataType.FLOAT_VECTOR, false, 768),
                field("text", VectorDataType.VARCHAR, false, null)
        ), "Test collection", false);
    }

    private static Map<String, Object> schemaDict768() {
        return Map.of(
                "description", "Test collection",
                "fields", List.of(
                        Map.of("name", "id", "type", "VARCHAR", "is_primary", true),
                        Map.of("name", "embedding", "type", "FLOAT_VECTOR", "dim", 768),
                        Map.of("name", "text", "type", "VARCHAR")
                )
        );
    }

    private static Map<String, Object> schemaDict3() {
        return Map.of(
                "fields", List.of(
                        Map.of("name", "id", "type", "VARCHAR", "is_primary", true),
                        Map.of("name", "embedding", "type", "FLOAT_VECTOR", "dim", 3)
                )
        );
    }

    private static FieldSchema field(String name, VectorDataType type) {
        return field(name, type, false, null);
    }

    private static FieldSchema field(String name, VectorDataType type, boolean primary, Integer dim) {
        return new FieldSchema(name, type, primary, false, 65535, dim, null, null, null, null);
    }

    private static Map<String, Object> doc(String id, List<Double> embedding, String text) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", id);
        doc.put("embedding", embedding);
        doc.put("text", text);
        return doc;
    }

    private static Map<String, Object> hit(String id, double score, Map<String, Object> source) {
        Map<String, Object> hit = new LinkedHashMap<>();
        hit.put("_id", id);
        hit.put("_score", score);
        hit.put("_source", new LinkedHashMap<>(source));
        return hit;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> invokeMapEsType(FieldSchema field) {
        try {
            ElasticsearchVectorStore store = new ElasticsearchVectorStore(new FakeElasticsearchClient());
            Method method = ElasticsearchVectorStore.class.getDeclaredMethod("mapEsType", FieldSchema.class);
            method.setAccessible(true);
            return (Map<String, Object>) method.invoke(store, field);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static Object invokePrimaryKeyField(Map<String, Object> schema) {
        try {
            ElasticsearchVectorStore store = new ElasticsearchVectorStore(new FakeElasticsearchClient());
            Method method = ElasticsearchVectorStore.class.getDeclaredMethod("primaryKeyField", Map.class);
            method.setAccessible(true);
            return method.invoke(store, schema);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T privateField(ElasticsearchVectorStore store, String fieldName) {
        try {
            java.lang.reflect.Field field = ElasticsearchVectorStore.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T) field.get(store);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static Map<String, Object> mapField(ElasticsearchVectorStore store, String fieldName) {
        return privateField(store, fieldName);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapAt(Map<String, Object> source, String key) {
        return (Map<String, Object>) source.get(key);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listAt(Map<String, Object> source, String key) {
        return (List<Map<String, Object>>) source.get(key);
    }

    private static final class FakeElasticsearchClient
            implements ElasticsearchVectorStore.ElasticsearchClientAdapter {
        private final FakeIndices indices = new FakeIndices();
        private final Map<String, Map<String, Map<String, Object>>> documents = new LinkedHashMap<>();
        private final Map<String, Map<String, Object>> mappings = new LinkedHashMap<>();
        private final List<Map<String, Object>> nextSearchHits = new ArrayList<>();
        private List<Map<String, Object>> lastBulkActions = List.of();
        private Map<String, Object> lastSearchBody = Map.of();
        private Map<String, Object> lastDeleteByQuery = Map.of();
        private boolean failSearch;
        private int bulkCalls;
        private int deleteByQueryCalls;
        private int indexCalls;
        private int closeCalls;

        @Override
        public ElasticsearchVectorStore.ElasticsearchIndicesAdapter indices() {
            return indices;
        }

        @Override
        public Map<String, Object> get(String index, String id) {
            Map<String, Map<String, Object>> indexDocs = documents.get(index);
            if (indexDocs == null || !indexDocs.containsKey(id)) {
                return Map.of("found", false);
            }
            return Map.of("found", true, "_source", indexDocs.get(id));
        }

        @Override
        public void index(String index, String id, Map<String, Object> body, boolean refresh) {
            indexCalls++;
            documents.computeIfAbsent(index, ignored -> new LinkedHashMap<>()).put(id, new LinkedHashMap<>(body));
            if (refresh) {
                indices.refreshCalls.add(index);
            }
        }

        @Override
        public ElasticsearchVectorStore.BulkResponse bulk(List<Map<String, Object>> actions, boolean refresh,
                boolean raiseOnError) {
            bulkCalls++;
            lastBulkActions = new ArrayList<>(actions);
            for (Map<String, Object> action : actions) {
                String index = String.valueOf(action.get("_index"));
                String id = action.get("_id") == null ? "auto-" + documents.size() : String.valueOf(action.get("_id"));
                if ("delete".equals(action.get("_op_type"))) {
                    Map<String, Map<String, Object>> indexDocs = documents.get(index);
                    if (indexDocs != null) {
                        indexDocs.remove(id);
                    }
                    continue;
                }
                Map<String, Object> source = new LinkedHashMap<>(mapAt(action, "_source"));
                documents.computeIfAbsent(index, ignored -> new LinkedHashMap<>()).put(id, source);
            }
            if (refresh && !actions.isEmpty()) {
                indices.refreshCalls.add(String.valueOf(actions.get(0).get("_index")));
            }
            return new ElasticsearchVectorStore.BulkResponse(actions.size(), List.of());
        }

        @Override
        public Map<String, Object> search(String index, Map<String, Object> body) {
            if (failSearch) {
                throw new IllegalStateException("Search failed");
            }
            lastSearchBody = new LinkedHashMap<>(body);
            if (!nextSearchHits.isEmpty()) {
                List<Map<String, Object>> hits = new ArrayList<>(nextSearchHits);
                nextSearchHits.clear();
                return Map.of("hits", Map.of("hits", hits));
            }
            return Map.of("hits", Map.of("hits", List.of()));
        }

        @Override
        public Map<String, Object> deleteByQuery(String index, Map<String, Object> body, boolean refresh) {
            deleteByQueryCalls++;
            lastDeleteByQuery = new LinkedHashMap<>(body);
            return Map.of("deleted", 0);
        }

        @Override
        public void close() {
            closeCalls++;
        }

        private void putMetadata(String index, Map<String, Object> metadata) {
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("_meta", new LinkedHashMap<>(metadata));
            documents.computeIfAbsent(index, ignored -> new LinkedHashMap<>())
                    .put(ElasticsearchVectorStore.METADATA_DOC_ID, source);
        }

        private final class FakeIndices implements ElasticsearchVectorStore.ElasticsearchIndicesAdapter {
            private final List<String> existsCalls = new ArrayList<>();
            private final List<String> deleteCalls = new ArrayList<>();
            private final List<String> refreshCalls = new ArrayList<>();
            private boolean failExists;
            private boolean failDelete;
            private boolean failGetMapping;
            private boolean failGet;
            private int createCalls;

            @Override
            public boolean exists(String index) {
                existsCalls.add(index);
                if (failExists) {
                    throw new IllegalStateException("ES error");
                }
                return documents.containsKey(index);
            }

            @Override
            public void create(String index, Map<String, Object> body) {
                createCalls++;
                documents.putIfAbsent(index, new LinkedHashMap<>());
                mappings.put(index, new LinkedHashMap<>(body));
            }

            @Override
            public void delete(String index) {
                deleteCalls.add(index);
                if (failDelete) {
                    throw new IllegalStateException("Delete failed");
                }
                documents.remove(index);
                mappings.remove(index);
            }

            @Override
            public Map<String, Object> getMapping(String index) {
                if (failGetMapping) {
                    throw new IllegalStateException("Not found");
                }
                Map<String, Object> mapping = mappings.get(index);
                if (mapping == null) {
                    throw new IllegalStateException("Not found");
                }
                return Map.of(index, mapping);
            }

            @Override
            public void refresh(String index) {
                refreshCalls.add(index);
            }

            @Override
            public Map<String, Object> get(String indexPattern) {
                if (failGet) {
                    throw new IllegalStateException("Not found");
                }
                String prefix = indexPattern.replace("*", "");
                Map<String, Object> result = new LinkedHashMap<>();
                for (String index : documents.keySet()) {
                    if (index.startsWith(prefix)) {
                        result.put(index, Map.of());
                    }
                }
                return result;
            }
        }
    }
}
