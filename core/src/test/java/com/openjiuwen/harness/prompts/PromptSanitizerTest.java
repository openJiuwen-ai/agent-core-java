/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code tests/unit_tests/harness/prompts/test_sanitize.py}.
 */
class PromptSanitizerTest {

    @Test
    void testRemovesAngleBrackets() {
        assertEquals("/home/user/file", PromptSanitizer.sanitizePath("/home/<user>/file"));
    }

    @Test
    void testRemovesBracesAndBrackets() {
        assertEquals("path/id/0", PromptSanitizer.sanitizePath("path/{id}/[0]"));
    }

    @Test
    void testRemovesBacktickAndDollar() {
        assertEquals("path/cmd/VAR", PromptSanitizer.sanitizePath("path/`cmd`/$VAR"));
    }

    @Test
    void testRemovesTripleDots() {
        assertEquals("path//secret", PromptSanitizer.sanitizePath("path/.../secret"));
    }

    @Test
    void testPreservesNormalPath() {
        assertEquals("/home/user/project/file.py", PromptSanitizer.sanitizePath("/home/user/project/file.py"));
    }

    @Test
    void testRemovesEscapedNewlines() {
        assertEquals("pathtofile", PromptSanitizer.sanitizePath("path\\nto\\rfile"));
    }

    @Test
    void testRemovesInjectionChars() {
        String result = PromptSanitizer.sanitizeUserContent("Hello <script>alert(1)</script>");
        assertFalse(result.contains("<"));
        assertFalse(result.contains(">"));
    }

    @Test
    void testTruncatesToMaxLen() {
        assertEquals(100, PromptSanitizer.sanitizeUserContent("a".repeat(5000), 100).length());
    }

    @Test
    void testDefaultMaxLen() {
        assertEquals(2000, PromptSanitizer.sanitizeUserContent("a".repeat(3000)).length());
    }

    @Test
    void testShortContentUnchanged() {
        assertEquals("hello world", PromptSanitizer.sanitizeUserContent("hello world"));
    }

    @Test
    void testSanitizeThenTruncate() {
        String result = PromptSanitizer.sanitizeUserContent("<".repeat(10) + "a".repeat(100), 50);
        assertTrue(result.length() <= 50);
        assertFalse(result.contains("<"));
    }
}
