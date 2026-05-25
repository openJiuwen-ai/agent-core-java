/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.retrieval.retriever;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.retriever.HybridRetriever;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Hybrid retriever test cases.
 *
 * <p>Mirrors Python's {@code test_hybrid_retriever.py} in
 * {@code tests/unit_tests/core/retrieval/retriever/test_hybrid_retriever}.</p>
 */
@DisplayName("HybridRetriever Tests")
class TestHybridRetriever {

    private VectorStore mockVectorStore;
    private Embedding mockEmbedModel;

    @BeforeEach
    void setUp() {
        mockVectorStore = mock(VectorStore.class);
        when(mockVectorStore.getIndexType()).thenReturn("hybrid");

        mockEmbedModel = mock(Embedding.class);
        when(mockEmbedModel.embedQuery(anyString())).thenReturn(List.of(0.1f, 0.2f, 0.3f));

        when(mockVectorStore.hybridSearch(anyString(), anyList(), anyInt(), anyDouble(), anyMap(), anyMap()))
                .thenReturn(List.of(
                        new SearchResult("1", "Hybrid result 1", 0.95, null),
                        new SearchResult("2", "Hybrid result 2", 0.85, null)
                ));

        when(mockVectorStore.search(anyList(), anyInt(), anyMap(), anyMap()))
                .thenReturn(List.of(new SearchResult("1", "Vector result", 0.9, null)));

        when(mockVectorStore.sparseSearch(anyString(), anyInt(), anyMap(), anyMap()))
                .thenReturn(List.of(new SearchResult("1", "Sparse result", 0.8, null)));
    }

    @Nested
    @DisplayName("Retrieve - Hybrid Mode")
    class HybridModeTests {

        @Test
        @DisplayName("test_retrieve_hybrid_mode - hybrid retrieval mode")
        void testRetrieveHybridMode() {
            HybridRetriever retriever = new HybridRetriever(mockVectorStore, mockEmbedModel);
            List<RetrievalResult> results = retriever.retrieve("test query", 5, null, "hybrid", null);

            assertThat(results).hasSize(2);
            assertThat(results.get(0).getText()).isEqualTo("Hybrid result 1");
            verify(mockVectorStore).hybridSearch(anyString(), anyList(), anyInt(), anyDouble(), anyMap(), anyMap());
        }

        @Test
        @DisplayName("test_retrieve_hybrid_with_custom_alpha - hybrid with custom alpha")
        void testRetrieveHybridWithCustomAlpha() {
            HybridRetriever retriever = new HybridRetriever(mockVectorStore, mockEmbedModel, 0.7);
            List<RetrievalResult> results = retriever.retrieve("test query", 5, null, "hybrid", null);

            assertThat(results).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Retrieve - Vector Mode")
    class VectorModeTests {

        @Test
        @DisplayName("test_retrieve_vector_mode - vector retrieval mode")
        void testRetrieveVectorMode() {
            HybridRetriever retriever = new HybridRetriever(mockVectorStore, mockEmbedModel);
            List<RetrievalResult> results = retriever.retrieve("test query", 5, null, "vector", null);

            assertThat(results).hasSize(1);
            verify(mockVectorStore).search(anyList(), anyInt(), anyMap(), anyMap());
        }

        @Test
        @DisplayName("test_retrieve_vector_mode_without_embed_model - throws error without embed model")
        void testRetrieveVectorModeWithoutEmbedModel() {
            HybridRetriever retriever = new HybridRetriever(mockVectorStore, null);

            assertThatThrownBy(() -> retriever.retrieve("test query", 5, null, "vector", null))
                    .isInstanceOf(BaseError.class)
                    .hasMessageContaining("embed_model is required");
        }

        @Test
        @DisplayName("test_retrieve_vector_with_score_threshold - vector with score threshold")
        void testRetrieveVectorWithScoreThreshold() {
            HybridRetriever retriever = new HybridRetriever(mockVectorStore, mockEmbedModel);
            List<RetrievalResult> results = retriever.retrieve("test query", 5, 0.85, "vector", null);

            assertThat(results).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Retrieve - Sparse Mode")
    class SparseModeTests {

        @Test
        @DisplayName("test_retrieve_sparse_mode - sparse retrieval mode")
        void testRetrieveSparseMode() {
            HybridRetriever retriever = new HybridRetriever(mockVectorStore, mockEmbedModel);
            List<RetrievalResult> results = retriever.retrieve("test query", 5, null, "sparse", null);

            assertThat(results).hasSize(1);
            verify(mockVectorStore).sparseSearch(anyString(), anyInt(), anyMap(), anyMap());
        }
    }

    @Nested
    @DisplayName("Score Threshold Validation")
    class ScoreThresholdTests {

        @Test
        @DisplayName("test_retrieve_score_threshold_invalid_mode - score threshold in non-vector mode throws error")
        void testRetrieveScoreThresholdInvalidMode() {
            HybridRetriever retriever = new HybridRetriever(mockVectorStore, mockEmbedModel);

            assertThatThrownBy(() -> retriever.retrieve("test query", 5, 0.5, "hybrid", null))
                    .isInstanceOf(BaseError.class)
                    .hasMessageContaining("score_threshold is only supported");
        }
    }

    @Nested
    @DisplayName("Batch Retrieve")
    class BatchRetrieveTests {

        @Test
        @DisplayName("test_batch_retrieve - batch retrieval")
        void testBatchRetrieve() {
            HybridRetriever retriever = new HybridRetriever(mockVectorStore, mockEmbedModel);
            List<String> queries = List.of("query 1", "query 2");
            List<List<RetrievalResult>> resultsList = retriever.batchRetrieve(queries, 5, "hybrid", null);

            assertThat(resultsList).hasSize(2);
        }
    }
}