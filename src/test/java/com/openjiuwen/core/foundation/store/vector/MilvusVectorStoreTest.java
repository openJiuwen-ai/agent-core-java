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
import com.openjiuwen.core.memory.migration.operation.BaseOperation;
import com.openjiuwen.core.memory.migration.operation.OperationMetadata;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused parity tests for Milvus vector store behavior.
 *
 * <p>Mirrors Python's {@code MilvusVectorStore} in
 * {@code openjiuwen/core/foundation/store/vector/milvus_vector_store.py}.</p>
 */
class MilvusVectorStoreTest {

    @Test
    void createCollectionStoresSchemaAndMetadata() {
        FakeMilvusClientAdapter adapter = new FakeMilvusClientAdapter();
        MilvusVectorStore store = new MilvusVectorStore(adapter);

        store.createCollection("docs", schema(), Map.of("distance_metric", "L2", "index_type", "HNSW")).join();

        assertTrue(adapter.hasCollection("docs"));
        assertEquals("L2", adapter.distanceMetricByCollection.get("docs"));
        assertEquals("HNSW", adapter.indexTypeByCollection.get("docs"));
        assertEquals("embedding", store.getCollectionMetadata("docs").join().get("vector_field"));
        assertEquals(0, store.getCollectionMetadata("docs").join().get("schema_version"));
    }

    @Test
    void addSearchAndDeletePreservePythonMilvusBehavior() {
        FakeMilvusClientAdapter adapter = new FakeMilvusClientAdapter();
        MilvusVectorStore store = new MilvusVectorStore(adapter);
        store.createCollection("docs", schema(), Map.of("distance_metric", "COSINE")).join();

        store.addDocs("docs", List.of(
                mapOf("doc_id", "a", "embedding", List.of(1.0d, 0.0d), "text", "alpha",
                        "tags", "[\"x\",\"y\"]", "score", 3),
                mapOf("doc_id", "b", "embedding", List.of(0.0d, 1.0d), "text", "beta",
                        "tags", "[\"z\"]", "score", 4)
        ), Map.of("batch_size", 1)).join();

        List<VectorSearchResult> results = store.search(
                "docs",
                List.of(1.0d, 0.0d),
                "embedding",
                5,
                Map.of("score", 3),
                Map.of()
        ).join();

        assertEquals(1, results.size());
        assertEquals(1.0d, results.get(0).getScore());
        assertEquals("a", results.get(0).getFields().get("id"));
        assertEquals("alpha", results.get(0).getFields().get("text"));
        assertIterableEquals(List.of("x", "y"), (List<?>) results.get(0).getFields().get("tags"));
        assertEquals("score == 3", adapter.lastSearchFilter);
        assertEquals(List.of("doc_id", "embedding", "text", "score"), adapter.lastOutputFields);

        store.deleteDocsByIds("docs", List.of("a"), Map.of()).join();
        assertFalse(adapter.rowsByCollection.get("docs").stream().anyMatch(row -> "a".equals(row.get("doc_id"))));

        store.deleteDocsByFilters("docs", Map.of("score", 4), Map.of()).join();
        assertTrue(adapter.rowsByCollection.get("docs").isEmpty());
    }

    @Test
    void supportsFilterExpressionAndScoreConversions() {
        MilvusVectorStore store = new MilvusVectorStore(new FakeMilvusClientAdapter());

        assertEquals("name == \"alpha\" && enabled == True && missing == None",
                store.buildFilterExpr(mapOf("name", "alpha", "enabled", true, "missing", null)));

        FakeMilvusClientAdapter ipAdapter = new FakeMilvusClientAdapter();
        MilvusVectorStore ipStore = new MilvusVectorStore(ipAdapter);
        ipStore.createCollection("docs", schema(), Map.of("distance_metric", "IP")).join();
        ipAdapter.searchHits = List.of(
                new MilvusVectorStore.SearchHit("a", null, 1.0d, null, Map.of()),
                new MilvusVectorStore.SearchHit("b", null, 0.0d, null, Map.of()),
                new MilvusVectorStore.SearchHit("c", null, -1.0d, null, Map.of())
        );

        List<VectorSearchResult> ipResults = ipStore.search("docs", List.of(1.0d, 0.0d), "embedding",
                3, null, Map.of("metric_type", "IP")).join();

        assertEquals(1.0d, ipResults.get(0).getScore());
        assertEquals(0.5d, ipResults.get(1).getScore());
        assertEquals(0.0d, ipResults.get(2).getScore());
    }

    @Test
    void validatesSchemaAndMigratesDocuments() {
        FakeMilvusClientAdapter adapter = new FakeMilvusClientAdapter();
        MilvusVectorStore store = new MilvusVectorStore(adapter);

        BaseError noVector = assertBaseError(
                () -> store.createCollection("bad", schemaWithoutVector(), Map.of()).join());
        assertEquals(StatusCode.STORE_VECTOR_SCHEMA_INVALID, noVector.getStatus());

        store.createCollection("docs", schema(), Map.of()).join();
        store.addDocs("docs", List.of(mapOf("doc_id", "a", "embedding", List.of(1.0d, 0.0d), "text", "alpha")),
                Map.of()).join();

        store.updateSchema("docs", List.of(new AddScalarFieldOperation("category", "string", "general"))).join();

        assertTrue(store.getSchema("docs", Map.of()).join().hasField("category"));
        assertEquals("general", adapter.rowsByCollection.get("docs").get(0).get("category"));
    }

    @Test
    void invalidMetadataVersionRaisesBaseError() {
        MilvusVectorStore store = new MilvusVectorStore(new FakeMilvusClientAdapter());
        store.createCollection("docs", schema(), Map.of()).join();

        BaseError error = assertBaseError(
                () -> store.updateCollectionMetadata("docs", Map.of("schema_version", -1)).join());

        assertEquals(StatusCode.STORE_VECTOR_SCHEMA_INVALID, error.getStatus());
    }

    private CollectionSchema schema() {
        return CollectionSchema.fromFields(List.of(
                new FieldSchema("doc_id", VectorDataType.VARCHAR, true, false, 256, null,
                        null, null, null, null),
                new FieldSchema("embedding", VectorDataType.FLOAT_VECTOR, false, false, 65535, 2,
                        null, null, null, null),
                new FieldSchema("text", VectorDataType.VARCHAR, false, false, 65535, null,
                        null, null, "docs", null),
                new FieldSchema("score", VectorDataType.INT32, false, false, 65535, null,
                        null, null, null, null)
        ), "docs", true);
    }

    private CollectionSchema schemaWithoutVector() {
        return CollectionSchema.fromFields(List.of(
                new FieldSchema("doc_id", VectorDataType.VARCHAR, true, false, 256, null,
                        null, null, null, null)
        ), "bad", true);
    }

    private BaseError assertBaseError(Runnable action) {
        CompletionException exception = assertThrows(CompletionException.class, action::run);
        assertInstanceOf(BaseError.class, exception.getCause());
        return (BaseError) exception.getCause();
    }

    private static Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }

    private static final class AddScalarFieldOperation extends BaseOperation {
        private final String fieldName;
        private final String fieldType;
        private final Object defaultValue;

        private AddScalarFieldOperation(String fieldName, String fieldType, Object defaultValue) {
            super(new OperationMetadata(2, "add scalar"));
            this.fieldName = fieldName;
            this.fieldType = fieldType;
            this.defaultValue = defaultValue;
        }

        public String getFieldName() {
            return fieldName;
        }

        public String getFieldType() {
            return fieldType;
        }

        public Object getDefaultValue() {
            return defaultValue;
        }
    }

    private static final class FakeMilvusClientAdapter implements MilvusVectorStore.MilvusClientAdapter {
        private final Map<String, MilvusVectorStore.CollectionDescription> descriptions = new LinkedHashMap<>();
        private final Map<String, List<Map<String, Object>>> rowsByCollection = new LinkedHashMap<>();
        private final Map<String, String> distanceMetricByCollection = new LinkedHashMap<>();
        private final Map<String, String> indexTypeByCollection = new LinkedHashMap<>();
        private final List<String> loadedCollections = new ArrayList<>();
        private List<MilvusVectorStore.SearchHit> searchHits = List.of();
        private String lastSearchFilter;
        private List<String> lastOutputFields = List.of();

        @Override
        public boolean hasCollection(String collectionName) {
            return descriptions.containsKey(collectionName);
        }

        @Override
        public void createCollection(String collectionName, CollectionSchema schema, String distanceMetric,
                String indexType) {
            List<MilvusVectorStore.FieldDescription> fields = schema.getFields().stream()
                    .map(field -> new MilvusVectorStore.FieldDescription(
                            field.getName(),
                            field.getDtype(),
                            field.isPrimary(),
                            field.isAutoId(),
                            field.getMaxLength(),
                            field.getDim(),
                            field.getDescription()
                    ))
                    .toList();
            descriptions.put(collectionName, new MilvusVectorStore.CollectionDescription(
                    schema.getDescription(),
                    schema.isEnableDynamicField(),
                    fields,
                    new LinkedHashMap<>()
            ));
            rowsByCollection.putIfAbsent(collectionName, new ArrayList<>());
            distanceMetricByCollection.put(collectionName, distanceMetric);
            indexTypeByCollection.put(collectionName, indexType);
        }

        @Override
        public void dropCollection(String collectionName) {
            descriptions.remove(collectionName);
            rowsByCollection.remove(collectionName);
        }

        @Override
        public MilvusVectorStore.CollectionDescription describeCollection(String collectionName) {
            MilvusVectorStore.CollectionDescription description = descriptions.get(collectionName);
            if (description == null) {
                throw new IllegalArgumentException("Collection does not exist: " + collectionName);
            }
            return description;
        }

        @Override
        public void insert(String collectionName, List<Map<String, Object>> rows) {
            for (Map<String, Object> row : rows) {
                rowsByCollection.computeIfAbsent(collectionName, ignored -> new ArrayList<>())
                        .add(new LinkedHashMap<>(row));
            }
        }

        @Override
        public void flush(String collectionName) {
        }

        @Override
        public List<MilvusVectorStore.SearchHit> search(String collectionName, List<Double> queryVector,
                String vectorField, int limit, List<String> outputFields, Map<String, Object> searchParams,
                String filter) {
            lastSearchFilter = filter;
            lastOutputFields = outputFields;
            if (!searchHits.isEmpty()) {
                return searchHits;
            }
            String metric = String.valueOf(searchParams.getOrDefault("metric_type", "COSINE"));
            return rowsByCollection.getOrDefault(collectionName, List.of()).stream()
                    .filter(row -> matchesFilter(row, filter))
                    .map(row -> new MilvusVectorStore.SearchHit(
                            null,
                            row.get("doc_id"),
                            rawMetric(metric, queryVector, doubleList(row.get(vectorField))),
                            null,
                            new LinkedHashMap<>(row)
                    ))
                    .limit(limit)
                    .toList();
        }

        @Override
        public Map<String, Object> deleteByIds(String collectionName, List<String> ids) {
            int before = rowsByCollection.getOrDefault(collectionName, List.of()).size();
            rowsByCollection.computeIfAbsent(collectionName, ignored -> new ArrayList<>())
                    .removeIf(row -> ids.contains(String.valueOf(row.get("doc_id"))));
            return Map.of("delete_count", before - rowsByCollection.getOrDefault(collectionName, List.of()).size());
        }

        @Override
        public Map<String, Object> deleteByFilter(String collectionName, String filter) {
            int before = rowsByCollection.getOrDefault(collectionName, List.of()).size();
            rowsByCollection.computeIfAbsent(collectionName, ignored -> new ArrayList<>())
                    .removeIf(row -> matchesFilter(row, filter));
            return Map.of("delete_count", before - rowsByCollection.getOrDefault(collectionName, List.of()).size());
        }

        @Override
        public void loadCollection(String collectionName) {
            loadedCollections.add(collectionName);
        }

        @Override
        public String describeIndexMetric(String collectionName, String vectorField) {
            return distanceMetricByCollection.getOrDefault(collectionName, "COSINE");
        }

        @Override
        public List<Map<String, Object>> queryAll(String collectionName) {
            return rowsByCollection.getOrDefault(collectionName, List.of()).stream()
                    .map(LinkedHashMap::new)
                    .map(row -> (Map<String, Object>) row)
                    .toList();
        }

        @Override
        public void releaseCollection(String collectionName) {
        }

        @Override
        public void renameCollection(String oldName, String newName) {
            descriptions.put(newName, descriptions.remove(oldName));
            rowsByCollection.put(newName, rowsByCollection.remove(oldName));
            distanceMetricByCollection.put(newName, distanceMetricByCollection.remove(oldName));
            indexTypeByCollection.put(newName, indexTypeByCollection.remove(oldName));
        }

        @Override
        public void alterCollectionProperties(String collectionName, Map<String, String> properties) {
            MilvusVectorStore.CollectionDescription current = describeCollection(collectionName);
            Map<String, String> merged = new LinkedHashMap<>(current.properties());
            merged.putAll(properties);
            descriptions.put(collectionName, new MilvusVectorStore.CollectionDescription(
                    current.description(),
                    current.enableDynamicField(),
                    current.fields(),
                    merged
            ));
        }

        @Override
        public List<String> listCollections() {
            return new ArrayList<>(descriptions.keySet());
        }

        @Override
        public void close() {
        }

        private boolean matchesFilter(Map<String, Object> row, String filter) {
            if (filter == null || filter.isBlank()) {
                return true;
            }
            String[] parts = filter.split(" && ");
            for (String part : parts) {
                String[] pieces = part.split(" == ", 2);
                String field = pieces[0];
                String expected = pieces[1].replaceAll("^\"|\"$", "");
                if (!expected.equals(String.valueOf(row.get(field)))) {
                    return false;
                }
            }
            return true;
        }

        private double rawMetric(String metric, List<Double> query, List<Double> embedding) {
            if ("L2".equals(metric)) {
                double sum = 0.0d;
                for (int i = 0; i < Math.min(query.size(), embedding.size()); i++) {
                    double delta = query.get(i) - embedding.get(i);
                    sum += delta * delta;
                }
                return sum;
            }
            double dot = 0.0d;
            double queryNorm = 0.0d;
            double embeddingNorm = 0.0d;
            for (int i = 0; i < Math.min(query.size(), embedding.size()); i++) {
                dot += query.get(i) * embedding.get(i);
                queryNorm += query.get(i) * query.get(i);
                embeddingNorm += embedding.get(i) * embedding.get(i);
            }
            if ("IP".equals(metric)) {
                return dot;
            }
            if (queryNorm == 0.0d || embeddingNorm == 0.0d) {
                return -1.0d;
            }
            return dot / (Math.sqrt(queryNorm) * Math.sqrt(embeddingNorm));
        }

        private List<Double> doubleList(Object value) {
            if (!(value instanceof List<?> list)) {
                return List.of();
            }
            List<Double> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Number number) {
                    result.add(number.doubleValue());
                }
            }
            return result;
        }
    }
}
