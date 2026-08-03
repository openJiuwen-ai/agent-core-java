/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.retriever;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code SparseRetriever} behavior in
 * {@code openjiuwen/core/retrieval/retriever/sparse_retriever.py}.
 *
 * <p>Mirrors Python's {@code TestSparseRetriever} in
 * {@code tests/unit_tests/core/retrieval/retriever/test_sparse_retriever.py}.</p>
 */
class SparseRetrieverTest {

    @Test
    void retrieveUsesSparseSearchAndConvertsResults() {
        FakeVectorStore vectorStore = new FakeVectorStore();
        vectorStore.sparseResults = List.of(result("chunk-1", "text", 0.7d, Map.of("doc_id", "doc-1")));
        SparseRetriever retriever = new SparseRetriever(vectorStore);

        List<RetrievalResult> results = retriever.retrieve("query", 4, 0.99d, "sparse", Map.of("ignored", "value"));

        assertThat(vectorStore.queries).containsExactly("query");
        assertThat(vectorStore.topKs).containsExactly(4);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getDocId()).isEqualTo("doc-1");
        assertThat(results.get(0).getChunkId()).isEqualTo("chunk-1");
    }

    @Test
    void nullModeUsesPythonSparseDefault() {
        FakeVectorStore vectorStore = new FakeVectorStore();
        vectorStore.sparseResults = List.of(result("chunk-2", "default", 0.8d, Map.of()));
        SparseRetriever retriever = new SparseRetriever(vectorStore);

        List<RetrievalResult> results = retriever.retrieve("query", 5, null, null, Map.of());

        assertThat(results).extracting(RetrievalResult::getText).containsExactly("default");
    }

    @Test
    void nonSparseModeIsRejected() {
        SparseRetriever retriever = new SparseRetriever(new FakeVectorStore());

        assertThatThrownBy(() -> retriever.retrieve("query", 5, null, "vector", Map.of()))
                .isInstanceOf(BaseError.class)
                .satisfies(error -> assertThat(((BaseError) error).getStatus())
                        .isEqualTo(StatusCode.RETRIEVAL_RETRIEVER_MODE_NOT_SUPPORT));
    }

    @Test
    void batchRetrievePreservesInputOrder() {
        FakeVectorStore vectorStore = new FakeVectorStore();
        SparseRetriever retriever = new SparseRetriever(vectorStore);

        List<List<RetrievalResult>> results = retriever.batchRetrieve(List.of("a", "b"), 3, "sparse", Map.of());

        assertThat(vectorStore.queries).containsExactly("a", "b");
        assertThat(results).hasSize(2);
        assertThat(results.get(0).get(0).getText()).isEqualTo("a-result");
        assertThat(results.get(1).get(0).getText()).isEqualTo("b-result");
    }

    @Test
    void retrieveSearchResultsReturnsRawSearchShape() {
        FakeVectorStore vectorStore = new FakeVectorStore();
        vectorStore.sparseResults = List.of(result("chunk-9", "raw", 0.6d, Map.of("doc_id", "doc-9")));
        SparseRetriever retriever = new SparseRetriever(vectorStore);

        List<SearchResult> results = retriever.retrieveSearchResults("query", 2, "sparse", Map.of());

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo("chunk-9");
        assertThat(results.get(0).getMetadata()).containsEntry("doc_id", "doc-9");
    }

    private static RetrievalResult result(String chunkId, String text, double score, Map<String, Object> metadata) {
        return new RetrievalResult(text, score, metadata, null, chunkId);
    }

    private static final class FakeVectorStore implements VectorStore {
        private List<RetrievalResult> sparseResults = List.of();
        private final List<String> queries = new ArrayList<>();
        private final List<Integer> topKs = new ArrayList<>();

        @Override
        public void checkVectorField() {
        }

        @Override
        public CompletableFuture<Void> add(List<Map<String, Object>> data, Integer batchSize, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<List<RetrievalResult>> search(
                List<Double> queryVector,
                int topK,
                VectorStoreFilter filters,
                Map<String, Object> kwargs
        ) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<List<RetrievalResult>> sparseSearch(
                String queryText,
                int topK,
                VectorStoreFilter filters,
                Map<String, Object> kwargs
        ) {
            queries.add(queryText);
            topKs.add(topK);
            if (sparseResults.isEmpty()) {
                return CompletableFuture.completedFuture(List.of(result(queryText, queryText + "-result", 1.0d, Map.of())));
            }
            return CompletableFuture.completedFuture(sparseResults);
        }

        @Override
        public CompletableFuture<List<RetrievalResult>> hybridSearch(
                String queryText,
                List<Double> queryVector,
                int topK,
                double alpha,
                VectorStoreFilter filters,
                Map<String, Object> kwargs
        ) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<Boolean> delete(List<String> ids, DeleteFilter filterExpr, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public CompletableFuture<Boolean> tableExists(String tableName) {
            return CompletableFuture.completedFuture(Boolean.FALSE);
        }

        @Override
        public CompletableFuture<Void> deleteTable(String tableName) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
