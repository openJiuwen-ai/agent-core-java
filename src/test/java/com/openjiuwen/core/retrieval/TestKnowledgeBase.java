/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.retrieval;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.KnowledgeBaseConfig;
import com.openjiuwen.core.retrieval.common.RetrievalConfig;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.indexing.indexer.Indexer;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.Chunker;
import com.openjiuwen.core.retrieval.indexing.processor.extractor.Extractor;
import com.openjiuwen.core.retrieval.indexing.processor.parser.Parser;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for KnowledgeBase.
 * Mirrors Python's tests/unit_tests/core/retrieval/test_knowledge_base.py
 */
class TestKnowledgeBase {

    private static final String[] KNOWLEDGE_BASE_ATTRIBUTES = {
            "database_name",
            "distance_metric",
            "index_type",
            "text_field",
            "vector_field",
            "sparse_vector_field",
            "metadata_field",
            "doc_id_field"
    };

    private static class ConcreteKnowledgeBase extends KnowledgeBase {
        ConcreteKnowledgeBase(KnowledgeBaseConfig config) {
            super(config);
        }

        ConcreteKnowledgeBase(KnowledgeBaseConfig config,
                              VectorStore vectorStore,
                              Embedding embedModel,
                              Parser parser,
                              Chunker chunker,
                              Extractor extractor,
                              Indexer indexManager,
                              BaseModelClient llmClient) {
            super(config, vectorStore, embedModel, parser, chunker, extractor, indexManager, llmClient, null);
        }

        @Override
        public List<String> addDocuments(List<Document> documents) {
            List<String> ids = new ArrayList<>();
            for (Document doc : documents) {
                ids.add(doc.getId());
            }
            return ids;
        }

        @Override
        public List<RetrievalResult> retrieve(String query, RetrievalConfig config) {
            return new ArrayList<>();
        }

        @Override
        public boolean deleteDocuments(List<String> docIds) {
            return true;
        }

        @Override
        public List<String> updateDocuments(List<Document> documents) {
            List<String> ids = new ArrayList<>();
            for (Document doc : documents) {
                ids.add(doc.getId());
            }
            return ids;
        }

        @Override
        public Map<String, Object> getStatistics() {
            Map<String, Object> stats = new HashMap<>();
            stats.put("kb_id", getConfig().getKbId());
            return stats;
        }
    }

    @Nested
    @DisplayName("KnowledgeBase tests")
    class KnowledgeBaseTests {

        @Test
        @DisplayName("test initialization")
        void testInit() {
            KnowledgeBaseConfig config = new KnowledgeBaseConfig("test_kb");
            KnowledgeBase kb = new ConcreteKnowledgeBase(config);
            
            assertEquals(config, kb.getConfig());
            assertNull(kb.getVectorStore());
            assertNull(kb.getEmbedModel());
            assertNull(kb.getParser());
            assertNull(kb.getChunker());
            assertNull(kb.getExtractor());
            assertNull(kb.getIndexManager());
            assertNull(kb.getLlmClient());
        }

        @Test
        @DisplayName("test initialization with components")
        void testInitWithComponents() {
            KnowledgeBaseConfig config = new KnowledgeBaseConfig("test_kb");
            VectorStore mockVectorStore = mock(VectorStore.class);
            Embedding mockEmbedModel = mock(Embedding.class);
            Parser mockParser = mock(Parser.class);
            Chunker mockChunker = mock(Chunker.class);
            Extractor mockExtractor = mock(Extractor.class);
            Indexer mockIndexManager = mock(Indexer.class);
            BaseModelClient mockLlmClient = mock(BaseModelClient.class);

            for (String attr : KNOWLEDGE_BASE_ATTRIBUTES) {
                setMockAttribute(mockVectorStore, attr, "test_value");
                setMockAttribute(mockIndexManager, attr, "test_value");
            }

            KnowledgeBase kb = new ConcreteKnowledgeBase(
                    config,
                    mockVectorStore,
                    mockEmbedModel,
                    mockParser,
                    mockChunker,
                    mockExtractor,
                    mockIndexManager,
                    mockLlmClient
            );

            assertEquals(mockVectorStore, kb.getVectorStore());
            assertEquals(mockEmbedModel, kb.getEmbedModel());
            assertEquals(mockParser, kb.getParser());
            assertEquals(mockChunker, kb.getChunker());
            assertEquals(mockExtractor, kb.getExtractor());
            assertEquals(mockIndexManager, kb.getIndexManager());
            assertEquals(mockLlmClient, kb.getLlmClient());
        }

        @Test
        @DisplayName("test initialization with mismatching distance metric")
        void testInitWithMismatchMetrics() {
            KnowledgeBaseConfig config = new KnowledgeBaseConfig("test_kb");
            VectorStore mockVectorStore = mock(VectorStore.class);
            Embedding mockEmbedModel = mock(Embedding.class);
            Parser mockParser = mock(Parser.class);
            Chunker mockChunker = mock(Chunker.class);
            Extractor mockExtractor = mock(Extractor.class);
            Indexer mockIndexManager = mock(Indexer.class);
            BaseModelClient mockLlmClient = mock(BaseModelClient.class);

            for (String attr : KNOWLEDGE_BASE_ATTRIBUTES) {
                setMockAttribute(mockVectorStore, attr, "test_value");
                setMockAttribute(mockIndexManager, attr, "test_value");
            }
            
            when(mockVectorStore.getDistanceMetric()).thenReturn("some_metric");
            when(mockIndexManager.getDistanceMetric()).thenReturn("different_metric");

            BaseError exception = assertThrows(BaseError.class, () -> {
                new ConcreteKnowledgeBase(
                        config,
                        mockVectorStore,
                        mockEmbedModel,
                        mockParser,
                        mockChunker,
                        mockExtractor,
                        mockIndexManager,
                        mockLlmClient
                );
            });

            assertTrue(exception.getMessage().contains("incompatible distance_metric configs"));
        }

        @Test
        @DisplayName("test initialization with mismatching database names")
        void testInitWithMismatchNames() {
            KnowledgeBaseConfig config = new KnowledgeBaseConfig("test_kb");
            VectorStore mockVectorStore = mock(VectorStore.class);
            Embedding mockEmbedModel = mock(Embedding.class);
            Parser mockParser = mock(Parser.class);
            Chunker mockChunker = mock(Chunker.class);
            Extractor mockExtractor = mock(Extractor.class);
            Indexer mockIndexManager = mock(Indexer.class);
            BaseModelClient mockLlmClient = mock(BaseModelClient.class);

            for (String attr : KNOWLEDGE_BASE_ATTRIBUTES) {
                setMockAttribute(mockVectorStore, attr, "test_value");
                setMockAttribute(mockIndexManager, attr, "test_value");
            }
            
            when(mockVectorStore.getDatabaseName()).thenReturn("database_name");
            when(mockIndexManager.getDatabaseName()).thenReturn("different_name");

            BaseError exception = assertThrows(BaseError.class, () -> {
                new ConcreteKnowledgeBase(
                        config,
                        mockVectorStore,
                        mockEmbedModel,
                        mockParser,
                        mockChunker,
                        mockExtractor,
                        mockIndexManager,
                        mockLlmClient
                );
            });

            assertTrue(exception.getMessage().contains("incompatible database_name configs"));
        }

        @Test
        @DisplayName("test close with closeable components")
        void testCloseWithCloseableComponents() throws Exception {
            KnowledgeBaseConfig config = new KnowledgeBaseConfig("test_kb");
            VectorStore mockVectorStore = mock(VectorStore.class);
            Indexer mockIndexManager = mock(Indexer.class);

            for (String attr : KNOWLEDGE_BASE_ATTRIBUTES) {
                setMockAttribute(mockVectorStore, attr, "test_value");
                setMockAttribute(mockIndexManager, attr, "test_value");
            }

            KnowledgeBase kb = new ConcreteKnowledgeBase(
                    config,
                    mockVectorStore,
                    null,
                    null,
                    null,
                    null,
                    mockIndexManager,
                    null
            );

            kb.close();

            verify(mockVectorStore, times(1)).close();
            verify(mockIndexManager, times(1)).close();
        }

        @Test
        @DisplayName("test close with none components")
        void testCloseWithNoneComponents() {
            KnowledgeBaseConfig config = new KnowledgeBaseConfig("test_kb");
            KnowledgeBase kb = new ConcreteKnowledgeBase(config);
            
            assertDoesNotThrow(kb::close);
        }

        @Test
        @DisplayName("test close with exception")
        void testCloseWithException() throws Exception {
            KnowledgeBaseConfig config = new KnowledgeBaseConfig("test_kb");
            VectorStore mockVectorStore = mock(VectorStore.class);
            
            doThrow(new RuntimeException("Close error")).when(mockVectorStore).close();

            KnowledgeBase kb = new ConcreteKnowledgeBase(
                    config,
                    mockVectorStore,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );

            assertDoesNotThrow(kb::close);
        }
    }

    private void setMockAttribute(Object mock, String attr, Object value) {
        switch (attr) {
            case "database_name":
                when(((VectorStore) mock).getDatabaseName()).thenReturn((String) value);
                break;
            case "distance_metric":
                when(((VectorStore) mock).getDistanceMetric()).thenReturn((String) value);
                if (mock instanceof Indexer) {
                    when(((Indexer) mock).getDistanceMetric()).thenReturn((String) value);
                }
                break;
            case "index_type":
                when(((VectorStore) mock).getIndexType()).thenReturn((String) value);
                if (mock instanceof Indexer) {
                    when(((Indexer) mock).getIndexType()).thenReturn((String) value);
                }
                break;
            case "text_field":
                when(((VectorStore) mock).getTextField()).thenReturn((String) value);
                if (mock instanceof Indexer) {
                    when(((Indexer) mock).getTextField()).thenReturn((String) value);
                }
                break;
            case "vector_field":
                when(((VectorStore) mock).getVectorField()).thenReturn((String) value);
                if (mock instanceof Indexer) {
                    when(((Indexer) mock).getVectorField()).thenReturn((String) value);
                }
                break;
            case "sparse_vector_field":
                when(((VectorStore) mock).getSparseVectorField()).thenReturn((String) value);
                if (mock instanceof Indexer) {
                    when(((Indexer) mock).getSparseVectorField()).thenReturn((String) value);
                }
                break;
            case "metadata_field":
                when(((VectorStore) mock).getMetadataField()).thenReturn((String) value);
                if (mock instanceof Indexer) {
                    when(((Indexer) mock).getMetadataField()).thenReturn((String) value);
                }
                break;
            case "doc_id_field":
                when(((VectorStore) mock).getDocIdField()).thenReturn((String) value);
                if (mock instanceof Indexer) {
                    when(((Indexer) mock).getDocIdField()).thenReturn((String) value);
                }
                break;
        }
    }
}