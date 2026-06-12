/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import com.openjiuwen.core.foundation.store.Embedding;
import com.openjiuwen.core.foundation.store.base_reranker.Reranker;
import com.openjiuwen.core.foundation.store.query.QueryExpr;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's graph store protocol surface in
 * {@code openjiuwen/core/foundation/store/graph/base_graph_store.py}.
 */
class GraphStoreProtocolTest {

    @Test
    void exposesProtocolPropertiesAndDefaults() {
        FakeGraphStore store = new FakeGraphStore();

        assertEquals("memory", store.getConfig().getBackend());
        assertTrue(store.getSemophore().isPresent());
        assertTrue(store.getEmbedder().isEmpty());
        assertFalse(store.isReturnSimilarityScore());

        store.refresh().join();
        store.addData("entities", List.of(Map.of("id", "e1"))).join();
        store.addEntity(List.of("entity")).join();
        store.addRelation(List.of("relation")).join();
        store.addEpisode(List.of("episode")).join();

        assertEquals(List.of("refresh:true", "data:entities:true:false", "entity:true:false:false",
                "relation:true:false:false", "episode:true:false:false"), store.calls);
    }

    @Test
    void queryDeleteSearchAndFactorySurfaceMatchPythonProtocol() {
        FakeGraphStore store = new FakeGraphStore();
        assertTrue(store.query("entities").join().isEmpty());
        assertEquals(Map.of("deleted", 0), store.delete("entities").join());
        assertEquals(Map.of("entities", List.of(Map.of("id", "e1"))),
                store.search("alice", 1, "entities", new EmptyRankConfig(), null,
                        0, 0, null, List.of("id"), null, Map.of("language", "en")).join());

        GraphStore.Factory factory = (config, kwargs) -> store;
        assertSame(store, factory.fromConfig(store.getConfig(), Map.of("source", "test")));
    }

    private static final class FakeGraphStore implements GraphStore {

        private final List<String> calls = new ArrayList<>();
        private final GraphConfig config = GraphConfig.builder().uri("memory.db").backend("memory").build();
        private final Semaphore semophore = new Semaphore(1);

        @Override
        public GraphConfig getConfig() {
            return config;
        }

        @Override
        public Optional<Semaphore> getSemophore() {
            return Optional.of(semophore);
        }

        @Override
        public Optional<Embedding> getEmbedder() {
            return Optional.empty();
        }

        @Override
        public boolean isReturnSimilarityScore() {
            return false;
        }

        @Override
        public void rebuild() {
            calls.add("rebuild");
        }

        @Override
        public CompletableFuture<Void> refresh(boolean skipCompact, Map<String, Object> kwargs) {
            calls.add("refresh:" + skipCompact);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> addData(String collection,
                                               Iterable<Map<String, Object>> data,
                                               boolean flush,
                                               boolean upsert,
                                               Map<String, Object> kwargs) {
            calls.add("data:" + collection + ":" + flush + ":" + upsert);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> addEntity(Iterable<?> entities, boolean flush, boolean upsert, boolean noEmbed) {
            calls.add("entity:" + flush + ":" + upsert + ":" + noEmbed);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> addRelation(Iterable<?> relations, boolean flush, boolean upsert, boolean noEmbed) {
            calls.add("relation:" + flush + ":" + upsert + ":" + noEmbed);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> addEpisode(Iterable<?> episodes, boolean flush, boolean upsert, boolean noEmbed) {
            calls.add("episode:" + flush + ":" + upsert + ":" + noEmbed);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public boolean isEmpty(String collection) {
            return true;
        }

        @Override
        public CompletableFuture<List<Map<String, Object>>> query(String collection,
                                                                  List<?> ids,
                                                                  QueryExpr expr,
                                                                  boolean silenceErrors,
                                                                  Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<Map<String, Object>> delete(String collection,
                                                             List<?> ids,
                                                             QueryExpr expr,
                                                             Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(Map.of("deleted", 0));
        }

        @Override
        public CompletableFuture<Map<String, List<Map<String, Object>>>> search(String query,
                                                                                int k,
                                                                                String collection,
                                                                                BaseRankConfig rankerConfig,
                                                                                Reranker reranker,
                                                                                int bfsDepth,
                                                                                int bfsK,
                                                                                QueryExpr filterExpr,
                                                                                List<String> outputFields,
                                                                                List<Double> queryEmbedding,
                                                                                Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(Map.of(collection, List.of(Map.of("id", "e1"))));
        }

        @Override
        public void attachEmbedder(Embedding embedder) {
            calls.add("embedder");
        }

        @Override
        public void close() {
            calls.add("close");
        }
    }

    private static final class EmptyRankConfig extends BaseRankConfig {

        @Override
        public RankerArgs getArgs() {
            return new RankerArgs(List.of(), Map.of());
        }
    }
}
