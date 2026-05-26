/*
 *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.retrieval.indexing.chunker;

import com.openjiuwen.core.retrieval.indexing.processor.chunker.TokenizerChunker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TokenizerChunker.
 * Mirrors Python's tests/unit_tests/core/retrieval/indexing/processor/chunker/test_tokenizer_chunker.py
 */
class TestTokenizerChunker {

    @Nested
    @DisplayName("TokenizerChunker tests")
    class TokenizerChunkerTests {

        @Test
        @DisplayName("test init with defaults")
        void testInitWithDefaults() {
            // Test initialization with default values.
            TokenizerChunker chunker = new TokenizerChunker(512, 50);

            assertEquals(512, chunker.getChunkSize());
            assertEquals(50, chunker.getChunkOverlap());
            assertEquals("auto", chunker.getLanguage());
            assertNull(chunker.getTokenizer());
        }

        @Test
        @DisplayName("test init with custom values")
        void testInitWithCustomValues() {
            // Test initialization with custom values.
            TokenizerChunker chunker = new TokenizerChunker(256, 25);

            assertEquals(256, chunker.getChunkSize());
            assertEquals(25, chunker.getChunkOverlap());
        }

        @Test
        @DisplayName("test chunk text success")
        void testChunkTextSuccess() {
            // Test chunking text successfully.
            TokenizerChunker chunker = new TokenizerChunker(100, 10);
            List<String> chunks = chunker.chunkText("This is a test text.");

            assertNotNull(chunks);
        }

        @Test
        @DisplayName("test chunk text empty")
        void testChunkTextEmpty() {
            // Test chunking empty text.
            TokenizerChunker chunker = new TokenizerChunker(512, 50);
            List<String> chunks = chunker.chunkText("");

            assertNotNull(chunks);
        }

        @Test
        @DisplayName("test chunk text null")
        void testChunkTextNull() {
            // Test chunking null text.
            TokenizerChunker chunker = new TokenizerChunker(512, 50);
            List<String> chunks = chunker.chunkText(null);

            assertNotNull(chunks);
        }

        @Test
        @DisplayName("test language is set")
        void testLanguageIsSet() {
            // Test that language parameter is set.
            TokenizerChunker chunker = new TokenizerChunker(512, 50, null, "en", null);

            assertEquals("en", chunker.getLanguage());
        }

        @Test
        @DisplayName("test default language is auto")
        void testDefaultLanguageIsAuto() {
            // Test that default language is auto.
            TokenizerChunker chunker = new TokenizerChunker(512, 50);

            assertEquals("auto", chunker.getLanguage());
        }
    }
}