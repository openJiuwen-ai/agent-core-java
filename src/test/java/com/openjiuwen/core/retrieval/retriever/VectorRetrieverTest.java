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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code VectorRetriever} behavior in
 * {@code openjiuwen/core/retrieval/retriever/vector_retriever.py}.
 */
class VectorRetrieverTest {

    @Test
    void retrieveUsesVectorSearchAndMetadataChunkId() {
        FakeEmbedding embedding = new FakeEmbedding(List.of(0.1d, 0.2d));
        FakeVectorStore vectorStore = new FakeVectorStore();
        vectorStore.vectorResults = List.of(
                result("raw-id", "low", 0.2d, Map.of("doc_id", "doc-low", "chunk_id", "chunk-low")),
                result("raw-id-2", "high", 0.9d, Map.of("doc_id", "doc-high", "chunk_id", "chunk-high"))
        );
        VectorRetriever retriever = new VectorRetriever(vectorStore, embedding);

        List<RetrievalResult> results = retriever.retrieve("query", 4, 0.5d, "vector", Map.of("ignored", "value"));

        assertThat(embedding.queries).containsExactly("query");
        assertThat(vectorStore.vectorCalls).isEqualTo(1);
        assertThat(vectorStore.lastVector).containsExactly(0.1d, 0.2d);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getDocId()).isEqualTo("doc-high");
        assertThat(results.getFirst().getChunkId()).isEqualTo("chunk-high");
    }

    @Test
    void vectorSearchFallsBackToSparseWhenEmpty() {
        FakeEmbedding embedding = new FakeEmbedding(List.of(0.3d));
        FakeVectorStore vectorStore = new FakeVectorStore();
        vectorStore.vectorResults = List.of();
        vectorStore.sparseResults = List.of(result("sparse-id", "fallback", 0.6d, Map.of("doc_id", "doc-s")));
        VectorRetriever retriever = new VectorRetriever(vectorStore, embedding);

        List<RetrievalResult> results = retriever.retrieve("query", 5, null, "vector", Map.of());

        assertThat(vectorStore.vectorCalls).isEqualTo(1);
        assertThat(vectorStore.sparseCalls).isEqualTo(1);
        assertThat(results).extracting(RetrievalResult::getText).containsExactly("fallback");
    }

    @Test
    void nonVectorRetrieveModeIsRejected() {
        VectorRetriever retriever = new VectorRetriever(new FakeVectorStore(), new FakeEmbedding(List.of(1.0d)));

        assertThatThrownBy(() -> retriever.retrieve("query", 5, null, "sparse", Map.of()))
                .isInstanceOf(BaseError.class)
                .satisfies(error -> assertThat(((BaseError) error).getStatus())
                        .isEqualTo(StatusCode.RETRIEVAL_RETRIEVER_MODE_NOT_SUPPORT));
    }

    @Test
    void missingEmbeddingRejectsVectorSearch() {
        VectorRetriever retriever = new VectorRetriever(new FakeVectorStore());

        assertThatThrownBy(() -> retriever.retrieve("query", 5, null, "vector", Map.of()))
                .isInstanceOf(BaseError.class)
                .satisfies(error -> assertThat(((BaseError) error).getStatus())
                        .isEqualTo(StatusCode.RETRIEVAL_RETRIEVER_EMBED_MODEL_NOT_FOUND));
    }

    @Test
    void retrieveSearchResultsKeepsPythonModeAgnosticBehavior() {
        FakeEmbedding embedding = new FakeEmbedding(List.of(0.7d));
        FakeVectorStore vectorStore = new FakeVectorStore();
        vectorStore.vectorResults = List.of(result("vector-id", "raw", 0.8d, Map.of()));
        VectorRetriever retriever = new VectorRetriever(vectorStore, embedding);

        List<SearchResult> results = retriever.retrieveSearchResults("query", 2, "hybrid", Map.of());

        assertThat(vectorStore.vectorCalls).isEqualTo(1);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getId()).isEqualTo("vector-id");
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
        private List<RetrievalResult> vectorResults = List.of();
        private List<RetrievalResult> sparseResults = List.of();
        private int vectorCalls;
        private int sparseCalls;
        private List<Double> lastVector;

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
            lastVector = List.copyOf(queryVector);
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
