/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store.vector;

import com.openjiuwen.core.memory.migration.operation.BaseOperation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Compatibility facade for the 0.1.12 synchronous vector store SPI.
 *
 * <p>Mirrors Python's {@code BaseVectorStore} in
 * {@code openjiuwen/core/foundation/store/base_vector_store.py}.</p>
 */
public abstract class BaseVectorStore {

    public abstract void createCollection(String collectionName, Object schema,
                                          Map<String, Object> kwargs) throws Exception;

    public abstract void deleteCollection(String collectionName,
                                          Map<String, Object> kwargs) throws Exception;

    public abstract boolean collectionExists(String collectionName,
                                             Map<String, Object> kwargs) throws Exception;

    public abstract CollectionSchema getSchema(String collectionName,
                                               Map<String, Object> kwargs) throws Exception;

    public abstract void addDocs(String collectionName,
                                 List<Map<String, Object>> docs,
                                 Map<String, Object> kwargs) throws Exception;

    public abstract List<VectorSearchResult> search(String collectionName,
                                                    List<Float> queryVector,
                                                    String vectorField,
                                                    int topK,
                                                    Map<String, Object> filters,
                                                    Map<String, Object> kwargs) throws Exception;

    public abstract void deleteDocsByIds(String collectionName,
                                         List<String> ids,
                                         Map<String, Object> kwargs) throws Exception;

    public abstract void deleteDocsByFilters(String collectionName,
                                             Map<String, Object> filters,
                                             Map<String, Object> kwargs) throws Exception;

    public abstract List<String> listCollectionNames() throws Exception;

    public abstract void updateSchema(String collectionName, List<?> operations) throws Exception;

    public abstract void updateCollectionMetadata(String collectionName,
                                                  Map<String, Object> metadata) throws Exception;

    public abstract Map<String, Object> getCollectionMetadata(String collectionName) throws Exception;

    public static BaseVectorStore fromAsync(com.openjiuwen.core.foundation.store.BaseVectorStore delegate) {
        return new AsyncDelegate(delegate);
    }

    private static <T> T await(CompletableFuture<T> future) throws Exception {
        try {
            return future.join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception checked) {
                throw checked;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw exception;
        }
    }

    private static Object toCoreSchema(Object schema) {
        if (schema instanceof CollectionSchema collectionSchema) {
            return collectionSchema.toCore();
        }
        return schema;
    }

    private static List<Double> toDoubleVector(List<Float> queryVector) {
        if (queryVector == null) {
            return null;
        }
        return queryVector.stream().map(value -> value == null ? null : value.doubleValue()).toList();
    }

    @SuppressWarnings("unchecked")
    private static List<BaseOperation> toOperations(List<?> operations) {
        return operations == null ? null : (List<BaseOperation>) operations;
    }

    private static final class AsyncDelegate extends BaseVectorStore {
        private final com.openjiuwen.core.foundation.store.BaseVectorStore delegate;

        private AsyncDelegate(com.openjiuwen.core.foundation.store.BaseVectorStore delegate) {
            this.delegate = delegate;
        }

        @Override
        public void createCollection(String collectionName, Object schema, Map<String, Object> kwargs) throws Exception {
            await(delegate.createCollection(collectionName, toCoreSchema(schema), kwargs));
        }

        @Override
        public void deleteCollection(String collectionName, Map<String, Object> kwargs) throws Exception {
            await(delegate.deleteCollection(collectionName, kwargs));
        }

        @Override
        public boolean collectionExists(String collectionName, Map<String, Object> kwargs) throws Exception {
            return await(delegate.collectionExists(collectionName, kwargs));
        }

        @Override
        public CollectionSchema getSchema(String collectionName, Map<String, Object> kwargs) throws Exception {
            return CollectionSchema.fromCore(await(delegate.getSchema(collectionName, kwargs)));
        }

        @Override
        public void addDocs(String collectionName, List<Map<String, Object>> docs, Map<String, Object> kwargs)
                throws Exception {
            await(delegate.addDocs(collectionName, docs, kwargs));
        }

        @Override
        public List<VectorSearchResult> search(String collectionName, List<Float> queryVector, String vectorField,
                                               int topK, Map<String, Object> filters, Map<String, Object> kwargs)
                throws Exception {
            return await(delegate.search(collectionName, toDoubleVector(queryVector), vectorField, topK, filters, kwargs))
                    .stream()
                    .map(VectorSearchResult::fromCore)
                    .toList();
        }

        @Override
        public void deleteDocsByIds(String collectionName, List<String> ids, Map<String, Object> kwargs)
                throws Exception {
            await(delegate.deleteDocsByIds(collectionName, ids, kwargs));
        }

        @Override
        public void deleteDocsByFilters(String collectionName, Map<String, Object> filters,
                                        Map<String, Object> kwargs) throws Exception {
            await(delegate.deleteDocsByFilters(collectionName, filters, kwargs));
        }

        @Override
        public List<String> listCollectionNames() throws Exception {
            return await(delegate.listCollectionNames());
        }

        @Override
        public void updateSchema(String collectionName, List<?> operations) throws Exception {
            await(delegate.updateSchema(collectionName, toOperations(operations)));
        }

        @Override
        public void updateCollectionMetadata(String collectionName, Map<String, Object> metadata) throws Exception {
            await(delegate.updateCollectionMetadata(collectionName, metadata));
        }

        @Override
        public Map<String, Object> getCollectionMetadata(String collectionName) throws Exception {
            return await(delegate.getCollectionMetadata(collectionName));
        }
    }
}
