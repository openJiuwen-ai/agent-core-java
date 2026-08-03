/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.vector_store;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mirrors Python's tests in
 * {@code tests/unit_tests/core/retrieval/vector_store/test_base.py}.
 */
class VectorStoreTest {

    @Test
    void addAcceptsSingleRecordViaDefaultOverload() {
        ConcreteVectorStore store = new ConcreteVectorStore();

        assertDoesNotThrow(() -> store.add(Map.of("id", "1", "text", "test", "embedding", List.of(0.1d)), null, Map.of()).join());
        assertEquals(1, store.addCalls);
    }

    @Test
    void searchMethodsReturnEmptyResults() {
        ConcreteVectorStore store = new ConcreteVectorStore();

        assertEquals(List.of(), store.search(vector(), 5, VectorStore.VectorStoreFilter.none(), Map.of()).join());
        assertEquals(List.of(), store.sparseSearch("test query", 5, VectorStore.VectorStoreFilter.none(), Map.of()).join());
        assertEquals(List.of(), store.hybridSearch("test query", vector(), 5, 0.5d, VectorStore.VectorStoreFilter.none(), Map.of()).join());
    }

    @Test
    void deleteReturnsTrue() {
        ConcreteVectorStore store = new ConcreteVectorStore();

        assertEquals(true, store.delete(List.of("1", "2"), VectorStore.DeleteFilter.none(), Map.of()).join());
    }

    @Test
    void checkConfigsMatchingAllowsExactAndCloseNumericValues() {
        Map<String, Object> configured = Map.of(
                "metric", "COSINE",
                "dimension", 1024,
                "efSearchFactor", 999
        );
        Map<String, Object> actual = Map.of(
                "metric", "cosine",
                "dimension", "1024.001",
                "efSearchFactor", 1,
                "extra", "ignored"
        );

        assertDoesNotThrow(() -> VectorStore.checkConfigsMatching(configured, actual));
    }

    @Test
    void checkConfigsMatchingRaisesOnMismatch() {
        BaseError error = assertThrows(
                BaseError.class,
                () -> VectorStore.checkConfigsMatching(Map.of("metric", "cosine"), Map.of("metric", "l2"))
        );

        assertEquals(StatusCode.RETRIEVAL_KB_DATABASE_CONFIG_INVALID, error.getStatus());
    }

    private static List<Double> vector() {
        return List.of(0.1d, 0.1d, 0.1d);
    }

    private static final class ConcreteVectorStore implements VectorStore {

        private int addCalls;

        @Override
        public void checkVectorField() {
        }

        @Override
        public CompletableFuture<Void> add(List<Map<String, Object>> data,
                                           Integer batchSize,
                                           Map<String, Object> kwargs) {
            addCalls++;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<List<RetrievalResult>> search(List<Double> queryVector,
                                                               int topK,
                                                               VectorStoreFilter filters,
                                                               Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<List<RetrievalResult>> sparseSearch(String queryText,
                                                                     int topK,
                                                                     VectorStoreFilter filters,
                                                                     Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<List<RetrievalResult>> hybridSearch(String queryText,
                                                                     List<Double> queryVector,
                                                                     int topK,
                                                                     double alpha,
                                                                     VectorStoreFilter filters,
                                                                     Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<Boolean> delete(List<String> ids,
                                                 DeleteFilter filterExpr,
                                                 Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<Boolean> tableExists(String tableName) {
            return CompletableFuture.completedFuture(false);
        }

        @Override
        public CompletableFuture<Void> deleteTable(String tableName) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
