/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.retrieval.retriever;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.errors.BaseError;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.retriever.GraphRetriever;
import com.openjiuwen.core.retrieval.retriever.Retriever;
import com.openjiuwen.core.retrieval.retriever.TripleBeamSearch;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Graph retriever test cases.
 *
 * <p>Mirrors Python's {@code test_graph_retriever.py} in
 * {@code tests/unit_tests/core/retrieval/retriever/test_graph_retriever}.</p>
 */
@DisplayName("GraphRetriever Tests")
class TestGraphRetriever {

    private Retriever mockChunkRetriever;
    private Retriever mockTripleRetriever;
    private VectorStore mockVectorStore;
    private Embedding mockEmbedModel;

    @BeforeEach
    void setUp() {
        mockChunkRetriever = mock(Retriever.class);
        when(mockChunkRetriever.getIndexType()).thenReturn("hybrid");
        when(mockChunkRetriever.supportsMode("hybrid")).thenReturn(true);

        mockTripleRetriever = mock(Retriever.class);
        when(mockTripleRetriever.getIndexType()).thenReturn("hybrid");
        when(mockTripleRetriever.supportsMode("hybrid")).thenReturn(true);

        mockVectorStore = mock(VectorStore.class);
        when(mockVectorStore.getIndexType()).thenReturn("hybrid");

        mockEmbedModel = mock(Embedding.class);
        when(mockEmbedModel.embedQuery(anyString())).thenReturn(List.of(0.1f, 0.2f, 0.3f));
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("test_init_with_retrievers - initialization with retrievers")
        void testInitWithRetrievers() {
            GraphRetriever retriever = new GraphRetriever(mockChunkRetriever, mockTripleRetriever);
            assertThat(retriever.getIndexType()).isEqualTo("hybrid");
        }

        @Test
        @DisplayName("test_init_with_vector_store - initialization with vector store")
        void testInitWithVectorStore() {
            GraphRetriever retriever = new GraphRetriever(
                    mockVectorStore,
                    mockEmbedModel,
                    "chunks",
                    "triples"
            );
            assertThat(retriever.getIndexType()).isEqualTo("hybrid");
        }
    }

    @Nested
    @DisplayName("Retrieve")
    class RetrieveTests {

        @Test
        @DisplayName("test_retrieve_score_threshold_invalid_mode - score threshold in non-vector mode")
        void testRetrieveScoreThresholdInvalidMode() {
            GraphRetriever retriever = new GraphRetriever(mockChunkRetriever, mockTripleRetriever);

            assertThatThrownBy(() -> retriever.retrieve("test query", 5, 0.8, "sparse", null))
                    .isInstanceOf(BaseError.class)
                    .hasMessageContaining("score_threshold is only supported");
        }

        @Test
        @DisplayName("test_retrieve_hybrid_mode - hybrid mode retrieval")
        void testRetrieveHybridMode() {
            when(mockChunkRetriever.retrieve(anyString(), any(), any(), any(), any()))
                    .thenReturn(List.of(new RetrievalResult("id1", "Chunk result", 0.9, null)));

            GraphRetriever retriever = new GraphRetriever(mockChunkRetriever, mockTripleRetriever);
            List<RetrievalResult> results = retriever.retrieve("test query", 5, null, "hybrid", null);

            assertThat(results).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Graph Expansion")
    class GraphExpansionTests {

        @Test
        @DisplayName("test_graph_expansion_empty_chunks - graph expansion with empty chunks")
        void testGraphExpansionEmptyChunks() {
            GraphRetriever retriever = new GraphRetriever(mockChunkRetriever, mockTripleRetriever);
            List<RetrievalResult> results = retriever.graphExpansion(
                    "test query",
                    List.of(),
                    null,
                    5,
                    "hybrid",
                    null
            );

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("test_graph_expansion_with_chunks - graph expansion with chunks")
        void testGraphExpansionWithChunks() {
            when(mockChunkRetriever.retrieve(anyString(), any(), any(), any(), any()))
                    .thenReturn(List.of(new RetrievalResult("id1", "Result 1", 0.9, null)));

            GraphRetriever retriever = new GraphRetriever(mockChunkRetriever, mockTripleRetriever);
            List<RetrievalResult> chunks = List.of(new RetrievalResult("id1", "Chunk", 0.9, null));
            List<RetrievalResult> results = retriever.graphExpansion(
                    "test query",
                    chunks,
                    null,
                    5,
                    "hybrid",
                    null
            );

            assertThat(results).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Close")
    class CloseTests {

        @Test
        @DisplayName("test_close - closing retriever")
        void testClose() {
            GraphRetriever retriever = new GraphRetriever(mockChunkRetriever, mockTripleRetriever);
            retriever.close();

            verify(mockChunkRetriever).close();
            verify(mockTripleRetriever).close();
        }
    }

    @Nested
    @DisplayName("TripleBeamSearch Tests")
    class TripleBeamSearchTests {

        @Test
        @DisplayName("test_init_invalid_max_length - TripleBeamSearch validation")
        void testInitInvalidMaxLength() {
            assertThatThrownBy(() -> new TripleBeamSearch(mockChunkRetriever, 2, 100, 0))
                    .isInstanceOf(BaseError.class)
                    .hasMessageContaining("expect max_length >= 1");
        }

        @Test
        @DisplayName("test_init_valid - valid TripleBeamSearch initialization")
        void testInitValid() {
            TripleBeamSearch search = new TripleBeamSearch(mockChunkRetriever, 2, 100, 2);
            assertThat(search).isNotNull();
        }
    }

    @Nested
    @DisplayName("Index Type")
    class IndexTypeTests {

        @Test
        @DisplayName("test_get_index_type - returns correct index type")
        void testGetIndexType() {
            when(mockVectorStore.getIndexType()).thenReturn("vector");
            GraphRetriever retriever = new GraphRetriever(
                    mockVectorStore,
                    mockEmbedModel,
                    "chunks",
                    "triples"
            );
            assertThat(retriever.getIndexType()).isEqualTo("vector");
        }

        @Test
        @DisplayName("test_set_index_type - sets index type")
        void testSetIndexType() {
            GraphRetriever retriever = new GraphRetriever(mockChunkRetriever, mockTripleRetriever);
            retriever.setIndexType("vector");
            assertThat(retriever.getIndexType()).isEqualTo("vector");
        }
    }
}