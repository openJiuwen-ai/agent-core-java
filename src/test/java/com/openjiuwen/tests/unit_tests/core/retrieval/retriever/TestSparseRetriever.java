/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.retrieval.retriever;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.retriever.SparseRetriever;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sparse retriever test cases.
 *
 * <p>Mirrors Python's {@code test_sparse_retriever.py} in
 * {@code tests/unit_tests/core/retrieval/retriever/test_sparse_retriever}.</p>
 */
@DisplayName("SparseRetriever Tests")
class TestSparseRetriever {

    private VectorStore mockVectorStore;

    @BeforeEach
    void setUp() {
        mockVectorStore = mock(VectorStore.class);
        when(mockVectorStore.getIndexType()).thenReturn("sparse");
        when(mockVectorStore.sparseSearch(anyString(), anyInt(), anyMap(), anyMap()))
                .thenReturn(List.of(
                        new SearchResult("1", "Result 1", 0.95, null),
                        new SearchResult("2", "Result 2", 0.85, null)
                ));
    }

    @Nested
    @DisplayName("Retrieve")
    class RetrieveTests {

        @Test
        @DisplayName("test_retrieve_success - retrieval success")
        void testRetrieveSuccess() {
            SparseRetriever retriever = new SparseRetriever(mockVectorStore);
            List<RetrievalResult> results = retriever.retrieve("test query", 5, null, "sparse", null);

            assertThat(results).hasSize(2);
            assertThat(results.get(0).getText()).isEqualTo("Result 1");
            assertThat(results.get(0).getScore()).isEqualTo(0.95);
        }

        @Test
        @DisplayName("test_retrieve_invalid_mode - invalid retrieval mode throws error")
        void testRetrieveInvalidMode() {
            SparseRetriever retriever = new SparseRetriever(mockVectorStore);

            assertThatThrownBy(() -> retriever.retrieve("test query", 5, null, "vector", null))
                    .isInstanceOf(BaseError.class)
                    .hasMessageContaining("only supports 'sparse' mode");
        }
    }

    @Nested
    @DisplayName("Batch Retrieve")
    class BatchRetrieveTests {

        @Test
        @DisplayName("test_batch_retrieve - batch retrieval")
        void testBatchRetrieve() {
            SparseRetriever retriever = new SparseRetriever(mockVectorStore);
            List<String> queries = List.of("query 1", "query 2");
            List<List<RetrievalResult>> resultsList = retriever.batchRetrieve(queries, 5, "sparse", null);

            assertThat(resultsList).hasSize(2);
            assertThat(resultsList.get(0)).hasSize(2);
            assertThat(resultsList.get(1)).hasSize(2);
        }
    }
}