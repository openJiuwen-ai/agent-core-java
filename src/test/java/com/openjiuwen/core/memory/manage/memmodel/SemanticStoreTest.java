/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.memmodel;

import com.openjiuwen.core.common.utils.Pair;
import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.vectorstore.VectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SemanticStore.
 * Corresponds to Python: test_semantic_store.py
 */
class SemanticStoreTest {

    private Embedding mockEmbedding;
    private VectorStore mockVectorStore;
    private SemanticStore semanticStore;
    private SemanticStore semanticStoreNoEmbedding;

    @BeforeEach
    void setUp() {
        // Mock embedding model - returns [0.1, 0.1, 0.1, 0.1] for each text
        mockEmbedding = mock(Embedding.class);
        when(mockEmbedding.embedDocuments(anyList())).thenAnswer(invocation -> {
            List<String> texts = invocation.getArgument(0);
            List<List<Double>> embeddings = texts.stream()
                .map(t -> Arrays.asList(0.1, 0.1, 0.1, 0.1))
                .toList();
            return CompletableFuture.completedFuture(embeddings);
        });

        // Mock vector store
        mockVectorStore = mock(VectorStore.class);
        when(mockVectorStore.add(anyList(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        when(mockVectorStore.deleteFromTable(anyList(), anyString())).thenReturn(CompletableFuture.completedFuture(true));
        when(mockVectorStore.search(anyList(), anyInt(), anyString())).thenReturn(CompletableFuture.completedFuture(Collections.emptyList()));
        when(mockVectorStore.deleteTable(anyString())).thenReturn(CompletableFuture.completedFuture(true));
        // Also mock the Map<String,Object> filters variant for fallback
        when(mockVectorStore.search(anyList(), anyInt(), (Map<String, Object>) any())).thenReturn(CompletableFuture.completedFuture(Collections.emptyList()));

        // Create semantic stores
        semanticStore = new SemanticStore(mockVectorStore, mockEmbedding);
        semanticStoreNoEmbedding = new SemanticStore(mockVectorStore, null);
    }

    @Nested
    @DisplayName("Tests for SemanticStore.initializeEmbeddingModel()")
    class TestInitializeEmbeddingModel {

        @Test
        @DisplayName("Test initialize_embedding_model sets the model")
        void testInitializeSetsEmbeddingModel() {
            SemanticStore store = new SemanticStore(mockVectorStore, null);
            assertNull(store.getEmbeddingModel());

            Embedding newModel = mock(Embedding.class);
            store.initializeEmbeddingModel(newModel);

            assertSame(newModel, store.getEmbeddingModel());
        }
    }

    @Nested
    @DisplayName("Tests for SemanticStore.addDocs()")
    class TestAddDocs {

        @Test
        @DisplayName("Test successful document addition")
        void testAddDocsSuccess() throws Exception {
            List<Pair<String, String>> docs = Arrays.asList(
                new Pair<>("id1", "text1"),
                new Pair<>("id2", "text2")
            );

            Boolean result = semanticStore.addDocs(docs, "test_table", "scope1").get();

            assertTrue(result);
            verify(mockEmbedding).embedDocuments(Arrays.asList("text1", "text2"));
            verify(mockVectorStore).add(anyList(), eq("test_table"));
        }

        @Test
        @DisplayName("Test add_docs returns False when embedding model not initialized")
        void testAddDocsNoEmbeddingReturnsFalse() throws Exception {
            List<Pair<String, String>> docs = Arrays.asList(new Pair<>("id1", "text1"));

            Boolean result = semanticStoreNoEmbedding.addDocs(docs, "test_table", null).get();

            assertFalse(result);
        }

        @Test
        @DisplayName("Test add_docs returns False on exception")
        void testAddDocsExceptionReturnsFalse() throws Exception {
            when(mockEmbedding.embedDocuments(anyList()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Embedding error")));

            List<Pair<String, String>> docs = Arrays.asList(new Pair<>("id1", "text1"));

            Boolean result = semanticStore.addDocs(docs, "test_table", null).get();

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("Tests for SemanticStore.deleteDocs()")
    class TestDeleteDocs {

        @Test
        @DisplayName("Test successful document deletion")
        void testDeleteDocsSuccess() throws Exception {
            Boolean result = semanticStore.deleteDocs(Arrays.asList("id1", "id2"), "test_table").get();

            assertTrue(result);
            verify(mockVectorStore).deleteFromTable(Arrays.asList("id1", "id2"), "test_table");
        }

        @Test
        @DisplayName("Test delete_docs returns False on exception")
        void testDeleteDocsExceptionReturnsFalse() throws Exception {
            when(mockVectorStore.deleteFromTable(anyList(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Delete error")));

            Boolean result = semanticStore.deleteDocs(Arrays.asList("id1"), "test_table").get();

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("Tests for SemanticStore.search()")
    class TestSearch {

        @Test
        @DisplayName("Test successful search returns (id, score) tuples")
        void testSearchSuccess() throws Exception {
            // Mock search results
            SearchResult mockResult = SearchResult.builder()
                .id("id1")
                .text("test text")
                .score(0.85)
                .build();
            when(mockVectorStore.search(anyList(), anyInt(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(Arrays.asList(mockResult)));
            when(mockVectorStore.search(anyList(), anyInt(), (Map<String, Object>) any()))
                .thenReturn(CompletableFuture.completedFuture(Arrays.asList(mockResult)));

            List<Pair<String, Double>> result = semanticStore.search("test query", "test_table", null, 5).get();

            assertEquals(1, result.size());
            assertEquals("id1", result.get(0).getKey());
            assertEquals(0.85, result.get(0).getValue(), 0.001);
            verify(mockEmbedding).embedDocuments(Arrays.asList("test query"));
        }

        @Test
        @DisplayName("Test search returns empty list when embedding model not initialized")
        void testSearchNoEmbeddingReturnsEmpty() throws Exception {
            List<Pair<String, Double>> result = semanticStoreNoEmbedding.search("test", "test_table", null, 5).get();

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Test search returns empty list when embedding fails")
        void testSearchEmptyEmbeddingReturnsEmpty() throws Exception {
            when(mockEmbedding.embedDocuments(anyList()))
                .thenReturn(CompletableFuture.completedFuture(Collections.emptyList()));

            List<Pair<String, Double>> result = semanticStore.search("test", "test_table", null, 5).get();

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Test search returns empty list on exception")
        void testSearchExceptionReturnsEmpty() throws Exception {
            when(mockVectorStore.search(anyList(), anyInt(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Search error")));
            when(mockVectorStore.search(anyList(), anyInt(), (Map<String, Object>) any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Search error")));

            List<Pair<String, Double>> result = semanticStore.search("test", "test_table", null, 5).get();

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Test search passes scope_id to vector store")
        void testSearchWithScopeId() throws Exception {
            when(mockVectorStore.search(anyList(), anyInt(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(Collections.emptyList()));
            when(mockVectorStore.search(anyList(), anyInt(), (Map<String, Object>) any()))
                .thenReturn(CompletableFuture.completedFuture(Collections.emptyList()));

            semanticStore.search("test", "test_table", "scope1", 10).get();

            verify(mockVectorStore).search(anyList(), eq(10), eq("test_table"));
        }
    }

    @Nested
    @DisplayName("Tests for SemanticStore.deleteTable()")
    class TestDeleteTable {

        @Test
        @DisplayName("Test successful table deletion")
        void testDeleteTableSuccess() throws Exception {
            Boolean result = semanticStore.deleteTable("test_table").get();

            assertTrue(result);
            verify(mockVectorStore).deleteTable("test_table");
        }

        @Test
        @DisplayName("Test delete_table returns False on exception")
        void testDeleteTableExceptionReturnsFalse() throws Exception {
            when(mockVectorStore.deleteTable(anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Delete error")));

            Boolean result = semanticStore.deleteTable("test_table").get();

            assertFalse(result);
        }
    }
}

