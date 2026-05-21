/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.retrieval.indexing.chunker;

import com.openjiuwen.core.retrieval.indexing.processor.chunker.HybridChunker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Hybrid chunker test cases.
 * Mirrors Python's tests/unit_tests/core/retrieval/indexing/processor/chunker/test_hybrid_chunker.py
 */
class TestHybridChunker {

    @Test
    void testHybridChunkerExists() {
        // Test that HybridChunker class exists
        assertNotNull(HybridChunker.class);
    }

    @Test
    void testHybridChunkerCreation() {
        // Test creating HybridChunker
        HybridChunker chunker = new HybridChunker(512, 50);
        assertEquals(512, chunker.getChunkSize());
        assertEquals(50, chunker.getChunkOverlap());
    }
}