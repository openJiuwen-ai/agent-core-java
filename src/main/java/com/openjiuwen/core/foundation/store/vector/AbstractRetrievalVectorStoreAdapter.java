/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.foundation.store.vector;

import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.vector_store.MilvusVectorStore;
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
        if (scoped instanceof MilvusVectorStore milvusVectorStore) {
            String indexType = kwargs != null && kwargs.containsKey("index_type")
                    ? String.valueOf(kwargs.get("index_type"))
                    : milvusVectorStore.getIndexType();
            milvusVectorStore.ensureCollection(collectionName, indexType, dimension);
        }
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
        List<SearchResult> results = scoped.search(queryVector, topK, filters, kwargs);
        List<VectorSearchResult> mapped = new ArrayList<>();
        for (SearchResult result : results) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("id", result.getId());
            fields.put("text", result.getText());
            fields.put("metadata", result.getMetadata());
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
                FieldSchema.builder().name("vector").dtype(VectorDataType.FLOAT_VECTOR).dim(1536).build(),
                FieldSchema.builder().name("text").dtype(VectorDataType.VARCHAR).maxLength(65535).build(),
                FieldSchema.builder().name("metadata").dtype(VectorDataType.JSON).build()
        ), "Default adapter schema", false);
    }
}
