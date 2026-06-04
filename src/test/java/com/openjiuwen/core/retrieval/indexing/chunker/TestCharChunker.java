/*
 *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.retrieval.indexing.chunker;

import com.openjiuwen.core.retrieval.indexing.processor.chunker.CharChunker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CharChunker.
 * Mirrors Python's tests/unit_tests/core/retrieval/indexing/processor/chunker/test_char_chunker.py
 */
class TestCharChunker {

    @Nested
    @DisplayName("CharChunker tests")
    class CharChunkerTests {

        @Test
        @DisplayName("test init with defaults")
        void testInitWithDefaults() {
            // Test initialization with default values.
            CharChunker chunker = new CharChunker();

            assertEquals(512, chunker.getChunkSize());
            assertEquals(50, chunker.getChunkOverlap());
        }

        @Test
        @DisplayName("test init with custom values")
        void testInitWithCustomValues() {
            // Test initialization with custom values.
            CharChunker chunker = new CharChunker(256, 25);

            assertEquals(256, chunker.getChunkSize());
            assertEquals(25, chunker.getChunkOverlap());
        }

        @Test
        @DisplayName("test chunk text success")
        void testChunkTextSuccess() {
            // Test chunking text successfully.
            CharChunker chunker = new CharChunker(4, 1);
            List<String> chunks = chunker.chunkText("abcdefghij");

            assertEquals(List.of("abcd", "defg", "ghij"), chunks);
        }

        @Test
        @DisplayName("test chunk text empty")
        void testChunkTextEmpty() {
            // Test chunking empty text.
            CharChunker chunker = new CharChunker(512, 50);
            List<String> chunks = chunker.chunkText("");

            assertTrue(chunks.isEmpty());
        }

        @Test
        @DisplayName("test chunk text null")
        void testChunkTextNull() {
            // Test chunking null text.
            CharChunker chunker = new CharChunker(512, 50);
            List<String> chunks = chunker.chunkText(null);

            assertTrue(chunks.isEmpty());
        }

        @Test
        @DisplayName("test chunk text short")
        void testChunkTextShort() {
            // Test chunking text shorter than chunk_size.
            CharChunker chunker = new CharChunker(100, 10);
            List<String> chunks = chunker.chunkText("Short text");

            assertEquals(1, chunks.size());
            assertEquals("Short text", chunks.get(0));
        }

        @Test
        @DisplayName("test chunk overlap works")
        void testChunkOverlapWorks() {
            // Test that overlap creates overlapping chunks.
            CharChunker chunker = new CharChunker(5, 2);
            List<String> chunks = chunker.chunkText("abcdefghij");

            assertEquals(List.of("abcde", "defgh", "ghij"), chunks);
        }
    }
}
