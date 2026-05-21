/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.retrieval.indexing.chunker;

import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.Chunker;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Text chunker abstract base class test cases.
 * Mirrors Python's tests/unit_tests/core/retrieval/indexing/processor/chunker/test_base.py
 */
class TestChunker {

    /**
     * Concrete chunker implementation for testing abstract base class.
     */
    static class ConcreteChunker extends Chunker {

        public ConcreteChunker() {
            super(512, 50);
        }

        public ConcreteChunker(int chunkSize, int chunkOverlap) {
            super(chunkSize, chunkOverlap);
        }

        @Override
        public List<String> chunkText(String text) {
            // Simple chunking: one chunk per 10 characters
            List<String> chunks = new ArrayList<>();
            for (int i = 0; i < text.length(); i += 10) {
                chunks.add(text.substring(i, Math.min(i + 10, text.length())));
            }
            return chunks;
        }
    }

    @Test
    void testInitWithDefaults() {
        // Test initialization with default values
        ConcreteChunker chunker = new ConcreteChunker();
        assertEquals(512, chunker.getChunkSize());
        assertEquals(50, chunker.getChunkOverlap());
    }

    @Test
    void testInitWithCustomValues() {
        // Test initialization with custom values
        ConcreteChunker chunker = new ConcreteChunker(1024, 100);
        assertEquals(1024, chunker.getChunkSize());
        assertEquals(100, chunker.getChunkOverlap());
    }

    @Test
    void testInitInvalidChunkSizeZero() {
        // Test initialization with chunk_size = 0
        assertThrows(IllegalArgumentException.class, () -> new ConcreteChunker(0, 50));
    }

    @Test
    void testInitInvalidChunkSizeNegative() {
        // Test initialization with negative chunk_size
        assertThrows(IllegalArgumentException.class, () -> new ConcreteChunker(-1, 50));
    }

    @Test
    void testInitInvalidOverlapNegative() {
        // Test initialization with negative chunk_overlap
        assertThrows(IllegalArgumentException.class, () -> new ConcreteChunker(100, -1));
    }

    @Test
    void testInitInvalidOverlap() {
        // Test invalid overlap size (overlap >= chunk_size)
        assertThrows(IllegalArgumentException.class, () -> new ConcreteChunker(100, 100));
        assertThrows(IllegalArgumentException.class, () -> new ConcreteChunker(100, 150));
    }

    @Test
    void testChunkText() {
        // Test chunking text
        ConcreteChunker chunker = new ConcreteChunker(20, 5);
        List<String> chunks = chunker.chunkText("Hello world this is a test");
        assertFalse(chunks.isEmpty());
    }

    @Test
    void testChunkDocuments() {
        // Test chunking documents
        ConcreteChunker chunker = new ConcreteChunker(20, 5);
        Document doc = new Document("test_id", "Hello world this is a test document", null);
        List<TextChunk> chunks = chunker.chunkDocuments(List.of(doc));
        assertFalse(chunks.isEmpty());
        for (TextChunk chunk : chunks) {
            assertEquals("test_id", chunk.getDocId());
            assertTrue(chunk.getMetadata().containsKey("chunk_index"));
        }
    }
}