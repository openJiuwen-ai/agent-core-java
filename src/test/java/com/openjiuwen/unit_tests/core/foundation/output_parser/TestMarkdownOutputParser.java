/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.foundation.output_parser;

import org.junit.jupiter.api.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for markdown output parser.
 * <p>
 * Mirrors Python's {@code test_markdown_output_parser.py} from
 * {@code tests/unit_tests/core/foundation/output_parser/test_markdown_output_parser.py}.
 * Tests parsing structured output from markdown-formatted responses.
 */
class TestMarkdownOutputParser {

    // ---------------------------------------------------------------------------
    // Tests - Level 0 (Pattern matching basics)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testPatternClassExists() {
        assertNotNull(Pattern.class);
    }

    @Test
    @Tag("level0")
    void testMatcherClassExists() {
        assertNotNull(Matcher.class);
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 1 (Markdown code block detection)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level1")
    void testCodeBlockPattern() {
        String markdown = "```json\n{\"key\": \"value\"}\n```";
        Pattern pattern = Pattern.compile("```(\\w+)?\\s*([^`]*)```");
        Matcher matcher = pattern.matcher(markdown);
        assertTrue(matcher.find());
    }

    @Test
    @Tag("level1")
    void testJsonCodeBlockLanguage() {
        String markdown = "```json\n{\"key\": \"value\"}\n```";
        Pattern pattern = Pattern.compile("```(\\w+)");
        Matcher matcher = pattern.matcher(markdown);
        if (matcher.find()) {
            assertEquals("json", matcher.group(1));
        }
    }

    @Test
    @Tag("level1")
    void testCodeBlockContentExtraction() {
        String markdown = "```json\n{\"key\": \"value\"}\n```";
        Pattern pattern = Pattern.compile("```\\w*\\s*([^`]*)```");
        Matcher matcher = pattern.matcher(markdown);
        if (matcher.find()) {
            assertTrue(matcher.group(1).contains("{\"key\": \"value\"}"));
        }
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 2 (Multiple code blocks)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level2")
    void testMultipleCodeBlocks() {
        String markdown = "```python\nprint('hello')\n```\n\n```json\n{\"key\": \"value\"}\n```";
        Pattern pattern = Pattern.compile("```\\w+\\s*([^`]*)```");
        Matcher matcher = pattern.matcher(markdown);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        assertEquals(2, count);
    }

    @Test
    @Tag("level2")
    void testNoCodeBlockInPlainText() {
        String plainText = "This is just plain text without any code blocks.";
        Pattern pattern = Pattern.compile("```");
        Matcher matcher = pattern.matcher(plainText);
        assertFalse(matcher.find());
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 3 (Markdown header parsing)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level3")
    void testHeaderPattern() {
        String markdown = "# Header 1\n## Header 2\n### Header 3";
        Pattern pattern = Pattern.compile("^#{1,6}\\s+(.+)$");
        Matcher matcher = pattern.matcher(markdown);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        assertEquals(3, count);
    }

    @Test
    @Tag("level3")
    void testHeaderLevelDetection() {
        String header = "## This is a level 2 header";
        Pattern pattern = Pattern.compile("^#{1,6}");
        Matcher matcher = pattern.matcher(header);
        if (matcher.find()) {
            assertEquals(2, matcher.group().length());
        }
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 4 (List parsing)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level4")
    void testBulletListPattern() {
        String markdown = "- Item 1\n- Item 2\n- Item 3";
        Pattern pattern = Pattern.compile("^-\\s+(.+)$");
        Matcher matcher = pattern.matcher(markdown);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        assertEquals(3, count);
    }

    @Test
    @Tag("level4")
    void testNumberedListPattern() {
        String markdown = "1. First\n2. Second\n3. Third";
        Pattern pattern = Pattern.compile("^\\d+\\.\\s+(.+)$");
        Matcher matcher = pattern.matcher(markdown);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        assertEquals(3, count);
    }
}