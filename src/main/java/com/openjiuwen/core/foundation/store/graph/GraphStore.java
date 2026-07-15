/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import com.openjiuwen.core.foundation.store.base_embedding.Embedding;
import com.openjiuwen.spi.store.query.QueryExpr;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * Interface defining the contract for graph vector store backends.
 * <p>
 * Mirrors Python's {@code GraphStore} Protocol. In Java this is an interface
 * rather than a Protocol since Java has explicit interface support.
 * <p>
 * Implementations should provide:
 * <ul>
 * <li>{@code getConfig()} - graph configuration</li>
 * <li>{@code getEmbedExecutor()} - executor for embedding tasks</li>
 * <li>{@code getEmbedder()} - optional embedding service</li>
 * </ul>
 * 
 * @since 0.1.7
 */
public interface GraphStore {
    /**
     * getConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    GraphConfig getConfig();

    /**
     * Get the executor for embedding tasks.
     * 
     * @return the result
     * @since 0.1.7
     */
    ExecutorService getEmbedExecutor();

    /**
     * Get the optional embedding service.
     * 
     * @return the result
     * @since 0.1.7
     */
    Embedding getEmbedder();

    /**
     * Create a backend instance from configuration.
     * 
     * @param config graph configuration
     * @return configured backend instance
     * @since 0.1.7
     */
    static GraphStore fromConfig(GraphConfig config) {
        throw new UnsupportedOperationException("Subclasses must implement fromConfig");
    }

    /**
     * Refresh / flush inserted data to database.
     * 
     * @since 0.1.7
     */
    void refresh();

    /**
     * Add arbitrary data into database.
     * 
     * @param collection collection name for data insertion
     * @param data data to insert
     * @param flush whether to flush changes immediately
     * @param upsert whether to upsert instead of insert
     * @throws Exception Exception
     * @since 0.1.7
     */
    void addData(String collection, Iterable<Map<String, Object>> data, boolean flush, boolean upsert) throws Exception;

    /**
     * Add entity objects to the graph store.
     * 
     * @param entities iterable of entity objects
     * @param flush whether to flush changes immediately
     * @param upsert whether to upsert
     * @param noEmbed whether to skip embedding
     * @throws Exception Exception
     * @since 0.1.7
     */
    void addEntity(Iterable<?> entities, boolean flush, boolean upsert, boolean noEmbed) throws Exception;

    /**
     * Add relation objects to the graph store.
     * 
     * @param relations iterable of relation objects
     * @param flush whether to flush changes immediately
     * @param upsert whether to upsert
     * @param noEmbed whether to skip embedding
     * @throws Exception Exception
     * @since 0.1.7
     */
    void addRelation(Iterable<?> relations, boolean flush, boolean upsert, boolean noEmbed) throws Exception;

    /**
     * Add episode objects to the graph store.
     * 
     * @param episodes iterable of episode objects
     * @param flush whether to flush changes immediately
     * @param upsert whether to upsert
     * @param noEmbed whether to skip embedding
     * @throws Exception Exception
     * @since 0.1.7
     */
    void addEpisode(Iterable<?> episodes, boolean flush, boolean upsert, boolean noEmbed) throws Exception;

    /**
     * Check if a collection is empty.
     * 
     * @param collection name of collection
     * @return whether the collection is empty
     * @since 0.1.7
     */
    boolean isEmpty(String collection);

    /**
     * Query graph objects from a collection.
     * 
     * @param collection collection name to query
     * @param ids optional list of IDs to query
     * @param expr optional filter expression
     * @param silenceErrors suppress exceptions and return empty list
     * @return list of query results
     * @throws Exception Exception
     * @since 0.1.7
     */
    List<Map<String, Object>> query(String collection, List<Object> ids, QueryExpr expr, boolean silenceErrors)
            throws Exception;

    /**
     * Delete graph objects from a collection.
     * 
     * @param collection collection name to delete from
     * @param ids optional list of IDs to delete
     * @param expr optional filter expression
     * @return result of the delete operation
     * @throws Exception Exception
     * @since 0.1.7
     */
    Map<String, Object> delete(String collection, List<Object> ids, QueryExpr expr) throws Exception;

    /**
     * Search for graph objects using hybrid search.
     * 
     * @param queryText search query string
     * @param k number of results to return
     * @param collection collection to search
     * @param rankerConfig configuration for search ranking
     * @param bfsDepth breadth-first search depth
     * @param bfsK max nodes to expand in BFS
     * @param filterExpr optional filter expression
     * @param outputFields fields to include in results
     * @param queryEmbedding pre-computed query embedding (optional)
     * @param kwargs additional arguments (language, reranker, min_score, etc.)
     * @return map of collection names to results
     * @throws Exception Exception
     * @since 0.1.7
     */
    Map<String, List<Map<String, Object>>> search(String queryText, int k, String collection, Object rankerConfig,
            int bfsDepth, int bfsK, QueryExpr filterExpr, List<String> outputFields, List<Float> queryEmbedding,
            Map<String, Object> kwargs) throws Exception;

    /**
     * Attach an embedding service to the backend.
     * 
     * @param embedder embedding service instance
     * @since 0.1.7
     */
    void attachEmbedder(Embedding embedder);

    /**
     * Close the backend and clean up resources.
     * 
     * @since 0.1.7
     */
    void close();
}
