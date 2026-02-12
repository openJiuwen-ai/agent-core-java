/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.index;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BaseMemoryManager encrypt/decrypt utility methods.
 * Corresponds to Python: test_base_memory_manager.py
 */
@DisplayName("BaseMemoryManager Tests")
class BaseMemoryManagerTest {

    private static final byte[] VALID_KEY = "1234567890abcdef1234567890abcdef".getBytes();
    private static final byte[] INVALID_KEY = "short_key".getBytes();
    private static final byte[] DIFFERENT_KEY = "fedcba0987654321fedcba0987654321".getBytes();

    @Nested
    @DisplayName("TestEncryptMemoryIfNeeded")
    class TestEncryptMemoryIfNeeded {

        @Test
        @DisplayName("Test empty key returns original plaintext without encryption")
        void testEmptyKeyReturnsOriginal() {
            String plaintext = "用户喜欢吃川菜";
            String result = BaseMemoryManager.encryptMemoryIfNeeded(new byte[0], plaintext);
            assertEquals(plaintext, result);
        }

        @Test
        @DisplayName("Test null plaintext returns null")
        void testNullPlaintextReturnsNull() {
            String result = BaseMemoryManager.encryptMemoryIfNeeded(VALID_KEY, null);
            assertNull(result);
        }

        @Test
        @DisplayName("Test empty plaintext returns empty string")
        void testEmptyPlaintextReturnsEmpty() {
            String result = BaseMemoryManager.encryptMemoryIfNeeded(VALID_KEY, "");
            assertEquals("", result);
        }

        @Test
        @DisplayName("Test valid encryption returns nonce+tag+ciphertext hex string")
        void testValidEncryptionReturnsHexString() {
            String plaintext = "用户喜欢吃川菜";
            String result = BaseMemoryManager.encryptMemoryIfNeeded(VALID_KEY, plaintext);

            // Result should be hex string: nonce (24) + tag (32) + ciphertext
            assertNotNull(result);
            assertTrue(result.length() >= 56); // 24 (nonce) + 32 (tag) minimum
        }

        @Test
        @DisplayName("Test encrypted result is different from plaintext")
        void testEncryptionDifferentFromPlaintext() {
            String plaintext = "sensitive information";
            String result = BaseMemoryManager.encryptMemoryIfNeeded(VALID_KEY, plaintext);

            assertNotEquals(plaintext, result);
            assertFalse(result.contains(plaintext));
        }

        @Test
        @DisplayName("Test invalid key (wrong length) returns empty string")
        void testInvalidKeyReturnsEmptyString() {
            String plaintext = "用户喜欢吃川菜";
            String result = BaseMemoryManager.encryptMemoryIfNeeded(INVALID_KEY, plaintext);

            assertEquals("", result);
        }
    }

    @Nested
    @DisplayName("TestDecryptMemoryIfNeeded")
    class TestDecryptMemoryIfNeeded {

        @Test
        @DisplayName("Test empty key returns original ciphertext without decryption")
        void testDecryptEmptyKeyReturnsOriginal() {
            String ciphertext = "some_encrypted_data_hex";
            String result = BaseMemoryManager.decryptMemoryIfNeeded(new byte[0], ciphertext);
            assertEquals(ciphertext, result);
        }

        @Test
        @DisplayName("Test null ciphertext returns null")
        void testNullCiphertextReturnsNull() {
            String result = BaseMemoryManager.decryptMemoryIfNeeded(VALID_KEY, null);
            assertNull(result);
        }

        @Test
        @DisplayName("Test empty ciphertext returns empty string")
        void testEmptyCiphertextReturnsEmpty() {
            String result = BaseMemoryManager.decryptMemoryIfNeeded(VALID_KEY, "");
            assertEquals("", result);
        }

        @Test
        @DisplayName("Test ciphertext shorter than nonce+tag length returns empty string")
        void testCiphertextTooShortReturnsEmpty() {
            // Ciphertext should be at least 56 chars (24 nonce + 32 tag)
            String shortCiphertext = "abc123";
            String result = BaseMemoryManager.decryptMemoryIfNeeded(VALID_KEY, shortCiphertext);

            assertEquals("", result);
        }

        @Test
        @DisplayName("Test invalid ciphertext (garbage data) returns empty string")
        void testInvalidCiphertextReturnsEmpty() {
            // Long enough but invalid hex/content
            String garbage = "a".repeat(100);
            String result = BaseMemoryManager.decryptMemoryIfNeeded(VALID_KEY, garbage);

            assertEquals("", result);
        }
    }

    @Nested
    @DisplayName("TestEncryptDecryptRoundTrip")
    class TestEncryptDecryptRoundTrip {

        @Test
        @DisplayName("Test encrypt then decrypt returns original ASCII text")
        void testRoundtripAscii() {
            String original = "Hello, World!";

            String encrypted = BaseMemoryManager.encryptMemoryIfNeeded(VALID_KEY, original);
            String decrypted = BaseMemoryManager.decryptMemoryIfNeeded(VALID_KEY, encrypted);

            assertEquals(original, decrypted);
        }

        @Test
        @DisplayName("Test encrypt then decrypt returns original Unicode text")
        void testRoundtripUnicode() {
            String original = "用户喜欢吃川菜，特别是水煮鱼和麻婆豆腐";

            String encrypted = BaseMemoryManager.encryptMemoryIfNeeded(VALID_KEY, original);
            String decrypted = BaseMemoryManager.decryptMemoryIfNeeded(VALID_KEY, encrypted);

            assertEquals(original, decrypted);
        }

        @Test
        @DisplayName("Test encrypt then decrypt returns original mixed content")
        void testRoundtripMixedContent() {
            String original = "User123 likes 川菜 (Sichuan cuisine) 🌶️";

            String encrypted = BaseMemoryManager.encryptMemoryIfNeeded(VALID_KEY, original);
            String decrypted = BaseMemoryManager.decryptMemoryIfNeeded(VALID_KEY, encrypted);

            assertEquals(original, decrypted);
        }

        @Test
        @DisplayName("Test encrypt then decrypt returns original long text")
        void testRoundtripLongText() {
            String original = "这是一段很长的文本。".repeat(100);

            String encrypted = BaseMemoryManager.encryptMemoryIfNeeded(VALID_KEY, original);
            String decrypted = BaseMemoryManager.decryptMemoryIfNeeded(VALID_KEY, encrypted);

            assertEquals(original, decrypted);
        }

        @Test
        @DisplayName("Test decryption with different key returns empty string")
        void testDifferentKeysDecryptFails() {
            String original = "secret message";

            String encrypted = BaseMemoryManager.encryptMemoryIfNeeded(VALID_KEY, original);
            String decrypted = BaseMemoryManager.decryptMemoryIfNeeded(DIFFERENT_KEY, encrypted);

            assertEquals("", decrypted);
            assertNotEquals(original, decrypted);
        }
    }
}

