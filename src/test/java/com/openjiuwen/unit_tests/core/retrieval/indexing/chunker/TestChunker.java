/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.unit_tests.core.retrieval.indexing.chunker;

import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.CharChunker;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.Chunker;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Chunker.
 * <p>
 * Mirrors Python's chunker tests.
 * Tests document chunking functionality.
 */
class TestChunker {

    // ---------------------------------------------------------------------------
    // Tests - Level 0 (Chunker creation)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    @DisplayName("Test CharChunker creation with valid parameters")
    void testCharChunkerCreation() {
        Chunker chunker = new CharChunker(100, 20);
        assertNotNull(chunker);
        assertEquals(100, chunker.getChunkSize());
        assertEquals(20, chunker.getChunkOverlap());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test chunk size must be positive")
    void testChunkSizeMustBePositive() {
        // Chunker should throw on invalid chunk size
        assertThrows(Exception.class, () -> new CharChunker(0, 10));
        assertThrows(Exception.class, () -> new CharChunker(-1, 10));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test chunk overlap must be non-negative and smaller than chunk size")
    void testChunkOverlapValidation() {
        // Valid cases
        assertDoesNotThrow(() -> new CharChunker(100, 0));
        assertDoesNotThrow(() -> new CharChunker(100, 99));
        
        // Invalid: overlap >= chunk size
        assertThrows(Exception.class, () -> new CharChunker(100, 100));
        assertThrows(Exception.class, () -> new CharChunker(100, 150));
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 1 (Text chunking)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level1")
    @DisplayName("Test chunk text into parts")
    void testChunkText() {
        Chunker chunker = new CharChunker(10, 2);
        String text = "This is a test text for chunking.";
        
        List<String> chunks = chunker.chunkText(text);
        
        assertNotNull(chunks);
        assertFalse(chunks.isEmpty(), "Should produce at least one chunk");
        
        // Each chunk should be at most chunkSize characters (except possibly the last)
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= 12, // chunkSize + some overlap tolerance
                    "Chunk should not exceed chunk size significantly");
        }
    }

    @Test
    @Tag("level1")
    @DisplayName("Test chunk empty text returns empty list")
    void testChunkEmptyText() {
        Chunker chunker = new CharChunker(100, 20);
        List<String> chunks = chunker.chunkText("");
        assertTrue(chunks.isEmpty(), "Empty text should produce no chunks");
    }

    @Test
    @Tag("level1")
    @DisplayName("Test chunk short text returns single chunk")
    void testChunkShortText() {
        Chunker chunker = new CharChunker(100, 20);
        String shortText = "Short text";
        
        List<String> chunks = chunker.chunkText(shortText);
        
        assertEquals(1, chunks.size(), "Short text should produce single chunk");
        assertEquals(shortText, chunks.get(0));
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 2 (Document chunking)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level2")
    @DisplayName("Test chunk documents")
    void testChunkDocuments() {
        Chunker chunker = new CharChunker(50, 10);
        List<Document> documents = new ArrayList<>();
        documents.add(new Document("doc1", "This is a longer document that should be split into multiple chunks for testing.", null));
        documents.add(new Document("doc2", "Another document.", null));
        
        List<TextChunk> chunks = chunker.chunkDocuments(documents);
        
        assertNotNull(chunks);
        assertFalse(chunks.isEmpty(), "Should produce chunks from documents");
        
        // Verify chunk metadata
        for (TextChunk chunk : chunks) {
            assertNotNull(chunk.getId(), "Chunk should have an ID");
            assertNotNull(chunk.getText(), "Chunk should have text content");
            assertNotNull(chunk.getMetadata(), "Chunk should have metadata");
        }
    }

    @Test
    @Tag("level2")
    @DisplayName("Test chunk documents with null input")
    void testChunkDocumentsNullInput() {
        Chunker chunker = new CharChunker(100, 20);
        List<TextChunk> chunks = chunker.chunkDocuments(null);
        assertNotNull(chunks);
        assertTrue(chunks.isEmpty(), "Null documents should produce empty chunks");
    }
}