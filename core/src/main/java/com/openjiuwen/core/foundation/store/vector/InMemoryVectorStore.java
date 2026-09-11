/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector;

import com.openjiuwen.core.retrieval.common.RetrievalValidation;
import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.spi.store.vector.BaseVectorStore;
import com.openjiuwen.spi.store.vector.CollectionSchema;
import com.openjiuwen.spi.store.vector.FieldSchema;
import com.openjiuwen.spi.store.vector.VectorDataType;
import com.openjiuwen.spi.store.vector.VectorSearchResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Foundation-store in-memory vector store.
 *
 * @since 0.1.7
 */
public class InMemoryVectorStore extends BaseVectorStore {
    private static final String DEFAULT_VECTOR_FIELD = "embedding";

    private final com.openjiuwen.core.retrieval.vector_store.InMemoryVectorStore delegate;
    private final Map<String, CollectionSchema> schemas = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> collectionMetadata = new ConcurrentHashMap<>();

    /**
     * Creates an in-memory vector store with default options.
     *
     * @since 0.1.7
     */
    public InMemoryVectorStore() {
        this(Map.of());
    }

    /**
     * Creates an in-memory vector store with the specified options.
     *
     * @param options vector store options
     * @since 0.1.7
     */
    public InMemoryVectorStore(Map<String, Object> options) {
        delegate = new com.openjiuwen.core.retrieval.vector_store.InMemoryVectorStore(config(options),
                indexType(options));
    }

    @Override
    public void createCollection(String collectionName, Object schema, Map<String, Object> kwargs) {
        CollectionSchema resolvedSchema = normalizeSchema(schema);
        schemas.put(collectionName, resolvedSchema);
        Integer dimension = resolvedSchema.getVectorFields().stream().findFirst().map(FieldSchema::getDim).orElse(null);
        com.openjiuwen.core.retrieval.vector_store.VectorStore scoped = delegate.withCollection(collectionName);
        String requestedIndexType = requestedIndexType(scoped.getIndexType(), kwargs);
        scoped.ensureCollection(collectionName, requestedIndexType, dimension, emptyIfNull(kwargs));
    }

    @Override
    public void deleteCollection(String collectionName, Map<String, Object> kwargs) {
        delegate.deleteTable(collectionName);
        schemas.remove(collectionName);
    }

    @Override
    public boolean collectionExists(String collectionName, Map<String, Object> kwargs) {
        return delegate.tableExists(collectionName) || schemas.containsKey(collectionName);
    }

    @Override
    public CollectionSchema getSchema(String collectionName, Map<String, Object> kwargs) {
        CollectionSchema cachedSchema = schemas.get(collectionName);
        if (cachedSchema != null) {
            return cachedSchema;
        }
        CollectionSchema backendSchema = delegate.getSchema(collectionName);
        schemas.put(collectionName, backendSchema);
        return backendSchema;
    }

    @Override
    public void addDocs(String collectionName, List<Map<String, Object>> docs, Map<String, Object> kwargs) {
        Integer batchSize = null;
        if (kwargs != null && kwargs.get("batch_size") instanceof Number number) {
            batchSize = number.intValue();
        }
        delegate.withCollection(collectionName).add(toBackendDocs(collectionName, docs), batchSize, kwargs);
    }

    @Override
    public List<VectorSearchResult> search(String collectionName, List<Float> queryVector, String vectorField,
            int topK, Map<String, Object> filters, Map<String, Object> kwargs) {
        Map<String, Object> options = kwargs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(kwargs);
        if (vectorField != null && !vectorField.isBlank()) {
            options.put("vector_field", vectorField);
        }
        List<SearchResult> results = delegate.withCollection(collectionName).search(queryVector, topK, filters,
                options);
        return mapSearchResults(results);
    }

    @Override
    public void deleteDocsByIds(String collectionName, List<String> ids, Map<String, Object> kwargs) {
        delegate.withCollection(collectionName).delete(ids, null, kwargs);
    }

    @Override
    public void deleteDocsByFilters(String collectionName, Map<String, Object> filters, Map<String, Object> kwargs) {
        delegate.withCollection(collectionName).delete(null, filters, kwargs);
    }

    @Override
    public List<String> listCollectionNames() {
        return new ArrayList<>(schemas.keySet());
    }

    @Override
    public void updateSchema(String collectionName, List<?> operations) {
        delegate.updateSchema(collectionName, operations);
        CollectionSchema current = schemas.get(collectionName);
        if (current != null) {
            schemas.put(collectionName, VectorStoreUtils.computeNewSchema(current, operations));
        }
    }

    @Override
    public void updateCollectionMetadata(String collectionName, Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return;
        }
        collectionMetadata.merge(collectionName, new LinkedHashMap<>(metadata), (existing, incoming) -> {
            existing.putAll(incoming);
            return existing;
        });
    }

    @Override
    public Map<String, Object> getCollectionMetadata(String collectionName) {
        Map<String, Object> cached = collectionMetadata.get(collectionName);
        if (cached == null) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(cached);
    }

    /**
     * Closes the underlying in-memory vector store.
     *
     * @since 0.1.7
     */
    public void close() {
        delegate.close();
    }

    private static VectorStoreConfig config(Map<String, Object> options) {
        return new VectorStoreConfig("chroma", stringOption(options, "database_name", "databaseName", "default"),
                stringOption(options, "collection_name", "collectionName", "default_collection"),
                stringOption(options, "distance_metric", "distanceMetric", "cosine"));
    }

    static String indexType(Map<String, Object> options) {
        return stringOption(options, "index_type", "indexType", "hybrid");
    }

    static String stringOption(Map<String, Object> options, String key, String alternateKey, String fallback) {
        Object value = null;
        if (options != null) {
            value = options.containsKey(key) ? options.get(key) : options.get(alternateKey);
        }
        return value == null ? fallback : String.valueOf(value);
    }

    private static String requestedIndexType(String defaultIndexType, Map<String, Object> options) {
        if (options == null) {
            return defaultIndexType;
        }
        Object rawIndexType = options.containsKey("indexType") ? options.get("indexType") : options.get("index_type");
        if (rawIndexType == null) {
            return defaultIndexType;
        }
        String normalized = String.valueOf(rawIndexType).toLowerCase(Locale.ROOT);
        return RetrievalValidation.INDEX_TYPES.contains(normalized) ? normalized : defaultIndexType;
    }

    private static Map<String, Object> emptyIfNull(Map<String, Object> options) {
        return options == null ? Map.of() : options;
    }

    private static CollectionSchema normalizeSchema(Object schema) {
        if (schema instanceof CollectionSchema collectionSchema) {
            return collectionSchema;
        }
        if (schema instanceof Map<?, ?> map) {
            return CollectionSchema.fromDict(castMap(map));
        }
        return defaultSchema();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    private static CollectionSchema defaultSchema() {
        return CollectionSchema.fromFields(
                List.of(FieldSchema.builder().name("id").dtype(VectorDataType.VARCHAR).isPrimary(true).maxLength(256)
                                .build(),
                        FieldSchema.builder().name(DEFAULT_VECTOR_FIELD).dtype(VectorDataType.FLOAT_VECTOR).dim(1536)
                                .build(),
                        FieldSchema.builder().name("text").dtype(VectorDataType.VARCHAR).maxLength(65535).build(),
                        FieldSchema.builder().name("metadata").dtype(VectorDataType.JSON).build()),
                "Default adapter schema", false);
    }

    private List<Map<String, Object>> toBackendDocs(String collectionName, List<Map<String, Object>> docs) {
        CollectionSchema schema = schemas.get(collectionName);
        String sourceVectorField = schema == null
                ? DEFAULT_VECTOR_FIELD
                : schema.getVectorFields().stream().findFirst().map(FieldSchema::getName)
                        .orElse(delegate.getVectorField());
        if (sourceVectorField.equals(delegate.getVectorField())) {
            return docs;
        }
        List<Map<String, Object>> backendDocs = new ArrayList<>();
        for (Map<String, Object> doc : docs) {
            Map<String, Object> backendDoc = new LinkedHashMap<>(doc);
            if (doc.containsKey(sourceVectorField)) {
                backendDoc.put(delegate.getVectorField(), doc.get(sourceVectorField));
            }
            backendDocs.add(backendDoc);
        }
        return backendDocs;
    }

    private static List<VectorSearchResult> mapSearchResults(List<SearchResult> results) {
        List<VectorSearchResult> mapped = new ArrayList<>();
        for (SearchResult result : results) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("id", result.getId());
            fields.put("text", result.getText());
            Map<String, Object> metadata = result.getMetadata();
            if (metadata != null) {
                fields.putAll(metadata);
            }
            mapped.add(VectorSearchResult.builder().score(result.getScore()).fields(fields).build());
        }
        return mapped;
    }
}
