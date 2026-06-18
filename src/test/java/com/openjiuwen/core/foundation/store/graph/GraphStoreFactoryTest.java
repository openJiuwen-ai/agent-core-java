/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.store.Embedding;
import com.openjiuwen.core.foundation.store.base_reranker.Reranker;
import com.openjiuwen.core.foundation.store.query.QueryExpr;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code GraphStoreFactory} in
 * {@code openjiuwen/core/foundation/store/graph/base.py}.
 */
class GraphStoreFactoryTest {

    @AfterEach
    void resetRegistry() {
        GraphStoreFactory.clearRegistryForTest();
        FakeGraphStore.lastConfig = null;
        FakeGraphStore.lastKwargs = null;
    }

    @Test
    void constructorIsNotInstantiable() throws Exception {
        Constructor<GraphStoreFactory> constructor = GraphStoreFactory.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        InvocationTargetException thrown = assertThrows(InvocationTargetException.class, constructor::newInstance);
        BaseError error = (BaseError) thrown.getCause();

        assertEquals(StatusCode.STORE_GRAPH_FACTORY_NOT_INSTANTIABLE, error.getStatus());
        assertEquals("GraphStoreFactory", error.getParams().get("class_name"));
    }

    @Test
    void registerBackendRejectsEmptyNameAndDuplicateUnlessForced() {
        BaseError emptyName = assertThrows(BaseError.class,
                () -> GraphStoreFactory.registerBackend("", FakeGraphStore.class));
        assertEquals(StatusCode.STORE_GRAPH_BACKEND_NAME_INVALID, emptyName.getStatus());

        GraphStoreFactory.registerBackend("fake", FakeGraphStore.class);
        BaseError duplicate = assertThrows(BaseError.class,
                () -> GraphStoreFactory.registerBackend("fake", AlternateGraphStore.class));
        assertEquals(StatusCode.STORE_GRAPH_BACKEND_ALREADY_EXISTS, duplicate.getStatus());
        assertEquals("fake", duplicate.getParams().get("name"));
        assertEquals(FakeGraphStore.class.getSimpleName(), duplicate.getParams().get("existing"));

        GraphStoreFactory.registerBackend("fake", AlternateGraphStore.class, true);
        assertSame(AlternateGraphStore.class, GraphStoreFactory.getBackendClass("fake"));
    }

    @Test
    void registerBackendValidatesGraphStoreProtocolUnlessForced() {
        BaseError protocol = assertThrows(BaseError.class,
                () -> GraphStoreFactory.registerBackend("bad", String.class));
        assertEquals(StatusCode.STORE_GRAPH_PROTOCOL_NOT_IMPLEMENTED, protocol.getStatus());
        assertEquals("bad did not implement GraphStore Protocol!", protocol.getParams().get("error_msg"));

        GraphStoreFactory.registerBackend("bad", String.class, true);
        assertSame(String.class, GraphStoreFactory.getBackendClass("bad"));
        assertTrue(GraphStoreFactory.isRegistered("bad"));
    }

    @Test
    void fromConfigUsesExplicitBackendOrConfigBackendAndPassesKwargs() {
        GraphStoreFactory.registerBackend("config-backend", FakeGraphStore.class);
        GraphStoreFactory.registerBackend("override-backend", AlternateGraphStore.class);
        GraphConfig config = GraphConfig.builder()
                .uri("graph-factory.db")
                .backend("config-backend")
                .build();

        GraphStore fromConfig = GraphStoreFactory.fromConfig(config);
        assertTrue(fromConfig instanceof FakeGraphStore);
        assertSame(config, FakeGraphStore.lastConfig);
        assertEquals(Map.of(), FakeGraphStore.lastKwargs);

        GraphStore overridden = GraphStoreFactory.fromConfig(config, "override-backend", Map.of("source", "test"));
        assertTrue(overridden instanceof AlternateGraphStore);
        assertSame(config, AlternateGraphStore.lastConfig);
        assertEquals(Map.of("source", "test"), AlternateGraphStore.lastKwargs);
    }

    @Test
    void fromConfigRaisesBackendNotFoundForUnknownName() {
        GraphConfig config = GraphConfig.builder()
                .uri("graph-factory.db")
                .backend("missing")
                .build();

        BaseError error = assertThrows(BaseError.class, () -> GraphStoreFactory.fromConfig(config));

        assertEquals(StatusCode.STORE_GRAPH_BACKEND_NOT_FOUND, error.getStatus());
        assertEquals("missing", error.getParams().get("name"));
        assertFalse(GraphStoreFactory.isRegistered("missing"));
    }

    private static class FakeGraphStore implements GraphStore {

        static GraphConfig lastConfig;
        static Map<String, Object> lastKwargs;

        private final GraphConfig config;

        FakeGraphStore(GraphConfig config) {
            this.config = config;
        }

        public static FakeGraphStore fromConfig(GraphConfig config, Map<String, Object> kwargs) {
            lastConfig = config;
            lastKwargs = kwargs;
            return new FakeGraphStore(config);
        }

        @Override
        public GraphConfig getConfig() {
            return config;
        }

        @Override
        public Optional<Semaphore> getSemophore() {
            return Optional.empty();
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
        }

        @Override
        public CompletableFuture<Void> refresh(boolean skipCompact, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> addData(String collection,
                                               Iterable<Map<String, Object>> data,
                                               boolean flush,
                                               boolean upsert,
                                               Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> addEntity(Iterable<?> entities, boolean flush, boolean upsert, boolean noEmbed) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> addRelation(Iterable<?> relations,
                                                   boolean flush,
                                                   boolean upsert,
                                                   boolean noEmbed) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> addEpisode(Iterable<?> episodes,
                                                  boolean flush,
                                                  boolean upsert,
                                                  boolean noEmbed) {
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
            return CompletableFuture.completedFuture(Map.of());
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
            return CompletableFuture.completedFuture(Map.of());
        }

        @Override
        public void attachEmbedder(Embedding embedder) {
        }

        @Override
        public void close() {
        }
    }

    private static final class AlternateGraphStore extends FakeGraphStore {

        static GraphConfig lastConfig;
        static Map<String, Object> lastKwargs;

        AlternateGraphStore(GraphConfig config) {
            super(config);
        }

        public static AlternateGraphStore fromConfig(GraphConfig config, Map<String, Object> kwargs) {
            lastConfig = config;
            lastKwargs = kwargs;
            return new AlternateGraphStore(config);
        }
    }
}
