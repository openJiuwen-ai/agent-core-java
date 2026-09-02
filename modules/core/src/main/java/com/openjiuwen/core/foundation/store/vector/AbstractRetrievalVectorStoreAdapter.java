/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector;

import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.common.RetrievalValidation;
import com.openjiuwen.core.retrieval.vector_store.SchemaMutableVectorStore;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;
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
 * Adapter that exposes retrieval vector stores through foundation.store's BaseVectorStore API.
 */
abstract class AbstractRetrievalVectorStoreAdapter extends BaseVectorStore {
    private final VectorStore delegate;

    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, CollectionSchema> schemas = new ConcurrentHashMap<>();

    /**
     * AbstractRetrievalVectorStoreAdapter.
     * 
     * @param delegate delegate
     * @since 0.1.7
     */
    protected AbstractRetrievalVectorStoreAdapter(VectorStore delegate) {
        this.delegate = delegate;
    }

    /**
     * delegate.
     * 
     * @return the result
     * @since 0.1.7
     */
    protected VectorStore delegate() {
        return delegate;
    }

    /**
     * createCollection.
     * 
     * @param collectionName collectionName
     * @param schema schema
     * @param kwargs kwargs
     * @throws Exception Exception
     * @since 0.1.7
     */
    @Override
    public void createCollection(String collectionName, Object schema, Map<String, Object> kwargs) throws Exception {
        CollectionSchema resolvedSchema = normalizeSchema(schema);
        schemas.put(collectionName, resolvedSchema);

        if (delegate instanceof SchemaMutableVectorStore schemaMutableStore) {
            Integer dimension = resolvedSchema.getVectorFields().stream().findFirst().map(FieldSchema::getDim).orElse(null);
            schemaMutableStore.ensureCollection(collectionName, schemaMutableStore.getIndexType(), dimension, kwargs);
            return;
        }

        Integer dimension = resolvedSchema.getVectorFields().stream().findFirst().map(FieldSchema::getDim).orElse(null);

        VectorStore scoped = delegate.withCollection(collectionName);
        String requestedIndexType = scoped.getIndexType();
        if (kwargs != null) {
            Object rawIndexType = kwargs.containsKey("indexType") ? kwargs.get("indexType") : kwargs.get("index_type");
            if (rawIndexType != null) {
                String normalized = String.valueOf(rawIndexType).toLowerCase(Locale.ROOT);
                if (RetrievalValidation.INDEX_TYPES.contains(normalized)) {
                    requestedIndexType = normalized;
                }
            }
        }
        scoped.ensureCollection(collectionName, requestedIndexType, dimension, kwargs == null ? Map.of() : kwargs);
    }

    /**
     * deleteCollection.
     * 
     * @param collectionName collectionName
     * @param kwargs kwargs
     * @throws Exception Exception
     * @since 0.1.7
     */
    @Override
    public void deleteCollection(String collectionName, Map<String, Object> kwargs) throws Exception {
        delegate.deleteTable(collectionName);
        schemas.remove(collectionName);
    }

    /**
     * collectionExists.
     * 
     * @param collectionName collectionName
     * @param kwargs kwargs
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    @Override
    public boolean collectionExists(String collectionName, Map<String, Object> kwargs) throws Exception {
        return delegate.tableExists(collectionName) || schemas.containsKey(collectionName);
    }

    /**
     * getSchema.
     * 
     * @param collectionName collectionName
     * @param kwargs kwargs
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    @Override
    public CollectionSchema getSchema(String collectionName, Map<String, Object> kwargs) throws Exception {
        if (delegate instanceof SchemaMutableVectorStore schemaMutableStore) {
            CollectionSchema backendSchema = schemaMutableStore.getSchema(collectionName);
            schemas.put(collectionName, backendSchema);
            return backendSchema;
        }
        return schemas.computeIfAbsent(collectionName, key -> defaultSchema());
    }

    /**
     * addDocs.
     * 
     * @param collectionName collectionName
     * @param docs docs
     * @param kwargs kwargs
     * @throws Exception Exception
     * @since 0.1.7
     */
    @Override
    public void addDocs(String collectionName, List<Map<String, Object>> docs, Map<String, Object> kwargs)
            throws Exception {
        VectorStore scoped = delegate.withCollection(collectionName);
        scoped.add(docs, kwargs != null && kwargs.get("batch_size") instanceof Number number ? number.intValue() : null,
                kwargs);
    }

    /**
     * search.
     * 
     * @param collectionName collectionName
     * @param queryVector queryVector
     * @param vectorField vectorField
     * @param topK topK
     * @param filters filters
     * @param kwargs kwargs
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    @Override
    public List<VectorSearchResult> search(String collectionName, List<Float> queryVector, String vectorField, int topK,
            Map<String, Object> filters, Map<String, Object> kwargs) throws Exception {
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
            mapped.add(VectorSearchResult.builder().score(result.getScore()).fields(fields).build());
        }
        return mapped;
    }

    /**
     * deleteDocsByIds.
     * 
     * @param collectionName collectionName
     * @param ids ids
     * @param kwargs kwargs
     * @throws Exception Exception
     * @since 0.1.7
     */
    @Override
    public void deleteDocsByIds(String collectionName, List<String> ids, Map<String, Object> kwargs) throws Exception {
        delegate.withCollection(collectionName).delete(ids, null, kwargs);
    }

    /**
     * deleteDocsByFilters.
     * 
     * @param collectionName collectionName
     * @param filters filters
     * @param kwargs kwargs
     * @throws Exception Exception
     * @since 0.1.7
     */
    @Override
    public void deleteDocsByFilters(String collectionName, Map<String, Object> filters, Map<String, Object> kwargs)
            throws Exception {
        delegate.withCollection(collectionName).delete(null, filters, kwargs);
    }

    /**
     * listCollectionNames.
     * 
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    @Override
    public List<String> listCollectionNames() throws Exception {
        if (delegate instanceof SchemaMutableVectorStore schemaMutableStore) {
            List<String> names = new ArrayList<>(schemaMutableStore.listCollectionNames());
            for (String cached : schemas.keySet()) {
                if (!names.contains(cached)) {
                    names.add(cached);
                }
            }
            return names;
        }
        return new ArrayList<>(schemas.keySet());
    }

    /**
     * updateSchema.
     * 
     * @param collectionName collectionName
     * @param operations operations
     * @throws Exception Exception
     * @since 0.1.7
     */
    @Override
    public void updateSchema(String collectionName, List<?> operations) throws Exception {
        if (delegate instanceof SchemaMutableVectorStore schemaMutableStore) {
            schemaMutableStore.updateSchema(collectionName, operations);
        }
        // Default: update in-memory schema cache if possible
        CollectionSchema current = schemas.get(collectionName);
        if (current != null) {
            CollectionSchema newSchema = VectorStoreUtils.computeNewSchema(current, operations);
            schemas.put(collectionName, newSchema);
        }
    }

    /**
     * Per-collection metadata cache.
     * 
     * @since 0.1.7
     */
    private final Map<String, Map<String, Object>> collectionMetadata = new ConcurrentHashMap<>();

    /**
     * updateCollectionMetadata.
     * 
     * @param collectionName collectionName
     * @param metadata metadata
     * @throws Exception Exception
     * @since 0.1.7
     */
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
        if (delegate instanceof SchemaMutableVectorStore schemaMutableStore) {
            schemaMutableStore.updateCollectionMetadata(collectionName, metadata);
        }
    }

    /**
     * getCollectionMetadata.
     * 
     * @param collectionName collectionName
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    @Override
    public Map<String, Object> getCollectionMetadata(String collectionName) throws Exception {
        // Return cached metadata if available
        Map<String, Object> cached = collectionMetadata.get(collectionName);
        if (cached != null) {
            return new LinkedHashMap<>(cached);
        }
        if (delegate instanceof SchemaMutableVectorStore schemaMutableStore) {
            Map<String, Object> result = schemaMutableStore.getCollectionMetadata(collectionName);
            collectionMetadata.put(collectionName, result);
            return new LinkedHashMap<>(result);
        }
        return new LinkedHashMap<>();
    }

    /**
     * Close the underlying vector store connection.
     * Delegates to the retrieval-layer store's close method.
     * 
     * @since 0.1.7
     */
    public void close() {
        delegate.close();
    }

    /**
     * normalizeSchema.
     * 
     * @param schema schema
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * defaultSchema.
     * 
     * @return the result
     * @since 0.1.7
     */
    private CollectionSchema defaultSchema() {
        return CollectionSchema.fromFields(
                List.of(FieldSchema.builder().name("id").dtype(VectorDataType.VARCHAR).isPrimary(true).maxLength(256)
                        .build(),
                        FieldSchema.builder().name("embedding").dtype(VectorDataType.FLOAT_VECTOR).dim(1536).build(),
                        FieldSchema.builder().name("text").dtype(VectorDataType.VARCHAR).maxLength(65535).build(),
                        FieldSchema.builder().name("metadata").dtype(VectorDataType.JSON).build()),
                "Default adapter schema", false);
    }
}
