/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.tool_impls;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Text Processing.
 * <p>
 * Mirrors Python's test_text_processing.py from
 * <code>tests/unit_tests/core/tool_impls/test_text_processing.py</code>.
 */
@DisplayName("Text Processing Tests")
class TestTextProcessing {

    // Stub classes
    static class TextProcessor {
        String normalize(String text) {
            if (text == null) return "";
            return text.trim().replaceAll("\\s+", " ");
        }

        int countLines(String text) {
            if (text == null || text.isEmpty()) return 0;
            return text.split("\n").length;
        }

        int countWords(String text) {
            if (text == null || text.isEmpty()) return 0;
            String[] words = text.trim().split("\\s+");
            return words.length;
        }

        String truncate(String text, int maxLength) {
            if (text == null) return "";
            if (text.length() <= maxLength) return text;
            return text.substring(0, maxLength) + "...";
        }

        String extractSection(String text, String startMarker, String endMarker) {
            if (text == null) return "";
            int start = text.indexOf(startMarker);
            int end = text.indexOf(endMarker);
            if (start == -1 || end == -1 || start >= end) return "";
            return text.substring(start + startMarker.length(), end);
        }
    }

    @Nested
    @DisplayName("Text Normalization Tests")
    class TestTextNormalization {

        @Test
        @DisplayName("normalize text")
        void testNormalizeText() {
            TextProcessor processor = new TextProcessor();

            String result = processor.normalize("  Hello   World  ");

            assertEquals("Hello World", result);
        }

        @Test
        @DisplayName("normalize null returns empty")
        void testNormalizeNullReturnsEmpty() {
            TextProcessor processor = new TextProcessor();

            String result = processor.normalize(null);

            assertEquals("", result);
        }

        @Test
        @DisplayName("normalize empty returns empty")
        void testNormalizeEmptyReturnsEmpty() {
            TextProcessor processor = new TextProcessor();

            String result = processor.normalize("");

            assertEquals("", result);
        }
    }

    @Nested
    @DisplayName("Line Count Tests")
    class TestLineCount {

        @Test
        @DisplayName("count lines")
        void testCountLines() {
            TextProcessor processor = new TextProcessor();

            int count = processor.countLines("Line 1\nLine 2\nLine 3");

            assertEquals(3, count);
        }

        @Test
        @DisplayName("count lines empty text")
        void testCountLinesEmptyText() {
            TextProcessor processor = new TextProcessor();

            int count = processor.countLines("");

            assertEquals(0, count);
        }

        @Test
        @DisplayName("count lines single line")
        void testCountLinesSingleLine() {
            TextProcessor processor = new TextProcessor();

            int count = processor.countLines("Single line");

            assertEquals(1, count);
        }
    }

    @Nested
    @DisplayName("Word Count Tests")
    class TestWordCount {

        @Test
        @DisplayName("count words")
        void testCountWords() {
            TextProcessor processor = new TextProcessor();

            int count = processor.countWords("Hello World Test");

            assertEquals(3, count);
        }

        @Test
        @DisplayName("count words empty")
        void testCountWordsEmpty() {
            TextProcessor processor = new TextProcessor();

            int count = processor.countWords("");

            assertEquals(0, count);
        }
    }

    @Nested
    @DisplayName("Text Truncation Tests")
    class TestTextTruncation {

        @Test
        @DisplayName("truncate text")
        void testTruncateText() {
            TextProcessor processor = new TextProcessor();

            String result = processor.truncate("This is a long text", 10);

            assertEquals("This is a...", result);
        }

        @Test
        @DisplayName("truncate short text unchanged")
        void testTruncateShortTextUnchanged() {
            TextProcessor processor = new TextProcessor();

            String result = processor.truncate("Short", 10);

            assertEquals("Short", result);
        }
    }

    @Nested
    @DisplayName("Section Extraction Tests")
    class TestSectionExtraction {

        @Test
        @DisplayName("extract section")
        void testExtractSection() {
            TextProcessor processor = new TextProcessor();
            String text = "Start content here END more text";

            String result = processor.extractSection(text, "Start ", " END");

            assertEquals("content here", result);
        }

        @Test
        @DisplayName("extract section no markers")
        void testExtractSectionNoMarkers() {
            TextProcessor processor = new TextProcessor();

            String result = processor.extractSection("plain text", "[START]", "[END]");

            assertEquals("", result);
        }
    }
}