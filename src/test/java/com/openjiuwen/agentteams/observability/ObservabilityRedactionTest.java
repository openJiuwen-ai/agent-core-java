/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ObservabilityRedaction} redaction utilities.
 *
 * <p>Translates the Python test
 * {@code tests/unit_tests/extensions/tracer_otel/test_redaction.py} into
 * JUnit 5. Verifies truncate, hash, redactPrompt, and redactCompletion
 * behavior against the Java implementation.</p>
 *
 * @since 0.1.7
 */
@DisplayName("ObservabilityRedaction tests")
class ObservabilityRedactionTest {

    // ================================================================
    // truncate
    // ================================================================

    @Nested
    @DisplayName("truncate()")
    class TruncateTest {

        @Test
        @DisplayName("short value not truncated")
        void test_short_value_not_truncated() {
            assertEquals("abc", ObservabilityRedaction.truncate("abc", 10));
        }

        @Test
        @DisplayName("long value truncated with count suffix")
        void test_long_value_truncated() {
            String result = ObservabilityRedaction.truncate("abcdefghij", 5);
            assertEquals("abcde...<truncated 5 chars>", result);
        }

        @Test
        @DisplayName("exact length not truncated")
        void test_exact_length_not_truncated() {
            assertEquals("abcde", ObservabilityRedaction.truncate("abcde", 5));
        }

        @Test
        @DisplayName("zero max length returns original")
        void test_zero_max_length_returns_original() {
            assertEquals("abc", ObservabilityRedaction.truncate("abc", 0));
        }

        @Test
        @DisplayName("negative max length returns original")
        void test_negative_max_length_returns_original() {
            assertEquals("abc", ObservabilityRedaction.truncate("abc", -1));
        }

        @Test
        @DisplayName("null value returns null")
        void test_null_value_returns_null() {
            assertEquals(null, ObservabilityRedaction.truncate(null, 10));
        }
    }

    // ================================================================
    // hash
    // ================================================================

    @Nested
    @DisplayName("hash()")
    class HashTest {

        @Test
        @DisplayName("hash returns sha256 prefix")
        void test_hash_returns_sha256_prefix() throws NoSuchAlgorithmException {
            String result = ObservabilityRedaction.hash("hello");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String expected = "sha256:" + hexPrefix(digest.digest("hello".getBytes(StandardCharsets.UTF_8)), 16);
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("hash empty string")
        void test_hash_empty_string() throws NoSuchAlgorithmException {
            String result = ObservabilityRedaction.hash("");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String expected = "sha256:" + hexPrefix(digest.digest(new byte[0]), 16);
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("hash null returns hash of empty string")
        void test_hash_null_returns_hash_of_empty() throws NoSuchAlgorithmException {
            String result = ObservabilityRedaction.hash(null);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String expected = "sha256:" + hexPrefix(digest.digest(new byte[0]), 16);
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("hash starts with sha256 prefix")
        void test_hash_starts_with_prefix() {
            String result = ObservabilityRedaction.hash("test value");
            assertTrue(result.startsWith("sha256:"),
                    "hash should start with 'sha256:', got: " + result);
        }

        @Test
        @DisplayName("hash produces 16 hex chars after prefix")
        void test_hash_produces_16_hex_chars() {
            String result = ObservabilityRedaction.hash("test value");
            String hexPart = result.substring("sha256:".length());
            assertEquals(16, hexPart.length(),
                    "hash hex part should be 16 chars, got: " + hexPart);
            assertTrue(hexPart.matches("[0-9a-f]{16}"),
                    "hash hex part should be lowercase hex, got: " + hexPart);
        }

        @Test
        @DisplayName("same input produces same hash")
        void test_same_input_produces_same_hash() {
            String h1 = ObservabilityRedaction.hash("consistent");
            String h2 = ObservabilityRedaction.hash("consistent");
            assertEquals(h1, h2);
        }

        @Test
        @DisplayName("different inputs produce different hashes")
        void test_different_inputs_produce_different_hashes() {
            String h1 = ObservabilityRedaction.hash("input1");
            String h2 = ObservabilityRedaction.hash("input2");
            assertFalse(h1.equals(h2), "different inputs should produce different hashes");
        }

        private String hexPrefix(byte[] bytes, int length) {
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.substring(0, Math.min(length, hex.length()));
        }
    }

    // ================================================================
    // redactPrompt
    // ================================================================

    @Nested
    @DisplayName("redactPrompt()")
    class RedactPromptTest {

        @Test
        @DisplayName("redaction enabled returns hash")
        void test_redaction_enabled_returns_hash() {
            ObservabilityConfig config = ObservabilityConfig.builder()
                    .shouldRedactPrompts(true)
                    .build();
            String result = ObservabilityRedaction.redactPrompt("hello world", config);
            assertTrue(result.startsWith("sha256:"),
                    "redactPrompt with redaction should return hash, got: " + result);
        }

        @Test
        @DisplayName("redaction disabled returns truncated")
        void test_redaction_disabled_returns_truncated() {
            ObservabilityConfig config = ObservabilityConfig.builder()
                    .shouldRedactPrompts(false)
                    .attributeValueMaxLength(10)
                    .build();
            String result = ObservabilityRedaction.redactPrompt("hello world longer text", config);
            assertEquals("hello worl...<truncated 13 chars>", result);
        }

        @Test
        @DisplayName("redact null returns empty string")
        void test_redact_null_returns_empty() {
            ObservabilityConfig config = ObservabilityConfig.builder()
                    .shouldRedactPrompts(true)
                    .build();
            assertEquals("", ObservabilityRedaction.redactPrompt(null, config));
        }

        @Test
        @DisplayName("redact non-string converts to string then hashes")
        void test_redact_non_string_converts() {
            ObservabilityConfig config = ObservabilityConfig.builder()
                    .shouldRedactPrompts(true)
                    .build();
            String result = ObservabilityRedaction.redactPrompt(123, config);
            assertTrue(result.startsWith("sha256:"),
                    "redactPrompt of non-string should return hash, got: " + result);
        }

        @Test
        @DisplayName("short value no hash when truncation disabled")
        void test_short_value_no_hash_truncation_disabled() {
            ObservabilityConfig config = ObservabilityConfig.builder()
                    .shouldRedactPrompts(false)
                    .attributeValueMaxLength(100)
                    .build();
            assertEquals("short", ObservabilityRedaction.redactPrompt("short", config));
        }
    }

    // ================================================================
    // redactCompletion
    // ================================================================

    @Nested
    @DisplayName("redactCompletion()")
    class RedactCompletionTest {

        @Test
        @DisplayName("redaction enabled returns hash")
        void test_redaction_enabled_returns_hash() {
            ObservabilityConfig config = ObservabilityConfig.builder()
                    .shouldRedactCompletions(true)
                    .build();
            String result = ObservabilityRedaction.redactCompletion("hello world", config);
            assertTrue(result.startsWith("sha256:"),
                    "redactCompletion with redaction should return hash, got: " + result);
        }

        @Test
        @DisplayName("redaction disabled returns truncated")
        void test_redaction_disabled_returns_truncated() {
            ObservabilityConfig config = ObservabilityConfig.builder()
                    .shouldRedactCompletions(false)
                    .attributeValueMaxLength(10)
                    .build();
            String result = ObservabilityRedaction.redactCompletion("hello world longer text", config);
            assertEquals("hello worl...<truncated 13 chars>", result);
        }

        @Test
        @DisplayName("redact null returns empty string")
        void test_redact_null_returns_empty() {
            ObservabilityConfig config = ObservabilityConfig.builder()
                    .shouldRedactCompletions(true)
                    .build();
            assertEquals("", ObservabilityRedaction.redactCompletion(null, config));
        }

        @Test
        @DisplayName("short value no hash when truncation disabled")
        void test_short_value_no_hash_truncation_disabled() {
            ObservabilityConfig config = ObservabilityConfig.builder()
                    .shouldRedactCompletions(false)
                    .attributeValueMaxLength(100)
                    .build();
            assertEquals("short", ObservabilityRedaction.redactCompletion("short", config));
        }
    }

    // ================================================================
    // Prompt vs completion independence
    // ================================================================

    @Nested
    @DisplayName("prompt/completion independence")
    class PromptCompletionIndependenceTest {

        @Test
        @DisplayName("prompts and completions can differ independently")
        void test_prompts_and_completions_independent() {
            ObservabilityConfig config = ObservabilityConfig.builder()
                    .shouldRedactPrompts(false)
                    .shouldRedactCompletions(true)
                    .attributeValueMaxLength(100)
                    .build();

            String promptResult = ObservabilityRedaction.redactPrompt("secret prompt", config);
            String completionResult = ObservabilityRedaction.redactCompletion("secret completion", config);

            assertFalse(promptResult.startsWith("sha256:"),
                    "prompt should not be hashed when shouldRedactPrompts=false, got: " + promptResult);
            assertTrue(completionResult.startsWith("sha256:"),
                    "completion should be hashed when shouldRedactCompletions=true, got: " + completionResult);
        }

        @Test
        @DisplayName("both redaction disabled returns plain truncated")
        void test_both_disabled_returns_plain() {
            ObservabilityConfig config = ObservabilityConfig.builder()
                    .shouldRedactPrompts(false)
                    .shouldRedactCompletions(false)
                    .attributeValueMaxLength(100)
                    .build();

            assertEquals("data", ObservabilityRedaction.redactPrompt("data", config));
            assertEquals("data", ObservabilityRedaction.redactCompletion("data", config));
        }

        @Test
        @DisplayName("both redaction enabled returns hashes")
        void test_both_enabled_returns_hashes() {
            ObservabilityConfig config = ObservabilityConfig.builder()
                    .shouldRedactPrompts(true)
                    .shouldRedactCompletions(true)
                    .build();

            assertNotNull(ObservabilityRedaction.redactPrompt("data", config));
            assertNotNull(ObservabilityRedaction.redactCompletion("data", config));
            assertTrue(ObservabilityRedaction.redactPrompt("data", config).startsWith("sha256:"));
            assertTrue(ObservabilityRedaction.redactCompletion("data", config).startsWith("sha256:"));
        }
    }
}
