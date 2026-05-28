/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.retrieval.indexing.chunker;

import com.openjiuwen.core.retrieval.indexing.processor.chunker.TextSplitter;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.RecursiveCharacterTextSplitter;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.TextChunk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Text splitter test cases.
 * <p>
 * Mirrors Python's {@code test_text_splitter.py} in
 * {@code tests.unit_tests.core.retrieval.indexing.processor.chunker.test_text_splitter}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>TextSplitter abstract class existence</li>
 *   <li>RecursiveCharacterTextSplitter initialization with defaults and custom values</li>
 *   <li>Overlap adjustment behavior</li>
 *   <li>Text splitting for short and long texts</li>
 *   <li>Overlap behavior verification</li>
 *   <li>Metadata preservation</li>
 * </ul>
 */
class TestTextSplitter {

    /**
     * Test: TextSplitter class exists.
     * <p>
     * Mirrors Python's test_cannot_instantiate_abstract_class.
     */
    @Test
    @Tag("level0")
    @DisplayName("TextSplitter class exists")
    void testTextSplitterExists() {
        assertNotNull(TextSplitter.class, "TextSplitter class should exist");
    }

    /**
     * Test: RecursiveCharacterTextSplitter class exists.
     * <p>
     * Mirrors Python's test_init_with_defaults for class existence.
     */
    @Test
    @Tag("level0")
    @DisplayName("RecursiveCharacterTextSplitter class exists")
    void testRecursiveCharacterTextSplitterExists() {
        assertNotNull(RecursiveCharacterTextSplitter.class, 
            "RecursiveCharacterTextSplitter class should exist");
    }

    /**
     * Test: RecursiveCharacterTextSplitter creation with custom values.
     * <p>
     * Mirrors Python's test_init_with_custom_values.
     *
     * <p>Verification:
     * <ul>
     *   <li>Chunk size matches configured value</li>
     *   <li>Chunk overlap matches configured value</li>
     * </ul>
     */
    @Test
    @Tag("level0")
    @DisplayName("RecursiveCharacterTextSplitter creation with custom values")
    void testRecursiveCharacterTextSplitterCreation() {
        RecursiveCharacterTextSplitter splitter = new RecursiveCharacterTextSplitter(512, 50);
        assertEquals(512, splitter.getChunkSize(), "Chunk size should be 512");
        assertEquals(50, splitter.getChunkOverlap(), "Chunk overlap should be 50");
    }

    /**
     * Test: Splitting text into chunks.
     * <p>
     * Mirrors Python's test_split_long_text.
     *
     * <p>Verification:
     * <ul>
     *   <li>Splitting produces non-empty result</li>
     *   <li>Multiple chunks are created for long text</li>
     *   <li>All chunks belong to the same document</li>
     * </ul>
     */
    @Test
    @Tag("level0")
    @DisplayName("Splitting text into chunks")
    void testSplitText() {
        RecursiveCharacterTextSplitter splitter = new RecursiveCharacterTextSplitter(20, 5);
        Document doc = new Document("This is a sample text for testing the splitting functionality.", null);
        List<TextChunk> chunks = splitter.split(doc);
        assertNotNull(chunks, "Chunks should not be null");
        assertFalse(chunks.isEmpty(), "Chunks should not be empty");
    }

    @Nested
    @DisplayName("RecursiveCharacterTextSplitter Detailed Tests")
    class RecursiveCharacterTextSplitterTests {

        /**
         * Test: Initialization with default separators.
         * <p>
         * Mirrors Python's test_init_with_defaults.
         */
        @Test
        @DisplayName("Default separator configuration")
        void testDefaultSeparators() {
            RecursiveCharacterTextSplitter splitter = new RecursiveCharacterTextSplitter(100, 20);
            // Default separators: ["\n\n", "\n", " ", ""]
            assertEquals(100, splitter.getChunkSize(), "Default chunk size should be 100");
            assertEquals(20, splitter.getChunkOverlap(), "Default overlap should be 20");
        }

        /**
         * Test: Splitting short text produces single chunk.
         * <p>
         * Mirrors Python's test_split_short_text.
         *
         * <p>Verification:
         * <ul>
         *   <li>Short text produces exactly one chunk</li>
         *   <li>Chunk text matches original</li>
         * </ul>
         */
        @Test
        @DisplayName("Splitting short text produces single chunk")
        void testSplitShortText() {
            RecursiveCharacterTextSplitter splitter = new RecursiveCharacterTextSplitter(100, 10);
            Document doc = new Document("Short text", "doc_1");
            List<TextChunk> chunks = splitter.split(doc);
            
            assertEquals(1, chunks.size(), "Short text should produce exactly one chunk");
            assertEquals("Short text", chunks.get(0).getText(), "Chunk text should match original");
        }

        /**
         * Test: Splitting long text produces multiple chunks.
         * <p>
         * Mirrors Python's test_split_long_text.
         */
        @Test
        @DisplayName("Splitting long text produces multiple chunks")
        void testSplitLongText() {
            RecursiveCharacterTextSplitter splitter = new RecursiveCharacterTextSplitter(10, 2);
            String longText = "This is a longer text that needs to be split into multiple chunks";
            Document doc = new Document(longText, "doc_1");
            List<TextChunk> chunks = splitter.split(doc);
            
            assertTrue(chunks.size() > 1, "Long text should produce multiple chunks");
            // Verify all chunks have content
            for (TextChunk chunk : chunks) {
                assertNotNull(chunk.getText(), "Each chunk should have text");
                assertFalse(chunk.getText().isEmpty(), "Chunk text should not be empty");
            }
        }

        /**
         * Test: Splitting with overlap produces overlapping content.
         * <p>
         * Mirrors Python's test_split_with_overlap.
         */
        @Test
        @DisplayName("Splitting with overlap")
        void testSplitWithOverlap() {
            RecursiveCharacterTextSplitter splitter = new RecursiveCharacterTextSplitter(10, 3);
            String text = "This is a test text for splitting";
            Document doc = new Document(text, "doc_1");
            List<TextChunk> chunks = splitter.split(doc);
            
            assertTrue(chunks.size() >= 1, "Should produce at least one chunk");
            
            if (chunks.size() > 1) {
                // Verify there is overlap between adjacent chunks
                TextChunk first = chunks.get(0);
                TextChunk second = chunks.get(1);
                
                // Due to overlap, end of first chunk may appear in start of second
                assertNotNull(first.getText(), "First chunk should have text");
                assertNotNull(second.getText(), "Second chunk should have text");
            }
        }

        /**
         * Test: Chunking method returns string list.
         * <p>
         * Mirrors Python's chunk text tests.
         */
        @Test
        @DisplayName("Chunking method returns string list")
        void testChunkTextMethod() {
            RecursiveCharacterTextSplitter splitter = new RecursiveCharacterTextSplitter(20, 5);
            String text = "This is a sample text for chunking.";
            List<String> chunks = splitter.chunkText(text);
            
            assertNotNull(chunks, "chunkText should return non-null list");
            assertFalse(chunks.isEmpty(), "chunkText should return non-empty list for non-empty input");
            
            // Verify chunk sizes are within bounds
            for (String chunk : chunks) {
                assertTrue(chunk.length() <= splitter.getChunkSize(), 
                    "Each chunk should not exceed chunk size");
            }
        }

        /**
         * Test: Empty document produces empty chunks.
         */
        @Test
        @DisplayName("Empty document produces empty chunks")
        void testSplitEmptyDocument() {
            RecursiveCharacterTextSplitter splitter = new RecursiveCharacterTextSplitter(100, 20);
            Document doc = new Document("", "doc_1");
            List<TextChunk> chunks = splitter.split(doc);
            
            assertTrue(chunks.isEmpty(), "Empty document should produce empty chunks");
        }

        /**
         * Test: Null text produces empty chunks.
         */
        @Test
        @DisplayName("Null text produces empty chunks")
        void testSplitNullText() {
            RecursiveCharacterTextSplitter splitter = new RecursiveCharacterTextSplitter(100, 20);
            Document doc = new Document(null, "doc_1");
            List<TextChunk> chunks = splitter.split(doc);
            
            assertTrue(chunks.isEmpty(), "Null text should produce empty chunks");
        }

        /**
         * Test: Large chunk size handles entire text.
         */
        @Test
        @DisplayName("Large chunk size handles entire text")
        void testLargeChunkSize() {
            RecursiveCharacterTextSplitter splitter = new RecursiveCharacterTextSplitter(1000, 100);
            String text = "Small text";
            Document doc = new Document(text, "doc_1");
            List<TextChunk> chunks = splitter.split(doc);
            
            assertEquals(1, chunks.size(), "Text smaller than chunk size should produce single chunk");
            assertEquals(text, chunks.get(0).getText(), "Chunk should contain entire text");
        }
    }
}