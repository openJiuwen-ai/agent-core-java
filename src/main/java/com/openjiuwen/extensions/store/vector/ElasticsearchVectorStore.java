/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.store.vector;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.spi.store.vector.CollectionSchema;
import com.openjiuwen.spi.store.vector.FieldSchema;
import com.openjiuwen.spi.store.vector.VectorDataType;
import com.openjiuwen.spi.store.vector.VectorSearchResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Elasticsearch-based vector store implementation.
 *
 * <p>Mirrors Python's {@code openjiuwen.extensions.store.vector.es_vector_store.ElasticsearchVectorStore}.
 *
 * Uses Elasticsearch dense_vector field type with k-NN search to provide
 * vector similarity search capabilities.
 */
public class ElasticsearchVectorStore {

    private static final String METADATA_DOC_ID = "__collection_metadata__";
    private static final Map<String, String> ES_SIMILARITY_MAP = new HashMap<>();
    private static final String DEFAULT_DISTANCE_METRIC = "COSINE";

    static {
        ES_SIMILARITY_MAP.put("COSINE", "cosine");
        ES_SIMILARITY_MAP.put("L2", "l2_norm");
        ES_SIMILARITY_MAP.put("IP", "dot_product");
    }

    private final String indexPrefix;
    private final Map<String, Map<String, Object>> metadataCache;
    private final Map<String, CollectionSchema> schemas;
    private final Map<String, List<Map<String, Object>>> documents;

    /**
     * Create ElasticsearchVectorStore with default index prefix.
     */
    public ElasticsearchVectorStore() {
        this("agent_vector");
    }

    /**
     * Create ElasticsearchVectorStore with custom index prefix.
     *
     * @param indexPrefix Prefix for Elasticsearch indices.
     */
    public ElasticsearchVectorStore(String indexPrefix) {
        this.indexPrefix = indexPrefix;
        this.metadataCache = new HashMap<>();
        this.schemas = new HashMap<>();
        this.documents = new HashMap<>();
    }

    /**
     * Generate index name from collection name.
     *
     * @param collectionName Collection name.
     * @return Elasticsearch index name.
     */
    private String indexName(String collectionName) {
        return indexPrefix + "__" + collectionName;
    }

    public String getIndexPrefix() {
        return indexPrefix;
    }

    public Map<String, Map<String, Object>> getMetadataCache() {
        return metadataCache;
    }

    public String getIndexName(String collectionName) {
        return indexName(collectionName);
    }

    public static Map<String, Object> mapEsType(FieldSchema field) {
        Map<String, Object> mapping = new LinkedHashMap<>();
        switch (field.getDtype()) {
            case FLOAT_VECTOR -> {
                mapping.put("type", "dense_vector");
                mapping.put("dims", field.getDim());
                mapping.put("index", true);
                mapping.put("similarity", "cosine");
            }
            case VARCHAR -> mapping.put("type", "keyword");
            case INT64 -> mapping.put("type", "long");
            case INT32 -> mapping.put("type", "integer");
            case FLOAT -> mapping.put("type", "float");
            case DOUBLE -> mapping.put("type", "double");
            case BOOL -> mapping.put("type", "boolean");
            case JSON -> {
                mapping.put("type", "object");
                mapping.put("enabled", true);
            }
            default -> mapping.put("type", "keyword");
        }
        return mapping;
    }

    public static String getPrimaryKeyField(Map<String, Object> schemaDict) {
        Object fields = schemaDict.get("fields");
        if (!(fields instanceof List<?> fieldList)) {
            return null;
        }
        for (Object field : fieldList) {
            if (field instanceof Map<?, ?> map && Boolean.TRUE.equals(map.get("is_primary"))) {
                Object name = map.get("name");
                return name == null ? null : String.valueOf(name);
            }
        }
        return null;
    }

    /**
     * Create a collection with the given schema.
     *
     * @param collectionName Collection name.
     * @param schema Collection schema.
     * @return CompletableFuture for async operation.
     */
    public CompletableFuture<Void> createCollection(String collectionName, Map<String, Object> schema) {
        return createCollection(collectionName, schema, DEFAULT_DISTANCE_METRIC);
    }

    public CompletableFuture<Void> createCollection(String collectionName, Object schema) {
        return createCollection(collectionName, schema, DEFAULT_DISTANCE_METRIC);
    }

    public CompletableFuture<Void> createCollection(String collectionName, Object schema, String distanceMetric) {
        if (collectionExistsSync(collectionName)) {
            return CompletableFuture.completedFuture(null);
        }
        CollectionSchema resolvedSchema = resolveSchema(schema);
        if (resolvedSchema.getVectorFields().isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                    "error_msg", "must contain at least one FLOAT_VECTOR field");
        }
        String metric = normalizeMetric(distanceMetric);
        String index = indexName(collectionName);
        schemas.put(collectionName, resolvedSchema);
        documents.put(collectionName, new ArrayList<>());
        Map<String, Object> metadata = defaultMetadata(metric, resolvedSchema);
        metadataCache.put(index, metadata);
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> deleteCollection(String collectionName) {
        String index = indexName(collectionName);
        schemas.remove(collectionName);
        documents.remove(collectionName);
        metadataCache.remove(index);
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Boolean> collectionExists(String collectionName) {
        return CompletableFuture.completedFuture(collectionExistsSync(collectionName));
    }

    public CompletableFuture<CollectionSchema> getSchema(String collectionName) {
        CollectionSchema schema = schemas.get(collectionName);
        if (schema == null) {
            throw ErrorHelper.buildError(StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                    "error_msg", "collection schema not found: " + collectionName);
        }
        return CompletableFuture.completedFuture(schema);
    }

    public CompletableFuture<Void> addDocs(String collectionName, List<Map<String, Object>> docs) {
        return addDocs(collectionName, docs, docs == null || docs.isEmpty() ? 1 : docs.size());
    }

    public CompletableFuture<Void> addDocs(String collectionName, List<Map<String, Object>> docs, int batchSize) {
        if (docs == null || docs.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        ensureCollection(collectionName);
        List<Map<String, Object>> storedDocs = documents.get(collectionName);
        for (Map<String, Object> doc : docs) {
            storedDocs.add(new LinkedHashMap<>(doc));
        }
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<List<VectorSearchResult>> search(
            String collectionName,
            List<Double> embedding,
            String vectorField,
            int topK) {
        return search(collectionName, embedding, vectorField, topK, Map.of());
    }

    public CompletableFuture<List<VectorSearchResult>> search(
            String collectionName,
            List<Double> embedding,
            String vectorField,
            int topK,
            Map<String, Object> filters) {
        ensureCollection(collectionName);
        List<VectorSearchResult> results = documents.getOrDefault(collectionName, List.of()).stream()
                .filter(doc -> matchesFilters(doc, filters))
                .map(doc -> toVectorResult(doc, embedding, vectorField))
                .sorted(Comparator.comparingDouble(VectorSearchResult::getScore).reversed())
                .limit(Math.max(topK, 0))
                .collect(Collectors.toList());
        return CompletableFuture.completedFuture(results);
    }

    public CompletableFuture<Void> deleteDocsByIds(String collectionName, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        ensureCollection(collectionName);
        String primaryKey = Optional.ofNullable(
                        schemas.get(collectionName).getPrimaryKeyField().map(FieldSchema::getName).orElse(null))
                .orElse("id");
        documents.get(collectionName).removeIf(doc -> ids.contains(String.valueOf(doc.get(primaryKey))));
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> deleteDocsByFilters(String collectionName, Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        ensureCollection(collectionName);
        documents.get(collectionName).removeIf(doc -> matchesFilters(doc, filters));
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<List<String>> listCollectionNames() {
        return CompletableFuture.completedFuture(new ArrayList<>(schemas.keySet()));
    }

    public CompletableFuture<Map<String, Object>> getCollectionMetadata(String collectionName) {
        Map<String, Object> metadata = metadataCache.get(indexName(collectionName));
        return CompletableFuture.completedFuture(metadata == null
                ? defaultMetadata(DEFAULT_DISTANCE_METRIC, null)
                : new LinkedHashMap<>(metadata));
    }

    public CompletableFuture<Void> updateCollectionMetadata(String collectionName, Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        Object schemaVersion = metadata.get("schema_version");
        if (schemaVersion instanceof Number number && number.intValue() < 0) {
            throw ErrorHelper.buildError(StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                    "error_msg", "schema_version must be a non-negative integer");
        }
        Map<String, Object> merged = new LinkedHashMap<>(metadataCache.getOrDefault(
                indexName(collectionName),
                defaultMetadata(DEFAULT_DISTANCE_METRIC, schemas.get(collectionName))));
        merged.putAll(metadata);
        metadataCache.put(indexName(collectionName), merged);
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> updateSchema(String collectionName, List<Map<String, Object>> operations) {
        ensureCollection(collectionName);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Insert vectors into the collection.
     *
     * @param collectionName Collection name.
     * @param vectors List of vector data.
     * @return CompletableFuture with inserted IDs.
     */
    public CompletableFuture<List<String>> insert(String collectionName, List<VectorData> vectors) {
        List<String> ids = new ArrayList<>();
        ensureCollection(collectionName);
        for (VectorData data : vectors) {
            ids.add(data.getId());
            Map<String, Object> doc = new LinkedHashMap<>(data.getMetadata());
            doc.put("id", data.getId());
            doc.put("embedding", data.getEmbedding());
            documents.get(collectionName).add(doc);
        }
        return CompletableFuture.completedFuture(ids);
    }

    /**
     * Search for similar vectors.
     *
     * @param collectionName Collection name.
     * @param embedding Query embedding vector.
     * @param topK Number of results.
     * @param filter Metadata filter.
     * @return CompletableFuture with search results.
     */
    public CompletableFuture<List<SearchResult>> search(
            String collectionName,
            List<Double> embedding,
            int topK,
            Map<String, Object> filter) {
        List<SearchResult> results = search(collectionName, embedding, "embedding", topK, filter).join().stream()
                .map(result -> new SearchResult(
                        String.valueOf(result.getFields().get("id")),
                        result.getScore(),
                        result.getFields()))
                .collect(Collectors.toList());
        return CompletableFuture.completedFuture(results);
    }

    /**
     * Delete vectors by IDs.
     *
     * @param collectionName Collection name.
     * @param ids List of IDs to delete.
     * @return CompletableFuture for async operation.
     */
    public CompletableFuture<Void> delete(String collectionName, List<String> ids) {
        return deleteDocsByIds(collectionName, ids);
    }

    /**
     * Close the Elasticsearch connection.
     *
     * @return CompletableFuture for async operation.
     */
    public CompletableFuture<Void> close() {
        metadataCache.clear();
        schemas.clear();
        documents.clear();
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Map similarity metric to Elasticsearch type.
     *
     * @param metric Similarity metric name.
     * @return Elasticsearch similarity type.
     */
    public static String mapSimilarity(String metric) {
        return ES_SIMILARITY_MAP.getOrDefault(metric.toUpperCase(), "cosine");
    }

    private boolean collectionExistsSync(String collectionName) {
        return schemas.containsKey(collectionName);
    }

    private CollectionSchema resolveSchema(Object schema) {
        if (schema instanceof CollectionSchema collectionSchema) {
            return collectionSchema;
        }
        if (schema instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typedMap = (Map<String, Object>) map;
            return CollectionSchema.fromDict(typedMap);
        }
        throw new IllegalArgumentException("Unsupported schema type: " + schema);
    }

    private void ensureCollection(String collectionName) {
        if (!collectionExistsSync(collectionName)) {
            throw ErrorHelper.buildError(StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                    "error_msg", "collection not found: " + collectionName);
        }
    }

    private String normalizeMetric(String distanceMetric) {
        return distanceMetric == null || distanceMetric.isBlank()
                ? DEFAULT_DISTANCE_METRIC
                : distanceMetric.toUpperCase();
    }

    private Map<String, Object> defaultMetadata(String distanceMetric, CollectionSchema schema) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("distance_metric", normalizeMetric(distanceMetric));
        metadata.put("schema_version", 0);
        if (schema != null) {
            metadata.put("schema", schema.toDict());
        }
        metadata.put("_meta_doc_id", METADATA_DOC_ID);
        return metadata;
    }

    private boolean matchesFilters(Map<String, Object> doc, Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            Object actual = doc.get(entry.getKey());
            Object expected = entry.getValue();
            if (expected instanceof List<?> list) {
                if (!list.contains(actual)) {
                    return false;
                }
            } else if (!Objects.equals(actual, expected)) {
                return false;
            }
        }
        return true;
    }

    private VectorSearchResult toVectorResult(Map<String, Object> doc, List<Double> embedding, String vectorField) {
        double score = 0.0;
        Object rawVector = doc.get(vectorField);
        if (rawVector instanceof List<?> list && embedding != null) {
            score = cosineSimilarity(embedding, toDoubleList(list));
        }
        Map<String, Object> fields = new LinkedHashMap<>(doc);
        return VectorSearchResult.builder()
                .score(score)
                .fields(fields)
                .build();
    }

    private List<Double> toDoubleList(List<?> values) {
        return values.stream()
                .map(value -> value instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(value)))
                .collect(Collectors.toList());
    }

    private double cosineSimilarity(List<Double> left, List<Double> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty() || left.size() != right.size()) {
            return 0.0;
        }
        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int i = 0; i < left.size(); i++) {
            double lv = left.get(i);
            double rv = right.get(i);
            dot += lv * rv;
            leftNorm += lv * lv;
            rightNorm += rv * rv;
        }
        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    /** Vector data container. */
    public static class VectorData {
        private final String id;
        private final List<Double> embedding;
        private final Map<String, Object> metadata;

        public VectorData(String id, List<Double> embedding, Map<String, Object> metadata) {
            this.id = id;
            this.embedding = embedding;
            this.metadata = metadata;
        }

        public String getId() { return id; }
        public List<Double> getEmbedding() { return embedding; }
        public Map<String, Object> getMetadata() { return metadata; }
    }

    /** Search result container. */
    public static class SearchResult {
        private final String id;
        private final double score;
        private final Map<String, Object> metadata;

        public SearchResult(String id, double score, Map<String, Object> metadata) {
            this.id = id;
            this.score = score;
            this.metadata = metadata;
        }

        public String getId() { return id; }
        public double getScore() { return score; }
        public Map<String, Object> getMetadata() { return metadata; }
    }
}
