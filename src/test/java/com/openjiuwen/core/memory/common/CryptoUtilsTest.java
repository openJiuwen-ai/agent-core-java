/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.crypto.AEADBadTagException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CryptoUtils (AES-GCM encryption/decryption).
 * Corresponds to Python: test_crypto.py
 */
class CryptoUtilsTest {

    private static final byte[] VALID_KEY = "1234567890abcdef1234567890123456".getBytes(StandardCharsets.UTF_8);

    @Nested
    @DisplayName("Tests for encrypt function")
    class TestEncrypt {

        @Test
        @DisplayName("Test encrypt returns correct format with proper nonce and tag lengths")
        void testEncryptOutputFormat() {
            String plaintext = "Hello, World!";

            CryptoUtils.EncryptResult result = CryptoUtils.encrypt(VALID_KEY, plaintext);

            // Returns EncryptResult with 3 hex strings
            assertNotNull(result);
            assertNotNull(result.ciphertext());
            assertNotNull(result.nonce());
            assertNotNull(result.tag());

            // All should be valid hex strings
            assertDoesNotThrow(() -> hexToBytes(result.ciphertext()));
            assertDoesNotThrow(() -> hexToBytes(result.nonce()));
            assertDoesNotThrow(() -> hexToBytes(result.tag()));

            // Check nonce and tag lengths
            assertEquals(CryptoUtils.NONCE_LENGTH, hexToBytes(result.nonce()).length);
            assertEquals(CryptoUtils.TAG_LENGTH, hexToBytes(result.tag()).length);
        }

        @Test
        @DisplayName("Test encrypt validates key length")
        void testEncryptKeyValidation() {
            String plaintext = "Test data";

            // Key too short
            IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class,
                () -> CryptoUtils.encrypt("short_key".getBytes(StandardCharsets.UTF_8), plaintext));
            assertTrue(ex1.getMessage().contains("Wrong key length"));

            // Key too long
            byte[] longKey = new byte[64];
            IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class,
                () -> CryptoUtils.encrypt(longKey, plaintext));
            assertTrue(ex2.getMessage().contains("Wrong key length"));
        }

        @Test
        @DisplayName("Test same plaintext produces different ciphertexts due to random nonce")
        void testEncryptProducesDifferentCiphertexts() {
            String plaintext = "Same message";

            CryptoUtils.EncryptResult result1 = CryptoUtils.encrypt(VALID_KEY, plaintext);
            CryptoUtils.EncryptResult result2 = CryptoUtils.encrypt(VALID_KEY, plaintext);

            // Ciphertexts should differ (due to random nonce)
            assertTrue(!result1.ciphertext().equals(result2.ciphertext()) ||
                       !result1.nonce().equals(result2.nonce()));
        }
    }

    @Nested
    @DisplayName("Tests for decrypt function")
    class TestDecrypt {

        @Test
        @DisplayName("Test decrypt validates key, nonce, and tag lengths")
        void testDecryptValidation() {
            String ciphertext = "deadbeef";
            String validNonce = "a".repeat(24);  // 12 bytes as hex
            String validTag = "b".repeat(32);    // 16 bytes as hex

            // Wrong key length
            IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class,
                () -> CryptoUtils.decrypt("short".getBytes(StandardCharsets.UTF_8), ciphertext, validNonce, validTag));
            assertTrue(ex1.getMessage().contains("Wrong key length"));

            // Wrong nonce length
            IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class,
                () -> CryptoUtils.decrypt(VALID_KEY, ciphertext, "ab", validTag));
            assertTrue(ex2.getMessage().contains("Wrong nonce length"));

            // Wrong tag length
            IllegalArgumentException ex3 = assertThrows(IllegalArgumentException.class,
                () -> CryptoUtils.decrypt(VALID_KEY, ciphertext, validNonce, "b".repeat(10)));
            assertTrue(ex3.getMessage().contains("Wrong") && ex3.getMessage().contains("length"));
        }

        @Test
        @DisplayName("Test decrypt raises exception when tag doesn't match (tampered data)")
        void testDecryptInvalidAuthentication() {
            String plaintext = "Secret message";

            CryptoUtils.EncryptResult encrypted = CryptoUtils.encrypt(VALID_KEY, plaintext);

            // Tamper with ciphertext
            String tampered = "ff" + encrypted.ciphertext().substring(2);

            assertThrows(Exception.class,
                () -> CryptoUtils.decrypt(VALID_KEY, tampered, encrypted.nonce(), encrypted.tag()));
        }
    }

    @Nested
    @DisplayName("Tests for encryption/decryption round-trip consistency")
    class TestEncryptDecryptConsistency {

        @Test
        @DisplayName("Test encrypt-decrypt roundtrip with various content types")
        void testRoundtripVariousContent() {
            String[] testCases = {
                "Hello, World!",                                // ASCII
                "你好，我叫张三，很高兴认识你",                    // Chinese
                "Hello 你好 こんにちは 🎉🎊",                    // Mixed with emoji
                "A".repeat(10000),                              // Long text
                "Line1\nLine2\tTab\r\nWindows newline\0null",   // Special chars
                "",                                             // Empty string
            };

            for (String plaintext : testCases) {
                CryptoUtils.EncryptResult encrypted = CryptoUtils.encrypt(VALID_KEY, plaintext);
                String decrypted = CryptoUtils.decrypt(VALID_KEY, encrypted.ciphertext(),
                    encrypted.nonce(), encrypted.tag());
                assertEquals(plaintext, decrypted,
                    "Failed for: " + (plaintext.length() > 50 ? plaintext.substring(0, 50) + "..." : plaintext));
            }
        }

        @Test
        @DisplayName("Test decryption with wrong key fails")
        void testDecryptWithWrongKeyFails() {
            byte[] key1 = "1234567890abcdef1234567890123456".getBytes(StandardCharsets.UTF_8);
            byte[] key2 = "abcdef12345678901234567890123456".getBytes(StandardCharsets.UTF_8);
            String plaintext = "Secret";

            CryptoUtils.EncryptResult encrypted = CryptoUtils.encrypt(key1, plaintext);

            assertThrows(Exception.class,
                () -> CryptoUtils.decrypt(key2, encrypted.ciphertext(), encrypted.nonce(), encrypted.tag()));
        }
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}

