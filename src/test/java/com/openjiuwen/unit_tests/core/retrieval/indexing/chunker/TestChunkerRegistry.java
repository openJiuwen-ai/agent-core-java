/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.unit_tests.core.retrieval.indexing.chunker;

import com.openjiuwen.core.retrieval.indexing.processor.chunker.Chunker;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.ChunkerRegistry;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ChunkerRegistry.
 * <p>
 * Mirrors Python's chunker registry tests.
 * Tests chunker registration and retrieval.
 */
class TestChunkerRegistry {

    // ---------------------------------------------------------------------------
    // Tests - Level 0 (Built-in chunkers)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    @DisplayName("Test get char chunker")
    void testGetCharChunker() {
        Chunker chunker = ChunkerRegistry.getChunker("char");
        assertNotNull(chunker, "char chunker should be registered");
        assertTrue(chunker.getChunkSize() > 0, "Chunk size should be positive");
        assertTrue(chunker.getChunkOverlap() >= 0, "Chunk overlap should be non-negative");
    }

    @Test
    @Tag("level0")
    @DisplayName("Test get token chunker")
    void testGetTokenChunker() {
        Chunker chunker = ChunkerRegistry.getChunker("token");
        assertNotNull(chunker, "token chunker should be registered");
    }

    @Test
    @Tag("level0")
    @DisplayName("Test get text chunker")
    void testGetTextChunker() {
        Chunker chunker = ChunkerRegistry.getChunker("text");
        assertNotNull(chunker, "text chunker should be registered");
    }

    @Test
    @Tag("level0")
    @DisplayName("Test get hybrid chunker")
    void testGetHybridChunker() {
        Chunker chunker = ChunkerRegistry.getChunker("hybrid");
        assertNotNull(chunker, "hybrid chunker should be registered");
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 1 (Registry operations)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level1")
    @DisplayName("Test get non-existent chunker returns null")
    void testGetNonExistentChunker() {
        Chunker chunker = ChunkerRegistry.getChunker("non_existent_chunker");
        assertNull(chunker, "Non-existent chunker should return null");
    }

    @Test
    @Tag("level1")
    @DisplayName("Test get chunker with kwargs")
    void testGetChunkerWithKwargs() {
        // Get chunker with empty kwargs
        Chunker chunker = ChunkerRegistry.getChunker("char", Map.of());
        assertNotNull(chunker, "Should get chunker with empty kwargs");
    }

    @Test
    @Tag("level1")
    @DisplayName("Test get chunker with null kwargs")
    void testGetChunkerWithNullKwargs() {
        Chunker chunker = ChunkerRegistry.getChunker("char", null);
        assertNotNull(chunker, "Should handle null kwargs gracefully");
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 2 (Custom registration)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level2")
    @DisplayName("Test register custom chunker")
    void testRegisterCustomChunker() {
        // Register a custom chunker
        ChunkerRegistry.registerChunker("custom_test", () -> 
                new com.openjiuwen.core.retrieval.indexing.processor.chunker.CharChunker(256, 32));
        
        Chunker chunker = ChunkerRegistry.getChunker("custom_test");
        assertNotNull(chunker, "Custom chunker should be retrievable after registration");
        assertEquals(256, chunker.getChunkSize());
        assertEquals(32, chunker.getChunkOverlap());
    }

    @Test
    @Tag("level2")
    @DisplayName("Test overwrite existing chunker")
    void testOverwriteExistingChunker() {
        // Register a chunker with same name to overwrite
        ChunkerRegistry.registerChunker("char", () -> 
                new com.openjiuwen.core.retrieval.indexing.processor.chunker.CharChunker(512, 50));
        
        Chunker chunker = ChunkerRegistry.getChunker("char");
        assertNotNull(chunker, "Overwritten chunker should be retrievable");
        assertEquals(512, chunker.getChunkSize());
    }
}