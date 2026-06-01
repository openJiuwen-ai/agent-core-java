/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.unit_tests.core.retrieval.indexing.chunker;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.Chunker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for Chunker.
 *
 * <p>Mirrors Python's {@code test_base.py} chunker tests.</p>
 */
class TestChunker {

    @Test
    @DisplayName("Test initialization with default values")
    void testInitWithDefaults() {
        ConcreteChunker chunker = new ConcreteChunker();

        assertEquals(512, chunker.getChunkSize());
        assertEquals(50, chunker.getChunkOverlap());
        assertEquals(3, chunker.getLengthFunction().apply("abc"));
    }

    @Test
    @DisplayName("Test initialization with custom values")
    void testInitWithCustomValues() {
        Function<String, Integer> wordCountLength = text -> text.split("\\s+").length;

        ConcreteChunker chunker = new ConcreteChunker(1024, 100, wordCountLength);

        assertEquals(1024, chunker.getChunkSize());
        assertEquals(100, chunker.getChunkOverlap());
        assertSame(wordCountLength, chunker.getLengthFunction());
    }

    @Test
    @DisplayName("Test initialization with chunk_size = 0")
    void testInitInvalidChunkSizeZero() {
        BaseError error = assertThrows(BaseError.class, () -> new ConcreteChunker(0, 50, null));

        assertTrue(error.getMessage().contains("chunk_size must be greater than 0, current value: 0"));
    }

    @Test
    @DisplayName("Test initialization with negative chunk_size")
    void testInitInvalidChunkSizeNegative() {
        BaseError error = assertThrows(BaseError.class, () -> new ConcreteChunker(-1, 50, null));

        assertTrue(error.getMessage().contains("chunk_size must be greater than 0, current value: -1"));
    }

    @Test
    @DisplayName("Test initialization with negative chunk_overlap")
    void testInitInvalidOverlapNegative() {
        BaseError error = assertThrows(BaseError.class, () -> new ConcreteChunker(100, -1, null));

        assertTrue(error.getMessage().contains(
                "chunk_overlap must be greater than or equal to 0, current value: -1"));
    }

    @Test
    @DisplayName("Test invalid overlap size")
    void testInitInvalidOverlap() {
        BaseError error = assertThrows(BaseError.class, () -> new ConcreteChunker(100, 100, null));

        assertTrue(error.getMessage().contains("chunk_overlap must be less than chunk_size"));
    }

    @Test
    @DisplayName("Test chunking text")
    void testChunkText() {
        ConcreteChunker chunker = new ConcreteChunker();

        List<String> chunks = chunker.chunkText("This is a test text for chunking");

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.stream().allMatch(String.class::isInstance));
    }

    @Test
    @DisplayName("Test chunking document list")
    void testChunkDocuments() {
        ConcreteChunker chunker = new ConcreteChunker();
        List<Document> documents = List.of(
                new Document("doc_1", "This is document 1"),
                new Document("doc_2", "This is document 2"));

        List<TextChunk> chunks = chunker.chunkDocuments(documents);

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.stream().allMatch(chunk -> Set.of("doc_1", "doc_2").contains(chunk.getDocId())));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.getMetadata().containsKey("chunk_index")));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.getMetadata().containsKey("total_chunks")));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.getId().equals(chunk.getMetadata().get("chunk_id"))));
    }

    @Test
    @DisplayName("Test chunking documents with metadata")
    void testChunkDocumentsWithMetadata() {
        ConcreteChunker chunker = new ConcreteChunker();
        List<Document> documents = List.of(new Document(
                "doc_1",
                "This is document 1",
                Map.of("source", "test", "author", "test_author")));

        List<TextChunk> chunks = chunker.chunkDocuments(documents);

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.stream().allMatch(chunk -> chunk.getMetadata().containsKey("source")));
        assertTrue(chunks.stream().allMatch(chunk -> "test".equals(chunk.getMetadata().get("source"))));
    }

    @Test
    @DisplayName("Test processing documents through Processor interface")
    void testProcess() {
        ConcreteChunker chunker = new ConcreteChunker();
        List<Document> documents = List.of(new Document("doc_1", "Test document"));

        List<TextChunk> chunks = chunker.process(documents, Map.of());

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.stream().allMatch(chunk -> "doc_1".equals(chunk.getDocId())));
    }

    private static final class ConcreteChunker extends Chunker {

        private ConcreteChunker() {
            super();
        }

        private ConcreteChunker(int chunkSize, int chunkOverlap, Function<String, Integer> lengthFunction) {
            super(chunkSize, chunkOverlap, lengthFunction);
        }

        @Override
        public List<String> chunkText(String text) {
            if (text == null || text.isEmpty()) {
                return List.of();
            }
            java.util.ArrayList<String> chunks = new java.util.ArrayList<>();
            for (int i = 0; i < text.length(); i += 10) {
                chunks.add(text.substring(i, Math.min(i + 10, text.length())));
            }
            return chunks;
        }
    }
}
