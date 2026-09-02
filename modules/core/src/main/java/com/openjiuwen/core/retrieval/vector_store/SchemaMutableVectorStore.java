/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.vector_store;

import com.openjiuwen.spi.store.vector.CollectionSchema;

import java.util.List;
import java.util.Map;

/**
 * Optional extension for vector stores that support schema and collection metadata updates.
 * 
 * @since 0.1.7
 */
public interface SchemaMutableVectorStore extends VectorStore {
    /**
     * listCollectionNames.
     * 
     * @return the result
     * @since 0.1.7
     */
    List<String> listCollectionNames();

    /**
     * getCollectionMetadata.
     * 
     * @param collectionName collectionName
     * @return the result
     * @since 0.1.7
     */
    Map<String, Object> getCollectionMetadata(String collectionName);

    /**
     * updateCollectionMetadata.
     * 
     * @param collectionName collectionName
     * @param metadata metadata
     * @since 0.1.7
     */
    void updateCollectionMetadata(String collectionName, Map<String, Object> metadata);

    /**
     * updateSchema.
     * 
     * @param collectionName collectionName
     * @param operations operations
     * @since 0.1.7
     */
    void updateSchema(String collectionName, List<?> operations);

    /**
     * getSchema.
     * 
     * @param collectionName collectionName
     * @return the result
     * @since 0.1.7
     */
    CollectionSchema getSchema(String collectionName);
}
