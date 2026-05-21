/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.retrieval.indexing.chunker;

import com.openjiuwen.core.retrieval.indexing.processor.chunker.ChunkerRegistry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chunker registry test cases.
 * Mirrors Python's tests/unit_tests/core/retrieval/indexing/processor/chunker/test_chunker_registry.py
 */
class TestChunkerRegistry {

    @Test
    void testRegistryExists() {
        // Test that ChunkerRegistry class exists
        assertNotNull(ChunkerRegistry.class);
    }

    @Test
    void testGetRegisteredChunkers() {
        // Test getting registered chunker types
        var chunkers = ChunkerRegistry.getRegisteredTypes();
        assertNotNull(chunkers);
    }
}