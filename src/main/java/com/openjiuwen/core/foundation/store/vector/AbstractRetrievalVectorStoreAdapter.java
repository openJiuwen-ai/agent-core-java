/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.foundation.store.vector;

import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.common.RetrievalValidation;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;
import com.openjiuwen.spi.store.vector.BaseVectorStore;
import com.openjiuwen.spi.store.vector.CollectionSchema;
import com.openjiuwen.spi.store.vector.FieldSchema;
import com.openjiuwen.spi.store.vector.VectorDataType;
import com.openjiuwen.spi.store.vector.VectorSearchResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter that exposes retrieval vector stores through foundation.store's BaseVectorStore API.
 */
abstract class AbstractRetrievalVectorStoreAdapter extends BaseVectorStore {

    private final VectorStore delegate;
    private final Map<String, CollectionSchema> schemas = new ConcurrentHashMap<>();

    protected AbstractRetrievalVectorStoreAdapter(VectorStore delegate) {
        this.delegate = delegate;
    }

    protected VectorStore delegate() {
        return delegate;
    }

    @Override
    public void createCollection(String collectionName, Object schema, Map<String, Object> kwargs) throws Exception {
        CollectionSchema resolvedSchema = normalizeSchema(schema);
        schemas.put(collectionName, resolvedSchema);

        Integer dimension = resolvedSchema.getVectorFields().stream()
                .findFirst()
                .map(FieldSchema::getDim)
                .orElse(null);

        VectorStore scoped = delegate.withCollection(collectionName);
        String requestedIndexType = scoped.getIndexType();
        if (kwargs != null) {
            Object rawIndexType = kwargs.containsKey("indexType") ? kwargs.get("indexType") : kwargs.get("index_type");
            if (rawIndexType != null) {
                String normalized = String.valueOf(rawIndexType).toLowerCase();
                if (RetrievalValidation.INDEX_TYPES.contains(normalized)) {
                    requestedIndexType = normalized;
                }
            }
        }
        scoped.ensureCollection(collectionName, requestedIndexType, dimension, kwargs == null ? Map.of() : kwargs);
    }

    @Override
    public void deleteCollection(String collectionName, Map<String, Object> kwargs) throws Exception {
        delegate.deleteTable(collectionName);
        schemas.remove(collectionName);
    }

    @Override
    public boolean collectionExists(String collectionName, Map<String, Object> kwargs) throws Exception {
        return delegate.tableExists(collectionName) || schemas.containsKey(collectionName);
    }

    @Override
    public CollectionSchema getSchema(String collectionName, Map<String, Object> kwargs) throws Exception {
        return schemas.computeIfAbsent(collectionName, key -> defaultSchema());
    }

    @Override
    public void addDocs(String collectionName, List<Map<String, Object>> docs, Map<String, Object> kwargs) throws Exception {
        VectorStore scoped = delegate.withCollection(collectionName);
        scoped.add(docs, kwargs != null && kwargs.get("batch_size") instanceof Number number ? number.intValue() : null, kwargs);
    }

    @Override
    public List<VectorSearchResult> search(String collectionName,
                                           List<Float> queryVector,
                                           String vectorField,
                                           int topK,
                                           Map<String, Object> filters,
                                           Map<String, Object> kwargs) throws Exception {
        VectorStore scoped = delegate.withCollection(collectionName);
        // Pass vectorField through via options so the backend can use it
        Map<String, Object> options = kwargs != null ? new LinkedHashMap<>(kwargs) : new LinkedHashMap<>();
        if (vectorField != null && !vectorField.isBlank()) {
            options.put("vector_field", vectorField);
        }
        List<SearchResult> results = scoped.search(queryVector, topK, filters, options);
        List<VectorSearchResult> mapped = new ArrayList<>();
        for (SearchResult result : results) {
            // Build fields including all available data, not just id/text/metadata
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("id", result.getId());
            fields.put("text", result.getText());
            // Merge metadata fields directly into the result fields map
            Map<String, Object> metadata = result.getMetadata();
            if (metadata != null) {
                fields.putAll(metadata);
            }
            mapped.add(VectorSearchResult.builder()
                    .score(result.getScore())
                    .fields(fields)
                    .build());
        }
        return mapped;
    }

    @Override
    public void deleteDocsByIds(String collectionName, List<String> ids, Map<String, Object> kwargs) throws Exception {
        delegate.withCollection(collectionName).delete(ids, null, kwargs);
    }

    @Override
    public void deleteDocsByFilters(String collectionName, Map<String, Object> filters, Map<String, Object> kwargs) throws Exception {
        delegate.withCollection(collectionName).delete(null, filters, kwargs);
    }

    @Override
    public List<String> listCollectionNames() throws Exception {
        // Delegate to real backend when possible (e.g. Milvus can list collections)
        if (delegate instanceof com.openjiuwen.core.retrieval.vector_store.MilvusVectorStore milvusStore) {
            List<String> names = new ArrayList<>(milvusStore.getClient()
                    .listCollections().getCollectionNames());
            // Merge with locally cached names
            for (String cached : schemas.keySet()) {
                if (!names.contains(cached)) {
                    names.add(cached);
                }
            }
            return names;
        }
        // Fallback: return known collection names from cache
        return new ArrayList<>(schemas.keySet());
    }

    @Override
    public void updateSchema(String collectionName, List<?> operations) throws Exception {
        // Default: update in-memory schema cache if possible
        CollectionSchema current = schemas.get(collectionName);
        if (current != null) {
            CollectionSchema newSchema = VectorStoreUtils.computeNewSchema(current, operations);
            schemas.put(collectionName, newSchema);
        }
    }

    /** Per-collection metadata cache. */
    private final Map<String, Map<String, Object>> collectionMetadata = new ConcurrentHashMap<>();

    @Override
    public void updateCollectionMetadata(String collectionName, Map<String, Object> metadata) throws Exception {
        if (metadata == null || metadata.isEmpty()) {
            return;
        }
        // Update local metadata cache
        collectionMetadata.merge(collectionName, new LinkedHashMap<>(metadata), (existing, incoming) -> {
            existing.putAll(incoming);
            return existing;
        });
        // Attempt to propagate to real backend when possible
        if (delegate instanceof com.openjiuwen.core.retrieval.vector_store.MilvusVectorStore milvusStore) {
            Map<String, String> properties = new LinkedHashMap<>();
            for (var entry : metadata.entrySet()) {
                properties.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
            try {
                milvusStore.getClient().alterCollectionProperties(
                        io.milvus.v2.service.collection.request.AlterCollectionPropertiesReq.builder()
                                .collectionName(collectionName)
                                .properties(properties)
                                .build());
            } catch (Exception e) {
                // Log but don't fail - metadata update is best-effort on some backends
                com.openjiuwen.core.common.logging.Loggers.STORE.warn(
                        "Failed to update collection metadata on backend: %s", e.getMessage());
            }
        }
    }

    @Override
    public Map<String, Object> getCollectionMetadata(String collectionName) throws Exception {
        // Return cached metadata if available
        Map<String, Object> cached = collectionMetadata.get(collectionName);
        if (cached != null) {
            return new LinkedHashMap<>(cached);
        }
        // Attempt to fetch from real backend
        if (delegate instanceof com.openjiuwen.core.retrieval.vector_store.MilvusVectorStore milvusStore) {
            try {
                var info = milvusStore.getClient().describeCollection(
                        io.milvus.v2.service.collection.request.DescribeCollectionReq.builder()
                                .collectionName(collectionName).build());
                Map<String, Object> result = new LinkedHashMap<>();
                if (info.getProperties() != null) {
                    result.putAll(info.getProperties());
                }
                collectionMetadata.put(collectionName, result);
                return result;
            } catch (Exception e) {
                // Fall through to empty map
            }
        }
        return new LinkedHashMap<>();
    }

    /**
     * Close the underlying vector store connection.
     * Delegates to the retrieval-layer store's close method.
     */
    public void close() {
        delegate.close();
    }

    private CollectionSchema normalizeSchema(Object schema) {
        if (schema instanceof CollectionSchema collectionSchema) {
            return collectionSchema;
        }
        if (schema instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typedMap = (Map<String, Object>) map;
            return CollectionSchema.fromDict(typedMap);
        }
        return defaultSchema();
    }

    private CollectionSchema defaultSchema() {
        return CollectionSchema.fromFields(List.of(
                FieldSchema.builder().name("id").dtype(VectorDataType.VARCHAR).isPrimary(true).maxLength(256).build(),
                FieldSchema.builder().name("embedding").dtype(VectorDataType.FLOAT_VECTOR).dim(1536).build(),
                FieldSchema.builder().name("text").dtype(VectorDataType.VARCHAR).maxLength(65535).build(),
                FieldSchema.builder().name("metadata").dtype(VectorDataType.JSON).build()
        ), "Default adapter schema", false);
    }
}
