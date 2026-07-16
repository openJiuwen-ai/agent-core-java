/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store.vector;

import java.util.List;
import java.util.Map;

/**
 * Abstract base class defining a unified interface for vector storage.
 * <p>
 * Mirrors Python's {@code BaseVectorStore} ABC.
 * Synchronous methods (run on virtual threads in practice).
 * 
 * @since 0.1.7
 */
public abstract class BaseVectorStore {
    /**
     * createCollection.
     * 
     * @param collectionName collectionName
     * @param schema schema
     * @param kwargs kwargs
     * @throws Exception Exception
     * @since 0.1.7
     */
    public abstract void createCollection(String collectionName, Object schema, Map<String, Object> kwargs)
            throws Exception;

    /**
     * deleteCollection.
     * 
     * @param collectionName collectionName
     * @param kwargs kwargs
     * @throws Exception Exception
     * @since 0.1.7
     */
    public abstract void deleteCollection(String collectionName, Map<String, Object> kwargs) throws Exception;

    /**
     * collectionExists.
     * 
     * @param collectionName collectionName
     * @param kwargs kwargs
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public abstract boolean collectionExists(String collectionName, Map<String, Object> kwargs) throws Exception;

    /**
     * getSchema.
     * 
     * @param collectionName collectionName
     * @param kwargs kwargs
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public abstract CollectionSchema getSchema(String collectionName, Map<String, Object> kwargs) throws Exception;

    /**
     * addDocs.
     * 
     * @param collectionName collectionName
     * @param docs docs
     * @param kwargs kwargs
     * @throws Exception Exception
     * @since 0.1.7
     */
    public abstract void addDocs(String collectionName, List<Map<String, Object>> docs, Map<String, Object> kwargs)
            throws Exception;

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
    public abstract List<VectorSearchResult> search(String collectionName, List<Float> queryVector, String vectorField,
            int topK, Map<String, Object> filters, Map<String, Object> kwargs) throws Exception;

    /**
     * deleteDocsByIds.
     * 
     * @param collectionName collectionName
     * @param ids ids
     * @param kwargs kwargs
     * @throws Exception Exception
     * @since 0.1.7
     */
    public abstract void deleteDocsByIds(String collectionName, List<String> ids, Map<String, Object> kwargs)
            throws Exception;

    /**
     * deleteDocsByFilters.
     * 
     * @param collectionName collectionName
     * @param filters filters
     * @param kwargs kwargs
     * @throws Exception Exception
     * @since 0.1.7
     */
    public abstract void deleteDocsByFilters(String collectionName, Map<String, Object> filters,
            Map<String, Object> kwargs) throws Exception;

    /**
     * listCollectionNames.
     * 
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public abstract List<String> listCollectionNames() throws Exception;

    /**
     * updateSchema.
     * 
     * @param collectionName collectionName
     * @param operations operations
     * @throws Exception Exception
     * @since 0.1.7
     */
    public abstract void updateSchema(String collectionName, List<?> operations) throws Exception;

    /**
     * updateCollectionMetadata.
     * 
     * @param collectionName collectionName
     * @param metadata metadata
     * @throws Exception Exception
     * @since 0.1.7
     */
    public abstract void updateCollectionMetadata(String collectionName, Map<String, Object> metadata) throws Exception;

    /**
     * getCollectionMetadata.
     * 
     * @param collectionName collectionName
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public abstract Map<String, Object> getCollectionMetadata(String collectionName) throws Exception;
}
