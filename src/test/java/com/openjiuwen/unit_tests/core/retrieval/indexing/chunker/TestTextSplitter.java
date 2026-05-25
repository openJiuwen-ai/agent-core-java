/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.retrieval.indexing.chunker;

import com.openjiuwen.core.retrieval.indexing.processor.chunker.TextSplitter;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.RecursiveCharacterTextSplitter;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.TextChunk;

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
    void testRecursiveCharacterTextSplitterExists() {
        // Test that RecursiveCharacterTextSplitter class exists
        assertNotNull(RecursiveCharacterTextSplitter.class);
    }

    @Test
    void testRecursiveCharacterTextSplitterCreation() {
        // Test creating RecursiveCharacterTextSplitter
        RecursiveCharacterTextSplitter splitter = new RecursiveCharacterTextSplitter(512, 50);
        assertEquals(512, splitter.getChunkSize());
        assertEquals(50, splitter.getChunkOverlap());
    }

    @Test
    void testSplitText() {
        // Test splitting text using RecursiveCharacterTextSplitter
        RecursiveCharacterTextSplitter splitter = new RecursiveCharacterTextSplitter(20, 5);
        Document doc = new Document("This is a sample text for testing the splitting functionality.", null);
        List<TextChunk> chunks = splitter.split(doc);
        assertNotNull(chunks);
        assertFalse(chunks.isEmpty());
    }
}