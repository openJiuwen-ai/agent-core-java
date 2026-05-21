/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.retrieval.indexing.chunker;

import com.openjiuwen.core.retrieval.indexing.processor.chunker.TextSplitter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Text splitter test cases.
 * Mirrors Python's tests/unit_tests/core/retrieval/indexing/processor/chunker/test_text_splitter.py
 */
class TestTextSplitter {

    @Test
    void testTextSplitterExists() {
        // Test that TextSplitter class exists
        assertNotNull(TextSplitter.class);
    }

    @Test
    void testTextSplitterCreation() {
        // Test creating TextSplitter
        TextSplitter splitter = new TextSplitter(512, 50);
        assertEquals(512, splitter.getChunkSize());
        assertEquals(50, splitter.getChunkOverlap());
    }

    @Test
    void testSplitText() {
        // Test splitting text
        TextSplitter splitter = new TextSplitter(20, 5);
        String text = "This is a sample text for testing the splitting functionality.";
        List<String> chunks = splitter.chunkText(text);
        assertNotNull(chunks);
        assertFalse(chunks.isEmpty());
    }
}