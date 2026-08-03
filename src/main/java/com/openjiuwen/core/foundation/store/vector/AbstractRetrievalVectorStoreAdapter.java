/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
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
    }

    @Override
    public void deleteCollection(String collectionName, Map<String, Object> kwargs) throws Exception {
        delegate.deleteTable(collectionName);
        schemas.remove(collectionName);
    }

    @Override
    public boolean collectionExists(String collectionName, Map<String, Object> kwargs) throws Exception {
        Boolean exists = delegate.tableExists(collectionName).join();
        return (exists != null && exists) || schemas.containsKey(collectionName);
    }

    @Override
    public CollectionSchema getSchema(String collectionName, Map<String, Object> kwargs) throws Exception {
        if (delegate instanceof SchemaMutableVectorStore schemaMutableStore) {
            try {
                CollectionSchema backendSchema = schemaMutableStore.getSchema(collectionName);
                schemas.put(collectionName, backendSchema);
                return backendSchema;
            } catch (Exception ignored) {
                // Fall through to cached schema
            }
        }
        return schemas.computeIfAbsent(collectionName, key -> defaultSchema());
    }

    @Override
    public void addDocs(
            String collectionName,
            List<Map<String, Object>> docs,
            Map<String, Object> kwargs) throws Exception {
        delegate.add(
                docs,
                kwargs != null && kwargs.get("batch_size") instanceof Number number ? number.intValue() : null,
                kwargs).join();
    }

    @Override
    public List<VectorSearchResult> search(String collectionName,
                                           List<Float> queryVector,
                                           String vectorField,
                                           int topK,
                                           Map<String, Object> filters,
                                           Map<String, Object> kwargs) throws Exception {
        Map<String, Object> options = kwargs != null ? new LinkedHashMap<>(kwargs) : new LinkedHashMap<>();
        if (vectorField != null && !vectorField.isBlank()) {
            options.put("vector_field", vectorField);
        }
        List<Double> doubleVector = new ArrayList<>(queryVector.size());
        for (Float f : queryVector) {
            doubleVector.add(f != null ? f.doubleValue() : 0.0);
        }
        VectorStore.VectorStoreFilter vsFilter = filters != null
                ? VectorStore.VectorStoreFilter.ofMap(filters)
                : VectorStore.VectorStoreFilter.none();
        List<RetrievalResult> results = delegate.search(doubleVector, topK, vsFilter, options).join();
        List<VectorSearchResult> mapped = new ArrayList<>();
        for (RetrievalResult result : results) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("id", result.getDocId());
            fields.put("text", result.getText());
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
        delegate.delete(ids, VectorStore.DeleteFilter.none(), kwargs).join();
    }

    @Override
    public void deleteDocsByFilters(
            String collectionName,
            Map<String, Object> filters,
            Map<String, Object> kwargs) throws Exception {
        VectorStore.DeleteFilter deleteFilter = filters != null
                ? VectorStore.DeleteFilter.ofExpression(null)
                : VectorStore.DeleteFilter.none();
        delegate.delete(null, deleteFilter, kwargs).join();
    }

    @Override
    public List<String> listCollectionNames() throws Exception {
        if (delegate instanceof SchemaMutableVectorStore schemaMutableStore) {
            try {
                List<String> names = new ArrayList<>(schemaMutableStore.listCollectionNames());
                for (String cached : schemas.keySet()) {
                    if (!names.contains(cached)) {
                        names.add(cached);
                    }
                }
                return names;
            } catch (Exception ignored) {
                // Fall through
            }
        }
        return new ArrayList<>(schemas.keySet());
    }

    @Override
    public void updateSchema(String collectionName, List<?> operations) throws Exception {
        if (delegate instanceof SchemaMutableVectorStore schemaMutableStore) {
            schemaMutableStore.updateSchema(collectionName, operations);
        }
        CollectionSchema current = schemas.get(collectionName);
        if (current != null) {
            @SuppressWarnings("unchecked")
            List<BaseOperation> typedOps = (List<BaseOperation>) operations;
            com.openjiuwen.core.foundation.store.CollectionSchema foundationSchema =
                    com.openjiuwen.core.foundation.store.CollectionSchema.fromDict(current.toDict());
            com.openjiuwen.core.foundation.store.CollectionSchema newFoundationSchema =
                    VectorStoreUtils.computeNewSchema(foundationSchema, typedOps);
            schemas.put(collectionName, CollectionSchema.fromDict(newFoundationSchema.toDict()));
        }
    }

    private final Map<String, Map<String, Object>> collectionMetadata = new ConcurrentHashMap<>();

    @Override
    public void updateCollectionMetadata(String collectionName, Map<String, Object> metadata) throws Exception {
        if (metadata == null || metadata.isEmpty()) {
            return;
        }
        collectionMetadata.merge(collectionName, new LinkedHashMap<>(metadata), (existing, incoming) -> {
            existing.putAll(incoming);
            return existing;
        });
        if (delegate instanceof SchemaMutableVectorStore schemaMutableStore) {
            try {
                schemaMutableStore.updateCollectionMetadata(collectionName, metadata);
            } catch (Exception e) {
                com.openjiuwen.core.common.logging.Loggers.STORE.warn(
                        "Failed to update collection metadata on backend: %s", e.getMessage());
            }
        }
    }

    @Override
    public Map<String, Object> getCollectionMetadata(String collectionName) throws Exception {
        Map<String, Object> cached = collectionMetadata.get(collectionName);
        if (cached != null) {
            return new LinkedHashMap<>(cached);
        }
        if (delegate instanceof SchemaMutableVectorStore schemaMutableStore) {
            try {
                Map<String, Object> result = schemaMutableStore.getCollectionMetadata(collectionName);
                if (result != null) {
                    collectionMetadata.put(collectionName, result);
                    return result;
                }
            } catch (Exception e) {
                // Fall through to empty map
            }
        }
        return new LinkedHashMap<>();
    }

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
