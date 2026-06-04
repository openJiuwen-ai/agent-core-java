/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness.prompts;

import com.openjiuwen.harness.prompts.PromptSanitizer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code tests/unit_tests/harness/prompts/test_sanitize.py}.
 */
class TestSanitize {

    @Test
    void testRemovesAngleBrackets() {
        assertThat(PromptSanitizer.sanitizePath("/home/<user>/file")).isEqualTo("/home/user/file");
    }

    @Test
    void testRemovesBracesAndBrackets() {
        assertThat(PromptSanitizer.sanitizePath("path/{id}/[0]")).isEqualTo("path/id/0");
    }

    @Test
    void testRemovesBacktickAndDollar() {
        assertThat(PromptSanitizer.sanitizePath("path/`cmd`/$VAR")).isEqualTo("path/cmd/VAR");
    }

    @Test
    void testRemovesTripleDots() {
        assertThat(PromptSanitizer.sanitizePath("path/.../secret")).isEqualTo("path//secret");
    }

    @Test
    void testPreservesNormalPath() {
        assertThat(PromptSanitizer.sanitizePath("/home/user/project/file.py"))
                .isEqualTo("/home/user/project/file.py");
    }

    @Test
    void testRemovesEscapedNewlines() {
        assertThat(PromptSanitizer.sanitizePath("path\\nto\\rfile")).isEqualTo("pathtofile");
    }

    @Test
    void testRemovesInjectionChars() {
        String result = PromptSanitizer.sanitizeUserContent("Hello <script>alert(1)</script>", 2000);
        assertThat(result).doesNotContain("<").doesNotContain(">");
    }

    @Test
    void testTruncatesToMaxLen() {
        String result = PromptSanitizer.sanitizeUserContent("a".repeat(5000), 100);
        assertThat(result).hasSize(100);
    }

    @Test
    void testDefaultMaxLen() {
        String result = PromptSanitizer.sanitizeUserContent("a".repeat(3000));
        assertThat(result).hasSize(2000);
    }

    @Test
    void testShortContentUnchanged() {
        assertThat(PromptSanitizer.sanitizeUserContent("hello world")).isEqualTo("hello world");
    }

    @Test
    void testSanitizeThenTruncate() {
        String result = PromptSanitizer.sanitizeUserContent("<".repeat(10) + "a".repeat(100), 50);
        assertThat(result.length()).isLessThanOrEqualTo(50);
        assertThat(result).doesNotContain("<");
    }
}
