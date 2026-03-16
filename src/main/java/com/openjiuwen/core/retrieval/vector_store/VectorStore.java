/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.retrieval.vector_store;

import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.indexing.indexer.IndexBackendConfig;

import java.util.List;
import java.util.Map;

/**
 * Unified vector store abstraction.
 */
public interface VectorStore extends IndexBackendConfig, AutoCloseable {

    String getCollectionName();

    void setCollectionName(String collectionName);

    VectorStore withCollection(String collectionName);

    /**
     * Check if vector field configuration is consistent with actual database.
     * Corresponds to Python {@code VectorStore.check_vector_field()}.
     */
    default void checkVectorField() {
        // Default no-op; concrete implementations should override if applicable
    }

    void add(List<Map<String, Object>> data, Integer batchSize, Map<String, Object> options);

    List<SearchResult> search(List<Float> queryVector, int topK, Map<String, Object> filters, Map<String, Object> options);

    List<SearchResult> sparseSearch(String queryText, int topK, Map<String, Object> filters, Map<String, Object> options);

    List<SearchResult> hybridSearch(String queryText,
                                    List<Float> queryVector,
                                    int topK,
                                    double alpha,
                                    Map<String, Object> filters,
                                    Map<String, Object> options);

    boolean delete(List<String> ids, Map<String, Object> filterExpr, Map<String, Object> options);

    boolean tableExists(String tableName);

    void deleteTable(String tableName);

    List<SearchResult> queryByFilters(Map<String, Object> filters, int limit);

    long count(String tableName);

    @Override
    default void close() {
    }
}
