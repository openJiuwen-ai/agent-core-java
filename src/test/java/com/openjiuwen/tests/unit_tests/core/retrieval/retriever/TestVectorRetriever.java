/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.retrieval.retriever;

import com.openjiuwen.core.common.exception.errors.BaseError;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.retriever.VectorRetriever;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Vector retriever test cases.
 *
 * <p>Mirrors Python's {@code test_vector_retriever.py} in
 * {@code tests/unit_tests/core/retrieval/retriever/test_vector_retriever}.</p>
 */
@DisplayName("VectorRetriever Tests")
class TestVectorRetriever {

    private VectorStore mockVectorStore;
    private Embedding mockEmbedModel;

    @BeforeEach
    void setUp() {
        mockVectorStore = mock(VectorStore.class);
        when(mockVectorStore.getIndexType()).thenReturn("vector");

        mockEmbedModel = mock(Embedding.class);
        when(mockEmbedModel.embedQuery(anyString())).thenReturn(List.of(0.1f, 0.2f, 0.3f));

        when(mockVectorStore.search(anyList(), anyInt(), anyMap(), anyMap()))
                .thenReturn(List.of(
                        new SearchResult("1", "Result 1", 0.95, null),
                        new SearchResult("2", "Result 2", 0.85, null)
                ));
        when(mockVectorStore.sparseSearch(anyString(), anyInt(), anyMap(), anyMap()))
                .thenReturn(List.of());
    }

    @Nested
    @DisplayName("Retrieve")
    class RetrieveTests {

        @Test
        @DisplayName("test_retrieve_success - retrieval success")
        void testRetrieveSuccess() {
            VectorRetriever retriever = new VectorRetriever(mockVectorStore, mockEmbedModel);
            List<RetrievalResult> results = retriever.retrieve("test query", 5, null, "vector", null);

            assertThat(results).hasSize(2);
            assertThat(results.get(0).getText()).isEqualTo("Result 1");
            assertThat(results.get(0).getScore()).isEqualTo(0.95);
        }

        @Test
        @DisplayName("test_retrieve_with_score_threshold - score threshold filtering")
        void testRetrieveWithScoreThreshold() {
            VectorRetriever retriever = new VectorRetriever(mockVectorStore, mockEmbedModel);
            List<RetrievalResult> results = retriever.retrieve("test query", 5, 0.9, "vector", null);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getScore()).isGreaterThanOrEqualTo(0.9);
        }

        @Test
        @DisplayName("test_retrieve_fallback_to_sparse - fallback to sparse when vector returns empty")
        void testRetrieveFallbackToSparse() {
            when(mockVectorStore.search(anyList(), anyInt(), anyMap(), anyMap()))
                    .thenReturn(List.of());
            when(mockVectorStore.sparseSearch(anyString(), anyInt(), anyMap(), anyMap()))
                    .thenReturn(List.of(new SearchResult("1", "Sparse result", 0.8, null)));

            VectorRetriever retriever = new VectorRetriever(mockVectorStore, mockEmbedModel);
            List<RetrievalResult> results = retriever.retrieve("test query", 5, null, "vector", null);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getText()).isEqualTo("Sparse result");
        }

        @Test
        @DisplayName("test_retrieve_without_embed_model - retrieval without embed model throws error")
        void testRetrieveWithoutEmbedModel() {
            VectorRetriever retriever = new VectorRetriever(mockVectorStore, null);

            assertThatThrownBy(() -> retriever.retrieve("test query", 5, null, "vector", null))
                    .isInstanceOf(BaseError.class)
                    .hasMessageContaining("embed_model is required");
        }
    }

    @Nested
    @DisplayName("Batch Retrieve")
    class BatchRetrieveTests {

        @Test
        @DisplayName("test_batch_retrieve - batch retrieval")
        void testBatchRetrieve() {
            VectorRetriever retriever = new VectorRetriever(mockVectorStore, mockEmbedModel);
            List<String> queries = List.of("query 1", "query 2");
            List<List<RetrievalResult>> resultsList = retriever.batchRetrieve(queries, 5, "vector", null);

            assertThat(resultsList).hasSize(2);
        }
    }
}