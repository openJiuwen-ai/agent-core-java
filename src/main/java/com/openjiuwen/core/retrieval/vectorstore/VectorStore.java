/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.retrieval.vectorstore;

import com.openjiuwen.core.retrieval.common.SearchResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Vector store abstract interface.
 * <p>
 * Provides a unified interface for vector stores.
 * Placeholder implementation for memory module dependency.
 * Will be completed when retrieval module is converted.
 */
public interface VectorStore {

    /**
     * Create vector database client and ensure database exists.
     *
     * @param databaseName database name
     * @param pathOrUri path or URI
     * @param token optional token
     * @return the client object
     */
    static Object createClient(String databaseName, String pathOrUri, String token) {
        throw new UnsupportedOperationException("Placeholder: createClient not implemented");
    }

    /**
     * Check if vector field configuration is consistent with actual database.
     */
    void checkVectorField();

    /**
     * Add vectors.
     *
     * @param data single record or list of records
     * @param batchSize batch size for insertion
     * @return CompletableFuture that completes when insertion is done
     */
    CompletableFuture<Void> add(List<Map<String, Object>> data, Integer batchSize);

    /**
     * Add vectors with default batch size.
     *
     * @param data list of records
     * @return CompletableFuture that completes when insertion is done
     */
    default CompletableFuture<Void> add(List<Map<String, Object>> data) {
        return add(data, 128);
    }

    /**
     * Add vectors to a specific table.
     * Used by SemanticStore which manages multiple tables.
     *
     * @param data list of records
     * @param tableName target table name
     * @return CompletableFuture that completes when insertion is done
     */
    default CompletableFuture<Void> add(List<Map<String, Object>> data, String tableName) {
        return add(data, 128);
    }

    /**
     * Vector search.
     *
     * @param queryVector query vector
     * @param topK number of results to return
     * @param filters metadata filter conditions
     * @return CompletableFuture containing list of search results
     */
    CompletableFuture<List<SearchResult>> search(List<Double> queryVector, int topK, Map<String, Object> filters);

    /**
     * Vector search with default parameters.
     *
     * @param queryVector query vector
     * @return CompletableFuture containing list of search results
     */
    default CompletableFuture<List<SearchResult>> search(List<Double> queryVector) {
        return search(queryVector, 5, (Map<String, Object>) null);
    }

    /**
     * Vector search in a specific table.
     * Used by SemanticStore which manages multiple tables.
     *
     * @param queryVector query vector
     * @param topK number of results to return
     * @param tableName target table name
     * @return CompletableFuture containing list of search results
     */
    default CompletableFuture<List<SearchResult>> search(List<Double> queryVector, int topK, String tableName) {
        return search(queryVector, topK, (Map<String, Object>) null);
    }

    /**
     * Sparse search (BM25).
     *
     * @param queryText query text
     * @param topK number of results to return
     * @param filters metadata filter conditions
     * @return CompletableFuture containing list of search results
     */
    CompletableFuture<List<SearchResult>> sparseSearch(String queryText, int topK, Map<String, Object> filters);

    /**
     * Hybrid search (sparse retrieval + vector retrieval).
     *
     * @param queryText query text
     * @param queryVector optional query vector
     * @param topK number of results to return
     * @param alpha hybrid weight (0=pure sparse, 1=pure vector, 0.5=balanced)
     * @param filters metadata filter conditions
     * @return CompletableFuture containing list of search results
     */
    CompletableFuture<List<SearchResult>> hybridSearch(
            String queryText,
            List<Double> queryVector,
            int topK,
            double alpha,
            Map<String, Object> filters);

    /**
     * Delete vectors.
     *
     * @param ids list of IDs to delete
     * @param filterExpr filter expression
     * @return CompletableFuture containing true if successful
     */
    CompletableFuture<Boolean> delete(List<String> ids, String filterExpr);

    /**
     * Delete vectors by IDs.
     *
     * @param ids list of IDs to delete
     * @return CompletableFuture containing true if successful
     */
    default CompletableFuture<Boolean> delete(List<String> ids) {
        return delete(ids, (String) null);
    }

    /**
     * Delete vectors from a specific table.
     * Used by SemanticStore which manages multiple tables.
     *
     * @param ids list of IDs to delete
     * @param tableName target table name
     * @return CompletableFuture containing true if successful
     */
    default CompletableFuture<Boolean> deleteFromTable(List<String> ids, String tableName) {
        return delete(ids, (String) null);
    }

    /**
     * Check if a collection exists in current database.
     *
     * @param tableName table/collection name
     * @return CompletableFuture containing true if exists
     */
    CompletableFuture<Boolean> tableExists(String tableName);

    /**
     * Delete a collection from current database.
     *
     * @param tableName table/collection name
     * @return CompletableFuture that completes when deletion is done
     */
    CompletableFuture<Boolean> deleteTable(String tableName);
}

