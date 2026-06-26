/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.store.vector;

import com.openjiuwen.core.foundation.store.CollectionSchema;
import com.openjiuwen.core.foundation.store.FieldSchema;
import com.openjiuwen.core.foundation.store.VectorDataType;
import com.openjiuwen.core.foundation.store.VectorSearchResult;
import com.openjiuwen.core.memory.migration.operation.AddScalarFieldOperation;
import com.openjiuwen.core.memory.migration.operation.OperationMetadata;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code ElasticsearchVectorStore} behavior in
 * {@code openjiuwen/extensions/store/vector/es_vector_store.py}.
 */
class ElasticsearchVectorStoreTest {
    @Test
    void createCollectionBuildsMappingsAndStoresMetadata() {
        FakeElasticsearchClient client = new FakeElasticsearchClient();
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(client);

        store.createCollection("docs", schema(), Map.of("distance_metric", "IP")).join();

        String indexName = "agent_vector__docs";
        assertTrue(client.indices.exists(indexName));
        Map<String, Object> properties = mapAt(mapAt(mapAt(client.mappings.get(indexName), "mappings"), "properties"),
                "embedding");
        assertEquals("dense_vector", properties.get("type"));
        assertEquals("dot_product", properties.get("similarity"));
        Map<String, Object> metadata = mapAt(client.documents.get(indexName).get(ElasticsearchVectorStore.METADATA_DOC_ID),
                "_meta");
        assertEquals("docs", metadata.get("collection_name"));
        assertEquals("IP", metadata.get("distance_metric"));
    }

    @Test
    void addDocsUsesPrimaryKeyAndSkipsNullValues() {
        FakeElasticsearchClient client = new FakeElasticsearchClient();
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(client);
        store.createCollection("docs", schema(), Map.of()).join();

        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", "doc-1");
        doc.put("text", "hello");
        doc.put("drop", null);
        doc.put("embedding", List.of(1.0d, 0.0d, 0.0d));
        store.addDocs("docs", List.of(doc), Map.of("batch_size", 0)).join();

        Map<String, Object> stored = client.documents.get("agent_vector__docs").get("doc-1");
        assertEquals("hello", stored.get("text"));
        assertFalse(stored.containsKey("drop"));
        assertTrue(client.refreshed.contains("agent_vector__docs"));
    }

    @Test
    void searchBuildsKnnFilterAndReturnsFields() {
        FakeElasticsearchClient client = new FakeElasticsearchClient();
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(client);
        store.createCollection("docs", schema(), Map.of()).join();
        client.nextSearchHits.add(hit("doc-1", 0.72d, Map.of("text", "alpha", "_meta", Map.of())));

        List<VectorSearchResult> results = store.search("docs", List.of(1.0d, 0.0d, 0.0d), "embedding", 3,
                Map.of("tag", List.of("a", "b")), Map.of("output_fields", List.of("text"))).join();

        assertEquals(1, results.size());
        assertEquals(0.72d, results.get(0).getScore());
        assertEquals("doc-1", results.get(0).getFields().get("id"));
        assertEquals("alpha", results.get(0).getFields().get("text"));
        Map<String, Object> knn = mapAt(client.lastSearchBody, "knn");
        assertEquals("embedding", knn.get("field"));
        assertEquals(100, knn.get("num_candidates"));
        assertTrue(mapAt(knn, "filter").containsKey("bool"));
    }

    @Test
    void deleteDocsByIdsAndFiltersUseBulkAndDeleteByQuery() {
        FakeElasticsearchClient client = new FakeElasticsearchClient();
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(client);
        store.createCollection("docs", schema(), Map.of()).join();
        store.addDocs("docs", List.of(doc("doc-1", "a"), doc("doc-2", "b")), Map.of()).join();

        store.deleteDocsByIds("docs", List.of("doc-1"), Map.of()).join();
        store.deleteDocsByFilters("docs", Map.of("text", "b"), Map.of()).join();

        assertFalse(client.documents.get("agent_vector__docs").containsKey("doc-1"));
        assertFalse(client.documents.get("agent_vector__docs").containsKey("doc-2"));
        assertTrue(mapAt(client.lastDeleteByQuery, "query").containsKey("bool"));
    }

    @Test
    void getSchemaFallsBackToMappingsWhenMetadataMissing() {
        FakeElasticsearchClient client = new FakeElasticsearchClient();
        String indexName = "agent_vector__docs";
        client.indices.create(indexName, Map.of("mappings", Map.of("properties", Map.of(
                "id", Map.of("type", "keyword"),
                "embedding", Map.of("type", "dense_vector", "dims", 3)
        ))));
        client.documents.get(indexName).remove(ElasticsearchVectorStore.METADATA_DOC_ID);
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(client);

        CollectionSchema result = store.getSchema("docs", Map.of("primary_key_field", "id")).join();

        assertEquals(2, result.getFields().size());
        assertTrue(result.getField("id").isPrimary());
        assertEquals(VectorDataType.FLOAT_VECTOR, result.getField("embedding").getDtype());
        assertEquals(3, result.getField("embedding").getDim());
    }

    @Test
    void metadataDefaultsAndValidationMatchPython() {
        FakeElasticsearchClient client = new FakeElasticsearchClient();
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(client);
        store.createCollection("docs", schema(), Map.of()).join();

        Map<String, Object> metadata = store.getCollectionMetadata("docs").join();
        assertEquals("COSINE", metadata.get("distance_metric"));
        assertEquals(0, metadata.get("schema_version"));
        store.updateCollectionMetadata("docs", Map.of("schema_version", 2)).join();
        assertEquals(2, store.getCollectionMetadata("docs").join().get("schema_version"));

        CompletionException thrown = assertThrows(CompletionException.class,
                () -> store.updateCollectionMetadata("docs", Map.of("schema_version", -1)).join());
        assertTrue(thrown.getCause().getMessage().contains("schema_version"));
    }

    @Test
    void updateSchemaMigratesDocumentsThroughTemporaryCollection() {
        FakeElasticsearchClient client = new FakeElasticsearchClient();
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(client);
        store.createCollection("docs", schema(), Map.of("distance_metric", "COSINE")).join();
        store.addDocs("docs", List.of(doc("doc-1", "alpha")), Map.of()).join();

        AddScalarFieldOperation operation = new AddScalarFieldOperation(new OperationMetadata(1),
                "memory_doc", "category", "varchar", "default");
        store.updateSchema("docs", List.of(operation)).join();

        Map<String, Object> migrated = client.documents.get("agent_vector__docs").get("doc-1");
        assertEquals("default", migrated.get("category"));
        assertFalse(client.indices.keys().stream().anyMatch(name -> name.contains("_migration_")));
    }

    @Test
    void listCollectionNamesStripsPrefix() {
        FakeElasticsearchClient client = new FakeElasticsearchClient();
        ElasticsearchVectorStore store = new ElasticsearchVectorStore(client);
        store.createCollection("docs", schema(), Map.of()).join();

        assertEquals(List.of("docs"), store.listCollectionNames().join());
    }

    private static CollectionSchema schema() {
        return new CollectionSchema(List.of(
                new FieldSchema("id", VectorDataType.VARCHAR, true, false, 65535, null, null, null, null, null),
                new FieldSchema("embedding", VectorDataType.FLOAT_VECTOR, false, false, null, 3, null, null, null, null),
                new FieldSchema("text", VectorDataType.VARCHAR, false, false, 65535, null, null, null, null, null)
        ), "test schema", false);
    }

    private static Map<String, Object> doc(String id, String text) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", id);
        doc.put("text", text);
        doc.put("embedding", List.of(1.0d, 0.0d, 0.0d));
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
    private static Map<String, Object> mapAt(Map<String, Object> source, String key) {
        return (Map<String, Object>) source.get(key);
    }

    private static final class FakeElasticsearchClient
            implements ElasticsearchVectorStore.ElasticsearchClientAdapter {
        private final FakeIndices indices = new FakeIndices();
        private final Map<String, Map<String, Map<String, Object>>> documents = new LinkedHashMap<>();
        private final Map<String, Map<String, Object>> mappings = new LinkedHashMap<>();
        private final List<String> refreshed = new ArrayList<>();
        private final List<Map<String, Object>> nextSearchHits = new ArrayList<>();
        private Map<String, Object> lastSearchBody = Map.of();
        private Map<String, Object> lastDeleteByQuery = Map.of();

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
            documents.computeIfAbsent(index, key -> new LinkedHashMap<>()).put(id, new LinkedHashMap<>(body));
            if (refresh) {
                refreshed.add(index);
            }
        }

        @Override
        public ElasticsearchVectorStore.BulkResponse bulk(List<Map<String, Object>> actions, boolean refresh,
                boolean raiseOnError) {
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
                documents.computeIfAbsent(index, key -> new LinkedHashMap<>()).put(id, source);
            }
            if (refresh && !actions.isEmpty()) {
                refreshed.add(String.valueOf(actions.get(0).get("_index")));
            }
            return new ElasticsearchVectorStore.BulkResponse(actions.size(), List.of());
        }

        @Override
        public Map<String, Object> search(String index, Map<String, Object> body) {
            lastSearchBody = new LinkedHashMap<>(body);
            if (!nextSearchHits.isEmpty()) {
                List<Map<String, Object>> hits = new ArrayList<>(nextSearchHits);
                nextSearchHits.clear();
                return Map.of("hits", Map.of("hits", hits));
            }
            List<Map<String, Object>> hits = new ArrayList<>();
            Map<String, Map<String, Object>> indexDocs = documents.getOrDefault(index, Map.of());
            for (Map.Entry<String, Map<String, Object>> entry : indexDocs.entrySet()) {
                if (ElasticsearchVectorStore.METADATA_DOC_ID.equals(entry.getKey())) {
                    continue;
                }
                hits.add(hit(entry.getKey(), 1.0d, entry.getValue()));
            }
            return Map.of("hits", Map.of("hits", hits));
        }

        @Override
        public Map<String, Object> deleteByQuery(String index, Map<String, Object> body, boolean refresh) {
            lastDeleteByQuery = new LinkedHashMap<>(body);
            int deleted = 0;
            Map<String, Map<String, Object>> indexDocs = documents.getOrDefault(index, Map.of());
            List<String> ids = new ArrayList<>();
            for (Map.Entry<String, Map<String, Object>> entry : indexDocs.entrySet()) {
                if (ElasticsearchVectorStore.METADATA_DOC_ID.equals(entry.getKey())) {
                    continue;
                }
                if (matchesFilter(entry.getValue(), mapAt(body, "query"))) {
                    ids.add(entry.getKey());
                }
            }
            for (String id : ids) {
                indexDocs.remove(id);
                deleted++;
            }
            if (refresh) {
                refreshed.add(index);
            }
            return Map.of("deleted", deleted);
        }

        @Override
        public void close() {
        }

        private boolean matchesFilter(Map<String, Object> source, Map<String, Object> query) {
            Map<String, Object> bool = mapAt(query, "bool");
            Object filter = bool.get("filter");
            if (!(filter instanceof List<?> clauses)) {
                return true;
            }
            for (Object clause : clauses) {
                if (!(clause instanceof Map<?, ?> clauseMap)) {
                    continue;
                }
                Map<String, Object> typedClause = new LinkedHashMap<>();
                clauseMap.forEach((key, value) -> typedClause.put(String.valueOf(key), value));
                if (typedClause.containsKey("term")) {
                    Map<String, Object> term = mapAt(typedClause, "term");
                    for (Map.Entry<String, Object> entry : term.entrySet()) {
                        if (!entry.getValue().equals(source.get(entry.getKey()))) {
                            return false;
                        }
                    }
                }
                if (typedClause.containsKey("terms")) {
                    Map<String, Object> terms = mapAt(typedClause, "terms");
                    for (Map.Entry<String, Object> entry : terms.entrySet()) {
                        if (!(entry.getValue() instanceof Collection<?> values)
                                || !values.contains(source.get(entry.getKey()))) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }

        private final class FakeIndices implements ElasticsearchVectorStore.ElasticsearchIndicesAdapter {
            @Override
            public boolean exists(String index) {
                return documents.containsKey(index);
            }

            @Override
            public void create(String index, Map<String, Object> body) {
                documents.putIfAbsent(index, new LinkedHashMap<>());
                mappings.put(index, body);
            }

            @Override
            public void delete(String index) {
                documents.remove(index);
                mappings.remove(index);
            }

            @Override
            public Map<String, Object> getMapping(String index) {
                return Map.of(index, mappings.get(index));
            }

            @Override
            public void refresh(String index) {
                refreshed.add(index);
            }

            @Override
            public Map<String, Object> get(String indexPattern) {
                String prefix = indexPattern.replace("*", "");
                Map<String, Object> result = new LinkedHashMap<>();
                for (String index : documents.keySet()) {
                    if (index.startsWith(prefix)) {
                        result.put(index, Map.of());
                    }
                }
                return result;
            }

            private List<String> keys() {
                return new ArrayList<>(documents.keySet());
            }
        }
    }
}
