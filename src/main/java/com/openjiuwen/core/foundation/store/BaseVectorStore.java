/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store;

import com.openjiuwen.core.memory.migration.operation.BaseOperation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Mirrors Python's {@code BaseVectorStore} in
 * {@code openjiuwen/core/foundation/store/base_vector_store.py}.
 */
public abstract class BaseVectorStore {

    public abstract CompletableFuture<Void> createCollection(
            String collectionName,
            Object schema,
            Map<String, Object> kwargs
    );

    public abstract CompletableFuture<Void> deleteCollection(
            String collectionName,
            Map<String, Object> kwargs
    );

    public abstract CompletableFuture<Boolean> collectionExists(
            String collectionName,
            Map<String, Object> kwargs
    );

    public abstract CompletableFuture<CollectionSchema> getSchema(
            String collectionName,
            Map<String, Object> kwargs
    );

    public abstract CompletableFuture<Void> addDocs(
            String collectionName,
            List<Map<String, Object>> docs,
            Map<String, Object> kwargs
    );

    public abstract CompletableFuture<List<VectorSearchResult>> search(
            String collectionName,
            List<Double> queryVector,
            String vectorField,
            int topK,
            Map<String, Object> filters,
            Map<String, Object> kwargs
    );

    public abstract CompletableFuture<Void> deleteDocsByIds(
            String collectionName,
            List<String> ids,
            Map<String, Object> kwargs
    );

    public abstract CompletableFuture<Void> deleteDocsByFilters(
            String collectionName,
            Map<String, Object> filters,
            Map<String, Object> kwargs
    );

    public abstract CompletableFuture<List<String>> listCollectionNames();

    public abstract CompletableFuture<Void> updateSchema(String collectionName, List<BaseOperation> operations);

    public abstract CompletableFuture<Void> updateCollectionMetadata(String collectionName, Map<String, Object> metadata);

    public abstract CompletableFuture<Map<String, Object>> getCollectionMetadata(String collectionName);
}
