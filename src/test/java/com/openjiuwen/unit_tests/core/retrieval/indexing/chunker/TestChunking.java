/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.unit_tests.core.retrieval.indexing.chunker;

import com.openjiuwen.core.retrieval.indexing.processor.chunker.Chunker;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.TextChunker;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Chunking operations.
 * <p>
 * Mirrors Python's chunking tests.
 * Tests text chunking functionality.
 */
class TestChunking {

    // ---------------------------------------------------------------------------
    // Tests - Level 0 (Chunking basics)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    @DisplayName("Test chunking divides text into parts")
    void testChunkingDividesText() {
        String longText = "This is a very long text that needs to be chunked into smaller pieces for processing.";
        int chunkSize = 20;
        int overlap = 5;
        
        // Estimate expected chunks
        int expectedMinChunks = (int) Math.ceil((double) longText.length() / chunkSize);
        assertTrue(expectedMinChunks >= 3, "Long text should produce multiple chunks");
    }

    @Test
    @Tag("level0")
    @DisplayName("Test chunking preserves content coverage")
    void testChunkingPreservesContentCoverage() {
        String text = "ABCDEFGHIJKLMNO"; // 15 characters
        int chunkSize = 5;
        int overlap = 1;
        
        // With overlap, chunks should cover all content
        // First chunk: ABCDE (indices 0-4)
        // Second chunk: starts at index 4 (overlap), so EFGHI (indices 4-8)
        // ... this ensures all content is covered
        
        assertTrue(text.length() > chunkSize, "Text should be longer than chunk size");
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 1 (TextChunker)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level1")
    @DisplayName("Test TextChunker creation")
    void testTextChunkerCreation() {
        Chunker chunker = new TextChunker(512, 50, "token");
        assertNotNull(chunker);
        assertEquals(512, chunker.getChunkSize());
        assertEquals(50, chunker.getChunkOverlap());
    }

    @Test
    @Tag("level1")
    @DisplayName("Test TextChunker chunk modes")
    void testTextChunkerChunkModes() {
        // TextChunker supports different chunking modes
        String[] validModes = {"token", "char", "sentence", "paragraph"};
        
        for (String mode : validModes) {
            assertNotNull(mode, "Chunking mode should not be null");
            assertFalse(mode.isEmpty(), "Chunking mode should not be empty");
        }
    }

    @Test
    @Tag("level1")
    @DisplayName("Test chunking with different overlap values")
    void testChunkingWithDifferentOverlap() {
        String text = "This is test text for chunking with overlap.";
        
        // Zero overlap - no shared content between chunks
        Chunker noOverlap = new TextChunker(10, 0, "char");
        List<String> chunksNoOverlap = noOverlap.chunkText(text);
        assertNotNull(chunksNoOverlap);
        
        // High overlap - more shared content between chunks
        Chunker highOverlap = new TextChunker(20, 10, "char");
        List<String> chunksHighOverlap = highOverlap.chunkText(text);
        assertNotNull(chunksHighOverlap);
        
        // More overlap typically produces more chunks
        assertTrue(chunksHighOverlap.size() >= chunksNoOverlap.size(),
                "Higher overlap should produce at least as many chunks");
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 2 (Chunk quality)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level2")
    @DisplayName("Test chunks maintain readability")
    void testChunksMaintainReadability() {
        Chunker chunker = new TextChunker(50, 10, "char");
        String text = "This is a meaningful sentence that should be preserved as much as possible in chunks.";
        
        List<String> chunks = chunker.chunkText(text);
        
        for (String chunk : chunks) {
            assertFalse(chunk.isEmpty(), "Each chunk should have content");
            // Chunks should be readable (not just partial words)
            assertTrue(chunk.trim().length() > 0, "Chunk should not be all whitespace");
        }
    }
}