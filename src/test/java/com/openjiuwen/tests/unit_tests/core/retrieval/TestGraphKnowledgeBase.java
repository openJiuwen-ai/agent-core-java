/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.retrieval;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.errors.BaseError;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.IndexConfig;
import com.openjiuwen.core.retrieval.common.KnowledgeBaseConfig;
import com.openjiuwen.core.retrieval.common.RetrievalConfig;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.common.Triple;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.GraphKnowledgeBase;
import com.openjiuwen.core.retrieval.indexing.indexer.Indexer;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.Chunker;
import com.openjiuwen.core.retrieval.indexing.processor.extractor.Extractor;
import com.openjiuwen.core.retrieval.retriever.GraphRetriever;
import com.openjiuwen.core.retrieval.retriever.Retriever;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GraphRAG knowledge base implementation test cases.
 *
 * <p>Mirrors Python's {@code test_graph_knowledge_base.py} in
 * {@code tests/unit_tests/core/retrieval/test_graph_knowledge_base}.</p>
 */
@DisplayName("GraphKnowledgeBase Tests")
class TestGraphKnowledgeBase {

    private KnowledgeBaseConfig mockConfig;
    private VectorStore mockVectorStore;
    private Embedding mockEmbedModel;
    private Chunker mockChunker;
    private Extractor mockExtractor;
    private Indexer mockIndexManager;
    private Retriever mockChunkRetriever;
    private Retriever mockTripleRetriever;
    private BaseModelClient mockLlmClient;

    @BeforeEach
    void setUp() {
        mockConfig = new KnowledgeBaseConfig();
        mockConfig.setKbId("test_kb");
        mockConfig.setIndexType("vector");
        mockConfig.setUseGraph(true);

        mockVectorStore = mock(VectorStore.class);
        when(mockVectorStore.getIndexType()).thenReturn("hybrid");

        mockEmbedModel = mock(Embedding.class);
        when(mockEmbedModel.embedQuery(anyString())).thenReturn(List.of(0.1f, 0.2f, 0.3f));

        mockChunker = mock(Chunker.class);
        when(mockChunker.chunkDocuments(anyList())).thenReturn(List.of(
                new TextChunk("chunk_1", "Test chunk 1", "doc_1", null)
        ));

        mockExtractor = mock(Extractor.class);
        when(mockExtractor.extract(anyList(), anyMap())).thenReturn(List.of(
                new Triple("Alice", "knows", "Bob", null)
        ));

        mockIndexManager = mock(Indexer.class);
        when(mockIndexManager.buildIndex(anyList(), any(IndexConfig.class), any(Embedding.class), anyMap()))
                .thenReturn(true);
        when(mockIndexManager.deleteIndex(anyString(), anyString(), anyMap())).thenReturn(true);
        when(mockIndexManager.getIndexInfo(anyString())).thenReturn(Map.of("count", 10));

        mockChunkRetriever = mock(Retriever.class);
        when(mockChunkRetriever.getIndexType()).thenReturn("hybrid");

        mockTripleRetriever = mock(Retriever.class);
        when(mockTripleRetriever.getIndexType()).thenReturn("hybrid");

        mockLlmClient = mock(BaseModelClient.class);
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("test_init_with_retrievers - initialization with retrievers")
        void testInitWithRetrievers() {
            GraphKnowledgeBase kb = new GraphKnowledgeBase(
                    mockConfig,
                    mockVectorStore,
                    mockEmbedModel,
                    null,
                    null,
                    null,
                    null,
                    null,
                    mockChunkRetriever,
                    mockTripleRetriever
            );

            assertThat(kb).isNotNull();
        }

        @Test
        @DisplayName("test_init_with_vector_store - initialization with vector store")
        void testInitWithVectorStore() {
            GraphKnowledgeBase kb = new GraphKnowledgeBase(
                    mockConfig,
                    mockVectorStore,
                    mockEmbedModel,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );

            assertThat(kb).isNotNull();
        }
    }

    @Nested
    @DisplayName("Add Documents")
    class AddDocumentsTests {

        @Test
        @DisplayName("test_add_documents_with_graph - adding documents with graph index")
        void testAddDocumentsWithGraph() {
            GraphKnowledgeBase kb = new GraphKnowledgeBase(
                    mockConfig,
                    mockVectorStore,
                    mockEmbedModel,
                    null,
                    mockChunker,
                    mockExtractor,
                    mockIndexManager,
                    mockLlmClient,
                    null,
                    null
            );

            List<Document> documents = List.of(new Document("doc_1", "Test document", null));
            List<String> docIds = kb.addDocuments(documents);

            assertThat(docIds).hasSize(1);
            // Should build both chunk index and triple index (use_graph=true)
            verify(mockIndexManager, times(2)).buildIndex(anyList(), any(IndexConfig.class), any(Embedding.class), anyMap());
            verify(mockExtractor).extract(anyList(), anyMap());
        }

        @Test
        @DisplayName("test_add_documents_without_graph - adding documents without graph index")
        void testAddDocumentsWithoutGraph() {
            KnowledgeBaseConfig configNoGraph = new KnowledgeBaseConfig();
            configNoGraph.setKbId("test_kb");
            configNoGraph.setIndexType("vector");
            configNoGraph.setUseGraph(false);

            GraphKnowledgeBase kb = new GraphKnowledgeBase(
                    configNoGraph,
                    mockVectorStore,
                    mockEmbedModel,
                    null,
                    mockChunker,
                    mockExtractor,
                    mockIndexManager,
                    mockLlmClient,
                    null,
                    null
            );

            List<Document> documents = List.of(new Document("doc_1", "Test document", null));
            List<String> docIds = kb.addDocuments(documents);

            assertThat(docIds).hasSize(1);
            // Only build chunk index (use_graph=false)
            verify(mockIndexManager, times(1)).buildIndex(anyList(), any(IndexConfig.class), any(Embedding.class), anyMap());
        }

        @Test
        @DisplayName("test_add_documents_without_chunker - throws error without chunker")
        void testAddDocumentsWithoutChunker() {
            GraphKnowledgeBase kb = new GraphKnowledgeBase(
                    mockConfig,
                    mockVectorStore,
                    mockEmbedModel,
                    null,
                    null,  // no chunker
                    mockExtractor,
                    mockIndexManager,
                    mockLlmClient,
                    null,
                    null
            );

            List<Document> documents = List.of(new Document("doc_1", "Test document", null));

            assertThatThrownBy(() -> kb.addDocuments(documents))
                    .isInstanceOf(BaseError.class)
                    .hasMessageContaining("chunker is required");
        }
    }

    @Nested
    @DisplayName("Retrieve")
    class RetrieveTests {

        @Test
        @DisplayName("test_retrieve_with_graph - graph retrieval")
        void testRetrieveWithGraph() {
            when(mockChunkRetriever.retrieve(anyString(), any(), any(), any(), anyMap()))
                    .thenReturn(List.of(new RetrievalResult("id1", "Test result", 0.95, null)));

            GraphKnowledgeBase kb = new GraphKnowledgeBase(
                    mockConfig,
                    mockVectorStore,
                    mockEmbedModel,
                    null,
                    null,
                    null,
                    null,
                    null,
                    mockChunkRetriever,
                    mockTripleRetriever
            );

            RetrievalConfig config = new RetrievalConfig();
            config.setUseGraph(true);
            config.setTopK(5);

            List<RetrievalResult> results = kb.retrieve("test query", config);

            assertThat(results).isNotEmpty();
        }

        @Test
        @DisplayName("test_retrieve_without_graph_falls_back_to_simple - falls back to SimpleKB")
        void testRetrieveWithoutGraphFallsBackToSimple() {
            KnowledgeBaseConfig configNoGraph = new KnowledgeBaseConfig();
            configNoGraph.setKbId("test_kb");
            configNoGraph.setIndexType("vector");
            configNoGraph.setUseGraph(false);

            when(mockChunkRetriever.retrieve(anyString(), any(), any(), any(), anyMap()))
                    .thenReturn(List.of(new RetrievalResult("id1", "Simple result", 0.9, null)));

            GraphKnowledgeBase kb = new GraphKnowledgeBase(
                    configNoGraph,
                    mockVectorStore,
                    mockEmbedModel,
                    null,
                    null,
                    null,
                    null,
                    mockLlmClient,
                    mockChunkRetriever,
                    null
            );

            RetrievalConfig config = new RetrievalConfig();
            config.setUseGraph(false);
            config.setTopK(5);

            List<RetrievalResult> results = kb.retrieve("test query", config);

            assertThat(results).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Delete Documents")
    class DeleteDocumentsTests {

        @Test
        @DisplayName("test_delete_documents_with_graph - deleting documents with graph index")
        void testDeleteDocumentsWithGraph() {
            GraphKnowledgeBase kb = new GraphKnowledgeBase(
                    mockConfig,
                    mockVectorStore,
                    mockEmbedModel,
                    null,
                    null,
                    null,
                    mockIndexManager,
                    null,
                    null,
                    null
            );

            boolean result = kb.deleteDocuments(List.of("doc_1"));

            assertThat(result).isTrue();
            // Should delete both chunk index and triple index
            verify(mockIndexManager, times(2)).deleteIndex(anyString(), anyString(), anyMap());
        }

        @Test
        @DisplayName("test_delete_documents_without_graph - deleting documents without graph index")
        void testDeleteDocumentsWithoutGraph() {
            KnowledgeBaseConfig configNoGraph = new KnowledgeBaseConfig();
            configNoGraph.setKbId("test_kb");
            configNoGraph.setIndexType("vector");
            configNoGraph.setUseGraph(false);

            GraphKnowledgeBase kb = new GraphKnowledgeBase(
                    configNoGraph,
                    mockVectorStore,
                    mockEmbedModel,
                    null,
                    null,
                    null,
                    mockIndexManager,
                    null,
                    null,
                    null
            );

            boolean result = kb.deleteDocuments(List.of("doc_1"));

            assertThat(result).isTrue();
            // Only delete chunk index
            verify(mockIndexManager, times(1)).deleteIndex(anyString(), anyString(), anyMap());
        }
    }

    @Nested
    @DisplayName("Update Documents")
    class UpdateDocumentsTests {

        @Test
        @DisplayName("test_update_documents - updating documents")
        void testUpdateDocuments() {
            GraphKnowledgeBase kb = new GraphKnowledgeBase(
                    mockConfig,
                    mockVectorStore,
                    mockEmbedModel,
                    null,
                    mockChunker,
                    mockExtractor,
                    mockIndexManager,
                    mockLlmClient,
                    null,
                    null
            );

            List<Document> documents = List.of(new Document("doc_1", "Updated document", null));
            List<String> docIds = kb.updateDocuments(documents);

            assertThat(docIds).hasSize(1);
            // Should delete first then add
            verify(mockIndexManager, times(2)).deleteIndex(anyString(), anyString(), anyMap());
            verify(mockIndexManager, times(2)).buildIndex(anyList(), any(IndexConfig.class), any(Embedding.class), anyMap());
        }
    }

    @Nested
    @DisplayName("Statistics")
    class StatisticsTests {

        @Test
        @DisplayName("test_get_statistics_with_graph - getting statistics with graph")
        void testGetStatisticsWithGraph() {
            GraphKnowledgeBase kb = new GraphKnowledgeBase(
                    mockConfig,
                    mockVectorStore,
                    mockEmbedModel,
                    null,
                    null,
                    null,
                    mockIndexManager,
                    null,
                    null,
                    null
            );

            Map<String, Object> stats = kb.getStatistics();

            assertThat(stats.get("kb_id")).isEqualTo("test_kb");
            assertThat(stats.get("use_graph")).isEqualTo(true);
            assertThat(stats.containsKey("chunk_index_info")).isTrue();
            assertThat(stats.containsKey("triple_index_info")).isTrue();
        }

        @Test
        @DisplayName("test_get_statistics_without_index_manager - getting statistics without index manager")
        void testGetStatisticsWithoutIndexManager() {
            GraphKnowledgeBase kb = new GraphKnowledgeBase(mockConfig);

            Map<String, Object> stats = kb.getStatistics();

            assertThat(stats.get("kb_id")).isEqualTo("test_kb");
            assertThat(stats.get("index_exists")).isEqualTo(false);
        }
    }

    @Nested
    @DisplayName("Close")
    class CloseTests {

        @Test
        @DisplayName("test_close - closing knowledge base")
        void testClose() {
            GraphKnowledgeBase kb = new GraphKnowledgeBase(
                    mockConfig,
                    mockVectorStore,
                    mockEmbedModel,
                    null,
                    null,
                    null,
                    null,
                    null,
                    mockChunkRetriever,
                    mockTripleRetriever
            );

            kb.close();

            verify(mockChunkRetriever).close();
            verify(mockTripleRetriever).close();
        }
    }
}