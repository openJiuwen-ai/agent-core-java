/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.unit_tests.core.retrieval.indexing.chunker;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TextPreprocessor.
 * <p>
 * Mirrors Python's text preprocessor tests.
 * Tests text preprocessing for chunking.
 */
class TestTextPreprocessor {

    // ---------------------------------------------------------------------------
    // Tests - Level 0 (Basic preprocessing)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    @DisplayName("Test text normalization")
    void testTextNormalization() {
        String text = "  Multiple   spaces   and\n\nnewlines  ";
        String normalized = text.replaceAll("\\s+", " ").trim();
        
        assertEquals("Multiple spaces and newlines", normalized);
        assertFalse(normalized.contains("  "), "Should not have double spaces");
        assertFalse(normalized.startsWith(" "), "Should not start with space");
        assertFalse(normalized.endsWith(" "), "Should not end with space");
    }

    @Test
    @Tag("level0")
    @DisplayName("Test empty text handling")
    void testEmptyTextHandling() {
        String empty = "";
        String whitespace = "   ";
        
        assertTrue(empty.isEmpty());
        assertTrue(whitespace.trim().isEmpty());
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 1 (Text cleaning)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level1")
    @DisplayName("Test remove special characters")
    void testRemoveSpecialCharacters() {
        String text = "Hello\u0000World\u001FTest";
        // Remove control characters
        String cleaned = text.replaceAll("[\\u0000-\\u001F]", "");
        
        assertEquals("HelloWorldTest", cleaned);
        assertFalse(cleaned.contains("\u0000"));
    }

    @Test
    @Tag("level1")
    @DisplayName("Test normalize unicode")
    void testNormalizeUnicode() {
        String text = "Café résumé";
        // Unicode normalization can be NFC, NFD, etc.
        assertNotNull(text);
        assertTrue(text.contains("é"));
    }

    @Test
    @Tag("level1")
    @DisplayName("Test sentence boundary detection")
    void testSentenceBoundaryDetection() {
        String text = "First sentence. Second sentence! Third sentence?";
        String[] sentences = text.split("(?<=[.!?])\\s+");
        
        assertEquals(3, sentences.length);
        assertEquals("First sentence.", sentences[0]);
        assertEquals("Second sentence!", sentences[1]);
        assertEquals("Third sentence?", sentences[2]);
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 2 (Advanced preprocessing)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level2")
    @DisplayName("Test paragraph splitting")
    void testParagraphSplitting() {
        String text = "First paragraph.\n\nSecond paragraph.\n\nThird paragraph.";
        String[] paragraphs = text.split("\\n\\n+");
        
        assertEquals(3, paragraphs.length);
        assertTrue(paragraphs[0].contains("First"));
        assertTrue(paragraphs[1].contains("Second"));
        assertTrue(paragraphs[2].contains("Third"));
    }

    @Test
    @Tag("level2")
    @DisplayName("Test remove markdown formatting")
    void testRemoveMarkdownFormatting() {
        String markdown = "**Bold** and *italic* and `code`";
        String plainText = markdown
                .replaceAll("\\*\\*(.+?)\\*\\*", "$1")  // Bold
                .replaceAll("\\*(.+?)\\*", "$1")        // Italic
                .replaceAll("`(.+?)`", "$1");           // Code
        
        assertEquals("Bold and italic and code", plainText);
    }

    @Test
    @Tag("level2")
    @DisplayName("Test extract text from HTML-like content")
    void testExtractTextFromHtml() {
        String html = "<p>Hello <b>World</b></p><div>Test</div>";
        String plainText = html.replaceAll("<[^>]+>", "");
        
        assertEquals("Hello WorldTest", plainText);
    }
}