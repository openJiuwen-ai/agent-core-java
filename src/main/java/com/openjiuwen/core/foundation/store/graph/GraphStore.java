/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import com.openjiuwen.core.foundation.store.Embedding;
import com.openjiuwen.core.foundation.store.base_reranker.Reranker;
import com.openjiuwen.core.foundation.store.query.QueryExpr;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

/**
 * Protocol defining the graph vector store backend interface.
 * <p>
 * Mirrors Python's {@code GraphStore} in
 * {@code openjiuwen/core/foundation/store/graph/base_graph_store.py}.
 * </p>
 */
public interface GraphStore {

    GraphConfig getConfig();

    Optional<Semaphore> getSemophore();

    Optional<Embedding> getEmbedder();

    boolean isReturnSimilarityScore();

    void rebuild();

    default CompletableFuture<Void> refresh() {
        return refresh(true, Map.of());
    }

    default CompletableFuture<Void> refresh(boolean skipCompact) {
        return refresh(skipCompact, Map.of());
    }

    CompletableFuture<Void> refresh(boolean skipCompact, Map<String, Object> kwargs);

    default CompletableFuture<Void> addData(String collection, Iterable<Map<String, Object>> data) {
        return addData(collection, data, true, false, Map.of());
    }

    default CompletableFuture<Void> addData(String collection,
                                            Iterable<Map<String, Object>> data,
                                            boolean flush,
                                            boolean upsert) {
        return addData(collection, data, flush, upsert, Map.of());
    }

    CompletableFuture<Void> addData(String collection,
                                    Iterable<Map<String, Object>> data,
                                    boolean flush,
                                    boolean upsert,
                                    Map<String, Object> kwargs);

    default CompletableFuture<Void> addEntity(Iterable<?> entities) {
        return addEntity(entities, true, false, false);
    }

    CompletableFuture<Void> addEntity(Iterable<?> entities, boolean flush, boolean upsert, boolean noEmbed);

    default CompletableFuture<Void> addRelation(Iterable<?> relations) {
        return addRelation(relations, true, false, false);
    }

    CompletableFuture<Void> addRelation(Iterable<?> relations, boolean flush, boolean upsert, boolean noEmbed);

    default CompletableFuture<Void> addEpisode(Iterable<?> episodes) {
        return addEpisode(episodes, true, false, false);
    }

    CompletableFuture<Void> addEpisode(Iterable<?> episodes, boolean flush, boolean upsert, boolean noEmbed);

    boolean isEmpty(String collection);

    default CompletableFuture<List<Map<String, Object>>> query(String collection) {
        return query(collection, null, null, false, Map.of());
    }

    default CompletableFuture<List<Map<String, Object>>> query(String collection,
                                                               List<?> ids,
                                                               QueryExpr expr,
                                                               boolean silenceErrors) {
        return query(collection, ids, expr, silenceErrors, Map.of());
    }

    CompletableFuture<List<Map<String, Object>>> query(String collection,
                                                       List<?> ids,
                                                       QueryExpr expr,
                                                       boolean silenceErrors,
                                                       Map<String, Object> kwargs);

    default CompletableFuture<Map<String, Object>> delete(String collection) {
        return delete(collection, null, null, Map.of());
    }

    default CompletableFuture<Map<String, Object>> delete(String collection, List<?> ids, QueryExpr expr) {
        return delete(collection, ids, expr, Map.of());
    }

    CompletableFuture<Map<String, Object>> delete(String collection,
                                                  List<?> ids,
                                                  QueryExpr expr,
                                                  Map<String, Object> kwargs);

    CompletableFuture<Map<String, List<Map<String, Object>>>> search(String query,
                                                                     int k,
                                                                     String collection,
                                                                     BaseRankConfig rankerConfig,
                                                                     Reranker reranker,
                                                                     int bfsDepth,
                                                                     int bfsK,
                                                                     QueryExpr filterExpr,
                                                                     List<String> outputFields,
                                                                     List<Double> queryEmbedding,
                                                                     Map<String, Object> kwargs);

    void attachEmbedder(Embedding embedder);

    void close();

    /**
     * Java representation of Python's {@code @classmethod from_config}.
     */
    @FunctionalInterface
    interface Factory {

        GraphStore fromConfig(GraphConfig config, Map<String, Object> kwargs);
    }
}
