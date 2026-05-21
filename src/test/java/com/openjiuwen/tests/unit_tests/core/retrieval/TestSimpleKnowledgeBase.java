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
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.SimpleKnowledgeBase;
import com.openjiuwen.core.retrieval.indexing.indexer.Indexer;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.Chunker;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Simple knowledge base implementation test cases.
 *
 * <p>Mirrors Python's {@code test_simple_knowledge_base.py} in
 * {@code tests/unit_tests/core/retrieval/test_simple_knowledge_base}.</p>
 */
@DisplayName("SimpleKnowledgeBase Tests")
class TestSimpleKnowledgeBase {

    private KnowledgeBaseConfig mockConfig;
    private VectorStore mockVectorStore;
    private Embedding mockEmbedModel;
    private Chunker mockChunker;
    private Indexer mockIndexManager;
    private Retriever mockRetriever;
    private BaseModelClient mockLlmClient;

    @BeforeEach
    void setUp() {
        mockConfig = new KnowledgeBaseConfig();
        mockConfig.setKbId("test_kb");
        mockConfig.setIndexType("vector");

        mockVectorStore = mock(VectorStore.class);
        when(mockVectorStore.getIndexType()).thenReturn("hybrid");

        mockEmbedModel = mock(Embedding.class);
        when(mockEmbedModel.embedQuery(anyString())).thenReturn(List.of(0.1f, 0.2f, 0.3f));

        mockChunker = mock(Chunker.class);
        when(mockChunker.chunkDocuments(anyList())).thenReturn(List.of(
                new TextChunk("chunk_1", "Test chunk 1", "doc_1", null),
                new TextChunk("chunk_2", "Test chunk 2", "doc_1", null)
        ));

        mockIndexManager = mock(Indexer.class);
        when(mockIndexManager.buildIndex(anyList(), any(IndexConfig.class), any(Embedding.class), anyMap()))
                .thenReturn(true);
        when(mockIndexManager.deleteIndex(anyString(), anyString(), anyMap())).thenReturn(true);
        when(mockIndexManager.getIndexInfo(anyString())).thenReturn(Map.of("count", 10));

        mockRetriever = mock(Retriever.class);
        when(mockRetriever.getIndexType()).thenReturn("hybrid");
        when(mockRetriever.retrieve(anyString(), any(), any(), any(), anyMap()))
                .thenReturn(List.of(new RetrievalResult("id1", "Test result", 0.95, null)));

        mockLlmClient = mock(BaseModelClient.class);
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("test_init_with_config - initialization with config only")
        void testInitWithConfig() {
            SimpleKnowledgeBase kb = new SimpleKnowledgeBase(mockConfig);
            assertThat(kb).isNotNull();
        }

        @Test
        @DisplayName("test_init_with_all_params - initialization with all parameters")
        void testInitWithAllParams() {
            SimpleKnowledgeBase kb = new SimpleKnowledgeBase(
                    mockConfig,
                    mockVectorStore,
                    mockEmbedModel,
                    null,
                    mockChunker,
                    mockIndexManager,
                    mockLlmClient,
                    mockRetriever
            );
            assertThat(kb).isNotNull();
        }
    }

    @Nested
    @DisplayName("Add Documents")
    class AddDocumentsTests {

        @Test
        @DisplayName("test_add_documents_success - adding documents successfully")
        void testAddDocumentsSuccess() {
            SimpleKnowledgeBase kb = new SimpleKnowledgeBase(
                    mockConfig,
                    mockVectorStore,
                    mockEmbedModel,
                    null,
                    mockChunker,
                    mockIndexManager,
                    mockLlmClient,
                    null
            );

            List<Document> documents = List.of(
                    new Document("doc_1", "Test document 1", null),
                    new Document("doc_2", "Test document 2", null)
            );
            List<String> docIds = kb.addDocuments(documents);

            assertThat(docIds).hasSize(2);
            assertThat(docIds).containsExactly("doc_1", "doc_2");
            verify(mockChunker).chunkDocuments(anyList());
            verify(mockIndexManager).buildIndex(anyList(), any(IndexConfig.class), any(Embedding.class), anyMap());
        }

        @Test
        @DisplayName("test_add_documents_without_chunker - throws error without chunker")
        void testAddDocumentsWithoutChunker() {
            SimpleKnowledgeBase kb = new SimpleKnowledgeBase(mockConfig);

            List<Document> documents = List.of(new Document("doc_1", "Test document", null));

            assertThatThrownBy(() -> kb.addDocuments(documents))
                    .isInstanceOf(BaseError.class)
                    .hasMessageContaining("chunker is required");
        }

        @Test
        @DisplayName("test_add_documents_without_index_manager - throws error without index manager")
        void testAddDocumentsWithoutIndexManager() {
            SimpleKnowledgeBase kb = new SimpleKnowledgeBase(
                    mockConfig,
                    mockVectorStore,
                    mockEmbedModel,
                    null,
                    mockChunker,
                    null,  // no index manager
                    mockLlmClient,
                    null
            );

            List<Document> documents = List.of(new Document("doc_1", "Test document", null));

            assertThatThrownBy(() -> kb.addDocuments(documents))
                    .isInstanceOf(BaseError.class)
                    .hasMessageContaining("index_manager is required");
        }

        @Test
        @DisplayName("test_add_documents_build_index_failed - throws error when build fails")
        void testAddDocumentsBuildIndexFailed() {
            when(mockIndexManager.buildIndex(anyList(), any(IndexConfig.class), any(Embedding.class), anyMap()))
                    .thenReturn(false);

            SimpleKnowledgeBase kb = new SimpleKnowledgeBase(
                    mockConfig,
                    mockVectorStore,
                    mockEmbedModel,
                    null,
                    mockChunker,
                    mockIndexManager,
                    mockLlmClient,
                    null
            );

            List<Document> documents = List.of(new Document("doc_1", "Test document", null));

            assertThatThrownBy(() -> kb.addDocuments(documents))
                    .isInstanceOf(BaseError.class)
                    .hasMessageContaining("Failed to build index");
        }
    }

    @Nested
    @DisplayName("Retrieve")
    class RetrieveTests {

        @Test
        @DisplayName("test_retrieve_with_retriever - retrieval with provided retriever")
        void testRetrieveWithRetriever() {
            SimpleKnowledgeBase kb = new SimpleKnowledgeBase(
                    mockConfig,
                    mockVectorStore,
                    mockEmbedModel,
                    null,
                    null,
                    null,
                    mockLlmClient,
                    mockRetriever
            );

            List<RetrievalResult> results = kb.retrieve("test query", null);

            assertThat(results).hasSize(1);
            verify(mockRetriever).retrieve(anyString(), any(), any(), any(), anyMap());
        }

        @Test
        @DisplayName("test_retrieve_with_config - retrieval with RetrievalConfig")
        void testRetrieveWithConfig() {
            SimpleKnowledgeBase kb = new SimpleKnowledgeBase(
                    mockConfig,
                    mockVectorStore,
                    mockEmbedModel,
                    null,
                    null,
                    null,
                    mockLlmClient,
                    mockRetriever
            );

            RetrievalConfig config = new RetrievalConfig();
            config.setTopK(5);
            config.setScoreThreshold(0.8);

            List<RetrievalResult> results = kb.retrieve("test query", config);

            assertThat(results).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Delete Documents")
    class DeleteDocumentsTests {

        @Test
        @DisplayName("test_delete_documents_success - deleting documents successfully")
        void testDeleteDocumentsSuccess() {
            SimpleKnowledgeBase kb = new SimpleKnowledgeBase(
                    mockConfig,
                    mockVectorStore,
                    mockEmbedModel,
                    null,
                    mockChunker,
                    mockIndexManager,
                    mockLlmClient,
                    null
            );

            boolean result = kb.deleteDocuments(List.of("doc_1", "doc_2"));

            assertThat(result).isTrue();
            verify(mockIndexManager, times(2)).deleteIndex(anyString(), anyString(), anyMap());
        }
    }

    @Nested
    @DisplayName("Update Documents")
    class UpdateDocumentsTests {

        @Test
        @DisplayName("test_update_documents_success - updating documents successfully")
        void testUpdateDocumentsSuccess() {
            SimpleKnowledgeBase kb = new SimpleKnowledgeBase(
                    mockConfig,
                    mockVectorStore,
                    mockEmbedModel,
                    null,
                    mockChunker,
                    mockIndexManager,
                    mockLlmClient,
                    null
            );

            List<Document> documents = List.of(new Document("doc_1", "Updated document", null));
            List<String> docIds = kb.updateDocuments(documents);

            assertThat(docIds).hasSize(1);
            // Should delete first then add
            verify(mockIndexManager).deleteIndex(anyString(), anyString(), anyMap());
            verify(mockIndexManager).buildIndex(anyList(), any(IndexConfig.class), any(Embedding.class), anyMap());
        }
    }

    @Nested
    @DisplayName("Statistics")
    class StatisticsTests {

        @Test
        @DisplayName("test_get_statistics_with_index_manager - getting statistics with index manager")
        void testGetStatisticsWithIndexManager() {
            SimpleKnowledgeBase kb = new SimpleKnowledgeBase(
                    mockConfig,
                    mockVectorStore,
                    mockEmbedModel,
                    null,
                    mockChunker,
                    mockIndexManager,
                    mockLlmClient,
                    null
            );

            Map<String, Object> stats = kb.getStatistics();

            assertThat(stats.get("kb_id")).isEqualTo("test_kb");
            assertThat(stats.containsKey("chunk_index_info")).isTrue();
        }

        @Test
        @DisplayName("test_get_statistics_without_index_manager - getting statistics without index manager")
        void testGetStatisticsWithoutIndexManager() {
            SimpleKnowledgeBase kb = new SimpleKnowledgeBase(mockConfig);

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
            SimpleKnowledgeBase kb = new SimpleKnowledgeBase(
                    mockConfig,
                    mockVectorStore,
                    mockEmbedModel,
                    null,
                    null,
                    null,
                    mockLlmClient,
                    mockRetriever
            );

            kb.close();

            verify(mockRetriever).close();
        }
    }
}