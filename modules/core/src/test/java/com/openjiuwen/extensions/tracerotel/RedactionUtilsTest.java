/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.tracerotel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RedactionUtils}.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/extensions/tracer_otel/test_redaction.py}.</p>
 */
@DisplayName("RedactionUtils tests")
class RedactionUtilsTest {

    // ----------------------------------------------------------------
    // truncate
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("truncate()")
    class TestTruncate {

        @Test
        @DisplayName("short value not truncated")
        void testShortValueNotTruncated() {
            assertThat(RedactionUtils.truncate("abc", 10)).isEqualTo("abc");
        }

        @Test
        @DisplayName("long value truncated")
        void testLongValueTruncated() {
            assertThat(RedactionUtils.truncate("abcdefghij", 5)).isEqualTo("abcde...<truncated>");
        }

        @Test
        @DisplayName("exact length not truncated")
        void testExactLengthNotTruncated() {
            assertThat(RedactionUtils.truncate("abcde", 5)).isEqualTo("abcde");
        }

        @Test
        @DisplayName("zero max length passes through")
        void testZeroMaxLengthPassesThrough() {
            assertThat(RedactionUtils.truncate("abc", 0)).isEqualTo("abc");
        }

        @Test
        @DisplayName("negative max length passes through")
        void testNegativeMaxLengthPassesThrough() {
            assertThat(RedactionUtils.truncate("abc", -1)).isEqualTo("abc");
        }
    }

    // ----------------------------------------------------------------
    // hashValue
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("hashValue()")
    class TestHashValue {

        @Test
        @DisplayName("hash returns sha256 prefix")
        void testHashReturnsSha256Prefix() throws Exception {
            String result = RedactionUtils.hashValue("hello");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String expected = "sha256:" + toHex(digest.digest("hello".getBytes(StandardCharsets.UTF_8))).substring(0, 16);
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("hash empty string")
        void testHashEmptyString() throws Exception {
            String result = RedactionUtils.hashValue("");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String expected = "sha256:" + toHex(digest.digest(new byte[0])).substring(0, 16);
            assertThat(result).isEqualTo(expected);
        }
    }

    // ----------------------------------------------------------------
    // redact (legacy)
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("redact() legacy")
    class TestRedact {

        @Test
        @DisplayName("redaction enabled returns hash")
        void testRedactionEnabledReturnsHash() {
            OtelTracerConfig config = OtelTracerConfig.builder().isRedactionEnabled(true).build();
            String result = RedactionUtils.redact("hello world", config);
            assertThat(result).startsWith("sha256:");
        }

        @Test
        @DisplayName("redaction disabled returns truncated")
        void testRedactionDisabledReturnsTruncated() {
            OtelTracerConfig config = OtelTracerConfig.builder()
                    .isRedactionEnabled(false).maxAttrLength(10).build();
            String result = RedactionUtils.redact("hello world longer text", config);
            assertThat(result).isEqualTo("hello worl...<truncated>");
        }

        @Test
        @DisplayName("redact null returns hash of empty")
        void testRedactNullReturnsHashOfEmpty() throws Exception {
            OtelTracerConfig config = OtelTracerConfig.builder().isRedactionEnabled(true).build();
            String result = RedactionUtils.redact(null, config);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String expected = "sha256:" + toHex(digest.digest(new byte[0])).substring(0, 16);
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("redact non-string converts")
        void testRedactNonStringConverts() {
            OtelTracerConfig config = OtelTracerConfig.builder().isRedactionEnabled(true).build();
            String result = RedactionUtils.redact(123, config);
            assertThat(result).startsWith("sha256:");
        }

        @Test
        @DisplayName("redact short value no hash truncation disabled")
        void testRedactShortValueNoHashTruncationDisabled() {
            OtelTracerConfig config = OtelTracerConfig.builder()
                    .isRedactionEnabled(false).maxAttrLength(100).build();
            assertThat(RedactionUtils.redact("short", config)).isEqualTo("short");
        }
    }

    // ----------------------------------------------------------------
    // shouldRedact
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("shouldRedact()")
    class TestShouldRedact {

        @Test
        @DisplayName("field null uses redactionEnabled true")
        void testFieldNoneUsesRedactionEnabledTrue() {
            OtelTracerConfig config = OtelTracerConfig.builder().isRedactionEnabled(true).build();
            assertThat(RedactionUtils.shouldRedact(config, null)).isTrue();
        }

        @Test
        @DisplayName("field null uses redactionEnabled false")
        void testFieldNoneUsesRedactionEnabledFalse() {
            OtelTracerConfig config = OtelTracerConfig.builder().isRedactionEnabled(false).build();
            assertThat(RedactionUtils.shouldRedact(config, null)).isFalse();
        }

        @Test
        @DisplayName("field prompts override true")
        void testFieldPromptsOverrideTrue() {
            OtelTracerConfig config = OtelTracerConfig.builder()
                    .isRedactionEnabled(false).shouldRedactPrompts(true).build();
            assertThat(RedactionUtils.shouldRedact(config, "prompts")).isTrue();
        }

        @Test
        @DisplayName("field prompts override false")
        void testFieldPromptsOverrideFalse() {
            OtelTracerConfig config = OtelTracerConfig.builder()
                    .isRedactionEnabled(true).shouldRedactPrompts(false).build();
            assertThat(RedactionUtils.shouldRedact(config, "prompts")).isFalse();
        }

        @Test
        @DisplayName("field prompts null fallback")
        void testFieldPromptsNullFallback() {
            OtelTracerConfig config = OtelTracerConfig.builder()
                    .isRedactionEnabled(true).shouldRedactPrompts(null).build();
            assertThat(RedactionUtils.shouldRedact(config, "prompts")).isTrue();
        }

        @Test
        @DisplayName("field completions override true")
        void testFieldCompletionsOverrideTrue() {
            OtelTracerConfig config = OtelTracerConfig.builder()
                    .isRedactionEnabled(false).shouldRedactCompletions(true).build();
            assertThat(RedactionUtils.shouldRedact(config, "completions")).isTrue();
        }

        @Test
        @DisplayName("field completions override false")
        void testFieldCompletionsOverrideFalse() {
            OtelTracerConfig config = OtelTracerConfig.builder()
                    .isRedactionEnabled(true).shouldRedactCompletions(false).build();
            assertThat(RedactionUtils.shouldRedact(config, "completions")).isFalse();
        }

        @Test
        @DisplayName("field completions null fallback")
        void testFieldCompletionsNullFallback() {
            OtelTracerConfig config = OtelTracerConfig.builder()
                    .isRedactionEnabled(false).shouldRedactCompletions(null).build();
            assertThat(RedactionUtils.shouldRedact(config, "completions")).isFalse();
        }

        @Test
        @DisplayName("different fields independent")
        void testDifferentFieldsIndependent() {
            OtelTracerConfig config = OtelTracerConfig.builder()
                    .isRedactionEnabled(true).shouldRedactPrompts(false).shouldRedactCompletions(true).build();
            assertThat(RedactionUtils.shouldRedact(config, "prompts")).isFalse();
            assertThat(RedactionUtils.shouldRedact(config, "completions")).isTrue();
        }
    }

    // ----------------------------------------------------------------
    // redact with field parameter
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("redact() with field")
    class TestRedactWithField {

        @Test
        @DisplayName("redact prompt with override")
        void testRedactPromptWithOverride() {
            OtelTracerConfig config = OtelTracerConfig.builder()
                    .isRedactionEnabled(true).shouldRedactPrompts(false).maxAttrLength(100).build();
            String result = RedactionUtils.redact("secret prompt", config, "prompts");
            assertThat(result).doesNotStartWith("sha256:");
            assertThat(result).isEqualTo("secret prompt");
        }

        @Test
        @DisplayName("redact completion with override")
        void testRedactCompletionWithOverride() {
            OtelTracerConfig config = OtelTracerConfig.builder()
                    .isRedactionEnabled(true).shouldRedactCompletions(false).maxAttrLength(100).build();
            String result = RedactionUtils.redact("secret response", config, "completions");
            assertThat(result).doesNotStartWith("sha256:");
            assertThat(result).isEqualTo("secret response");
        }

        @Test
        @DisplayName("redact prompt override hash")
        void testRedactPromptOverrideHash() {
            OtelTracerConfig config = OtelTracerConfig.builder()
                    .isRedactionEnabled(false).shouldRedactPrompts(true).build();
            String result = RedactionUtils.redact("test", config, "prompts");
            assertThat(result).startsWith("sha256:");
        }

        @Test
        @DisplayName("redact field null uses legacy")
        void testRedactFieldNoneUsesLegacy() {
            OtelTracerConfig config = OtelTracerConfig.builder()
                    .isRedactionEnabled(false).shouldRedactPrompts(true).maxAttrLength(100).build();
            String result = RedactionUtils.redact("data", config, null);
            assertThat(result).doesNotStartWith("sha256:");
            assertThat(result).isEqualTo("data");
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
