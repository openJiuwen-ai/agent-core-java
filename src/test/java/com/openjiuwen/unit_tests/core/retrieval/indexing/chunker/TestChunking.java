/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.retrieval.indexing.chunker;

import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.TextChunker;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Text chunking test cases.
 * Mirrors Python's tests/unit_tests/core/retrieval/indexing/processor/chunker/test_chunking.py
 */
class TestChunking {

    @Test
    void testTextChunkerExists() {
        // Test that TextChunker class exists
        assertNotNull(TextChunker.class);
    }

    @Test
    void testTextChunkerCreation() {
        // Test creating TextChunker
        TextChunker chunker = new TextChunker(512, 50);
        assertEquals(512, chunker.getChunkSize());
        assertEquals(50, chunker.getChunkOverlap());
    }

    @Test
    void testChunkText() {
        // Test chunking text
        TextChunker chunker = new TextChunker(20, 5);
        String text = "This is a sample text for testing the chunking functionality.";
        List<String> chunks = chunker.chunkText(text);
        assertNotNull(chunks);
        assertFalse(chunks.isEmpty());
    }
}