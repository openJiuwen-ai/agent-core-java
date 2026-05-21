/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.retrieval.retriever;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.errors.BaseError;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.retriever.AgenticRetriever;
import com.openjiuwen.core.retrieval.retriever.GraphRetriever;
import com.openjiuwen.core.retrieval.retriever.Retriever;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Agentic retriever test cases.
 *
 * <p>Mirrors Python's {@code test_agentic_retriever.py} in
 * {@code tests/unit_tests/core/retrieval/retriever/test_agentic_retriever}.</p>
 */
@DisplayName("AgenticRetriever Tests")
class TestAgenticRetriever {

    private GraphRetriever mockGraphRetriever;
    private Retriever mockBaseRetriever;
    private BaseModelClient mockLlmClient;

    @BeforeEach
    void setUp() {
        mockGraphRetriever = mock(GraphRetriever.class);
        when(mockGraphRetriever.getIndexType()).thenReturn("hybrid");
        when(mockGraphRetriever.supportsMode("hybrid")).thenReturn(true);

        mockBaseRetriever = mock(Retriever.class);
        when(mockBaseRetriever.getIndexType()).thenReturn("hybrid");
        when(mockBaseRetriever.supportsMode("hybrid")).thenReturn(true);

        mockLlmClient = mock(BaseModelClient.class);
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("test_init_success_with_graph_retriever - successful initialization with graph retriever")
        void testInitSuccessWithGraphRetriever() {
            AgenticRetriever retriever = new AgenticRetriever(mockGraphRetriever, mockLlmClient, 3);

            assertThat(retriever.isGraphRetriever()).isTrue();
            assertThat(retriever.getDefaultMode()).isEqualTo("hybrid");
        }

        @Test
        @DisplayName("test_init_success_with_base_retriever - successful initialization with generic retriever")
        void testInitSuccessWithBaseRetriever() {
            AgenticRetriever retriever = new AgenticRetriever(mockBaseRetriever, mockLlmClient, 2);

            assertThat(retriever.isGraphRetriever()).isFalse();
            assertThat(retriever.getDefaultMode()).isEqualTo("hybrid");
        }

        @Test
        @DisplayName("test_init_with_defaults - initialization with default values")
        void testInitWithDefaults() {
            AgenticRetriever retriever = new AgenticRetriever(mockGraphRetriever, mockLlmClient);

            // Default maxIter is 2
            assertThat(retriever.getIndexType()).isEqualTo("hybrid");
        }

        @Test
        @DisplayName("test_init_with_invalid_max_iter - invalid max_iter falls back to default")
        void testInitWithInvalidMaxIter() {
            AgenticRetriever retriever = new AgenticRetriever(mockGraphRetriever, mockLlmClient, -1);

            // Should still work, maxIter defaults to 2 when invalid
            assertThat(retriever.getIndexType()).isEqualTo("hybrid");
        }

        @Test
        @DisplayName("test_init_without_retriever - initialization without retriever throws error")
        void testInitWithoutRetriever() {
            assertThatThrownBy(() -> new AgenticRetriever(null, mockLlmClient))
                    .isInstanceOf(BaseError.class)
                    .hasMessageContaining("retriever is required");
        }

        @Test
        @DisplayName("test_init_without_llm_client - initialization without LLM client throws error")
        void testInitWithoutLlmClient() {
            assertThatThrownBy(() -> new AgenticRetriever(mockGraphRetriever, null))
                    .isInstanceOf(BaseError.class)
                    .hasMessageContaining("llm_client is required");
        }
    }

    @Nested
    @DisplayName("Default Mode")
    class DefaultModeTests {

        @Test
        @DisplayName("test_default_mode_vector - default mode when index_type is vector")
        void testDefaultModeVector() {
            Retriever mockVectorRetriever = mock(Retriever.class);
            when(mockVectorRetriever.getIndexType()).thenReturn("vector");

            AgenticRetriever r = new AgenticRetriever(mockVectorRetriever, mockLlmClient);

            assertThat(r.getDefaultMode()).isEqualTo("vector");
        }

        @Test
        @DisplayName("test_default_mode_bm25 - default mode when index_type is bm25")
        void testDefaultModeBm25() {
            Retriever mockBm25Retriever = mock(Retriever.class);
            when(mockBm25Retriever.getIndexType()).thenReturn("bm25");

            AgenticRetriever r = new AgenticRetriever(mockBm25Retriever, mockLlmClient);

            assertThat(r.getDefaultMode()).isEqualTo("sparse");
        }

        @Test
        @DisplayName("test_default_mode_hybrid - default mode when index_type is hybrid")
        void testDefaultModeHybrid() {
            Retriever mockHybridRetriever = mock(Retriever.class);
            when(mockHybridRetriever.getIndexType()).thenReturn("hybrid");

            AgenticRetriever r = new AgenticRetriever(mockHybridRetriever, mockLlmClient);

            assertThat(r.getDefaultMode()).isEqualTo("hybrid");
        }
    }

    @Nested
    @DisplayName("Retrieve - Invalid Parameters")
    class RetrieveInvalidParamsTests {

        @Test
        @DisplayName("test_retrieve_without_valid_top_k - retrieval with invalid top_k throws error")
        void testRetrieveWithoutValidTopK() {
            AgenticRetriever retriever = new AgenticRetriever(mockGraphRetriever, mockLlmClient);

            assertThatThrownBy(() -> retriever.retrieve("test query", 0, null, null, null))
                    .isInstanceOf(BaseError.class)
                    .hasMessageContaining("top_k is invalid");
        }

        @Test
        @DisplayName("test_retrieve_with_negative_top_k - retrieval with negative top_k throws error")
        void testRetrieveWithNegativeTopK() {
            AgenticRetriever retriever = new AgenticRetriever(mockGraphRetriever, mockLlmClient);

            assertThatThrownBy(() -> retriever.retrieve("test query", -1, null, null, null))
                    .isInstanceOf(BaseError.class)
                    .hasMessageContaining("top_k is invalid");
        }
    }

    @Nested
    @DisplayName("Retrieve - Generic Path")
    class RetrieveGenericTests {

        @Test
        @DisplayName("test_retrieve_generic - generic retrieval returns results")
        void testRetrieveGeneric() {
            when(mockBaseRetriever.retrieve("test query", 5, null, "hybrid", null))
                    .thenReturn(List.of(new RetrievalResult("id1", "Result 1", 0.9, null)));

            AgenticRetriever retriever = new AgenticRetriever(mockBaseRetriever, mockLlmClient, 2);
            List<RetrievalResult> results = retriever.retrieve("test query", 5, null, "hybrid", null);

            assertThat(results).isNotEmpty();
            assertThat(results.get(0).getText()).isEqualTo("Result 1");
        }
    }

    @Nested
    @DisplayName("Retrieve - Graph Path")
    class RetrieveGraphTests {

        @Test
        @DisplayName("test_retrieve_with_graph - graph retrieval returns results")
        void testRetrieveWithGraph() {
            when(mockGraphRetriever.retrieve("test query", 5, null, "hybrid", null))
                    .thenReturn(List.of(new RetrievalResult("id1", "Graph result", 0.95, null)));

            AgenticRetriever retriever = new AgenticRetriever(mockGraphRetriever, mockLlmClient, 2);
            List<RetrievalResult> results = retriever.retrieve("test query", 5, null, "hybrid", null);

            assertThat(results).isNotEmpty();
            assertThat(results.get(0).getText()).isEqualTo("Graph result");
        }
    }

    @Nested
    @DisplayName("Batch Retrieve")
    class BatchRetrieveTests {

        @Test
        @DisplayName("test_batch_retrieve - batch retrieval returns results for each query")
        void testBatchRetrieve() {
            when(mockBaseRetriever.retrieve("query 1", 5, null, "hybrid", null))
                    .thenReturn(List.of(new RetrievalResult("id1", "Result 1", 0.9, null)));
            when(mockBaseRetriever.retrieve("query 2", 5, null, "hybrid", null))
                    .thenReturn(List.of(new RetrievalResult("id2", "Result 2", 0.85, null)));

            AgenticRetriever retriever = new AgenticRetriever(mockBaseRetriever, mockLlmClient);
            List<String> queries = List.of("query 1", "query 2");
            List<List<RetrievalResult>> resultsList = retriever.batchRetrieve(queries, 5, "hybrid", null);

            assertThat(resultsList).hasSize(2);
            assertThat(resultsList.get(0)).hasSize(1);
            assertThat(resultsList.get(1)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Close")
    class CloseTests {

        @Test
        @DisplayName("test_close - closing retriever calls close on underlying retriever")
        void testClose() {
            AgenticRetriever retriever = new AgenticRetriever(mockGraphRetriever, mockLlmClient);
            retriever.close();

            verify(mockGraphRetriever).close();
        }
    }
}