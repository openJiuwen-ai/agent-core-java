/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.retriever;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code HybridRetriever} behavior in
 * {@code openjiuwen/core/retrieval/retriever/hybrid_retriever.py}.
 */
class HybridRetrieverTest {

    @Test
    void hybridRetrievalUsesEmbedModelAndAlphaOverride() {
        FakeEmbedding embedding = new FakeEmbedding(List.of(0.1d, 0.2d));
        FakeVectorStore vectorStore = new FakeVectorStore();
        vectorStore.hybridResults = List.of(result("chunk-1", "hybrid", 0.8d, Map.of("doc_id", "doc-1")));
        HybridRetriever retriever = new HybridRetriever(vectorStore, embedding, 0.5d);

        List<RetrievalResult> results = retriever.retrieve("hello", 3, null, "hybrid", Map.of("alpha", 0.75d));

        assertThat(embedding.queries).containsExactly("hello");
        assertThat(vectorStore.lastHybridText).isEqualTo("hello");
        assertThat(vectorStore.lastHybridVector).containsExactly(0.1d, 0.2d);
        assertThat(vectorStore.lastHybridTopK).isEqualTo(3);
        assertThat(vectorStore.lastHybridAlpha).isEqualTo(0.75d);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getDocId()).isEqualTo("doc-1");
        assertThat(results.getFirst().getChunkId()).isEqualTo("chunk-1");
    }

    @Test
    void vectorRetrievalFallsBackToSparseAndAppliesThreshold() {
        FakeEmbedding embedding = new FakeEmbedding(List.of(0.4d, 0.5d));
        FakeVectorStore vectorStore = new FakeVectorStore();
        vectorStore.vectorResults = List.of();
        vectorStore.sparseResults = List.of(
                result("low", "discard", 0.2d, Map.of("doc_id", "doc-low")),
                result("high", "keep", 0.9d, Map.of("doc_id", "doc-high"))
        );
        HybridRetriever retriever = new HybridRetriever(vectorStore, embedding);

        List<RetrievalResult> results = retriever.retrieve("question", 5, 0.5d, "vector", Map.of());

        assertThat(vectorStore.vectorCalls).isEqualTo(1);
        assertThat(vectorStore.sparseCalls).isEqualTo(1);
        assertThat(results).extracting(RetrievalResult::getText).containsExactly("keep");
    }

    @Test
    void nonVectorScoreThresholdIsRejected() {
        HybridRetriever retriever = new HybridRetriever(new FakeVectorStore(), new FakeEmbedding(List.of(1.0d)));

        assertThatThrownBy(() -> retriever.retrieve("query", 5, 0.2d, "hybrid", Map.of()))
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.RETRIEVAL_RETRIEVER_SCORE_THRESHOLD_INVALID);
    }

    @Test
    void missingEmbeddingRejectsVectorMode() {
        HybridRetriever retriever = new HybridRetriever(new FakeVectorStore());

        assertThatThrownBy(() -> retriever.retrieve("query", 5, null, "vector", Map.of()))
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.RETRIEVAL_RETRIEVER_EMBED_MODEL_NOT_FOUND);
    }

    @Test
    void retrieveSearchResultsConvertsRawRetrievalResults() {
        FakeVectorStore vectorStore = new FakeVectorStore();
        vectorStore.sparseResults = List.of(result("chunk-9", "raw", 0.6d, Map.of("doc_id", "doc-9")));
        HybridRetriever retriever = new HybridRetriever(vectorStore);

        List<SearchResult> results = retriever.retrieveSearchResults("query", 2, "sparse", Map.of());

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getId()).isEqualTo("chunk-9");
        assertThat(results.getFirst().getText()).isEqualTo("raw");
        assertThat(results.getFirst().getMetadata()).containsEntry("doc_id", "doc-9");
    }

    private static RetrievalResult result(String chunkId, String text, double score, Map<String, Object> metadata) {
        return new RetrievalResult(text, score, metadata, null, chunkId);
    }

    private static final class FakeEmbedding extends Embedding {
        private final List<Double> vector;
        private final List<String> queries = new ArrayList<>();

        private FakeEmbedding(List<Double> vector) {
            this.vector = vector;
        }

        @Override
        public CompletableFuture<List<Double>> embedQuery(String text, Map<String, Object> kwargs) {
            queries.add(text);
            return CompletableFuture.completedFuture(vector);
        }

        @Override
        public CompletableFuture<List<List<Double>>> embedDocuments(
                List<String> texts,
                Integer batchSize,
                Map<String, Object> kwargs
        ) {
            return CompletableFuture.completedFuture(List.of(vector));
        }

        @Override
        public int getDimension() {
            return vector.size();
        }
    }

    private static final class FakeVectorStore implements VectorStore {
        private List<RetrievalResult> hybridResults = List.of();
        private List<RetrievalResult> vectorResults = List.of();
        private List<RetrievalResult> sparseResults = List.of();
        private String lastHybridText;
        private List<Double> lastHybridVector;
        private int lastHybridTopK;
        private double lastHybridAlpha;
        private int vectorCalls;
        private int sparseCalls;

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
            vectorCalls++;
            return CompletableFuture.completedFuture(vectorResults);
        }

        @Override
        public CompletableFuture<List<RetrievalResult>> sparseSearch(
                String queryText,
                int topK,
                VectorStoreFilter filters,
                Map<String, Object> kwargs
        ) {
            sparseCalls++;
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
            lastHybridText = queryText;
            lastHybridVector = queryVector == null ? null : List.copyOf(queryVector);
            lastHybridTopK = topK;
            lastHybridAlpha = alpha;
            return CompletableFuture.completedFuture(hybridResults);
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
