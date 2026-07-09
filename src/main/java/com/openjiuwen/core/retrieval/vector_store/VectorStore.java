/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.vector_store;

import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.indexing.indexer.IndexBackendConfig;

import java.util.List;
import java.util.Map;

/**
 * Unified vector store abstraction.
 * 
 * @since 0.1.7
 */
public interface VectorStore extends IndexBackendConfig, AutoCloseable {
    /**
     * getCollectionName.
     * 
     * @return the result
     * @since 0.1.7
     */
    String getCollectionName();

    /**
     * setCollectionName.
     * 
     * @param collectionName collectionName
     * @since 0.1.7
     */
    void setCollectionName(String collectionName);

    /**
     * withCollection.
     * 
     * @param collectionName collectionName
     * @return the result
     * @since 0.1.7
     */
    VectorStore withCollection(String collectionName);

    /**
     * Check if vector field configuration is consistent with actual database.
     * Corresponds to Python {@code VectorStore.check_vector_field()}.
     * 
     * @since 0.1.7
     */
    default void checkVectorField() {
        // Default no-op; concrete implementations should override if applicable
    }

    /**
     * ensureCollection.
     * 
     * @param collectionName collectionName
     * @param indexType indexType
     * @param dimension dimension
     * @since 0.1.7
     */
    default void ensureCollection(String collectionName, String indexType, Integer dimension) {
        ensureCollection(collectionName, indexType, dimension, Map.of());
    }

    /**
     * ensureCollection.
     * 
     * @param collectionName collectionName
     * @param indexType indexType
     * @param dimension dimension
     * @param options options
     * @since 0.1.7
     */
    default void ensureCollection(String collectionName, String indexType, Integer dimension,
            Map<String, Object> options) {
        // Default no-op; concrete implementations should override if applicable
    }

    /**
     * add.
     * 
     * @param data data
     * @param batchSize batchSize
     * @param options options
     * @since 0.1.7
     */
    void add(List<Map<String, Object>> data, Integer batchSize, Map<String, Object> options);

    /**
     * search.
     * 
     * @param queryVector queryVector
     * @param topK topK
     * @param filters filters
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    List<SearchResult> search(List<Float> queryVector, int topK, Map<String, Object> filters,
            Map<String, Object> options);

    /**
     * sparseSearch.
     * 
     * @param queryText queryText
     * @param topK topK
     * @param filters filters
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    List<SearchResult> sparseSearch(String queryText, int topK, Map<String, Object> filters,
            Map<String, Object> options);

    /**
     * hybridSearch.
     * 
     * @param queryText queryText
     * @param queryVector queryVector
     * @param topK topK
     * @param alpha alpha
     * @param filters filters
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    List<SearchResult> hybridSearch(String queryText, List<Float> queryVector, int topK, double alpha,
            Map<String, Object> filters, Map<String, Object> options);

    /**
     * delete.
     * 
     * @param ids ids
     * @param filterExpr filterExpr
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    boolean delete(List<String> ids, Map<String, Object> filterExpr, Map<String, Object> options);

    /**
     * tableExists.
     * 
     * @param tableName tableName
     * @return the result
     * @since 0.1.7
     */
    boolean tableExists(String tableName);

    /**
     * deleteTable.
     * 
     * @param tableName tableName
     * @since 0.1.7
     */
    void deleteTable(String tableName);

    /**
     * queryByFilters.
     * 
     * @param filters filters
     * @param limit limit
     * @return the result
     * @since 0.1.7
     */
    List<SearchResult> queryByFilters(Map<String, Object> filters, int limit);

    /**
     * count.
     * 
     * @param tableName tableName
     * @return the result
     * @since 0.1.7
     */
    long count(String tableName);

    @Override
    /**
     * close.
     * 
     * @since 0.1.7
     */
    default void close() {
    }
}
