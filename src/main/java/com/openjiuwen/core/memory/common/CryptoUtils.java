/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.common;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * AES-GCM encryption/decryption utilities.
 * Corresponds to Python: common/crypto.py
 */
public final class CryptoUtils {

    public static final int NONCE_LENGTH = 12;
    public static final int BIT_LENGTH = 8;
    public static final int AES_KEY_LENGTH = 32;
    public static final int TAG_LENGTH = 16;
    private static final int TAG_LENGTH_BITS = TAG_LENGTH * 8;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private CryptoUtils() {
        // Utility class, prevent instantiation
    }

    /**
     * Encrypt plaintext using AES-GCM.
     *
     * @param key 32-byte AES key
     * @param plaintext text to encrypt
     * @return EncryptResult containing ciphertext, nonce, and tag as hex strings
     * @throws IllegalArgumentException if key length is invalid
     */
    public static EncryptResult encrypt(byte[] key, String plaintext) {
        if (key.length != AES_KEY_LENGTH) {
            throw new IllegalArgumentException(
                String.format("Wrong key length: %d, expected %d", key.length, AES_KEY_LENGTH));
        }

        try {
            // Generate random nonce
            byte[] nonce = new byte[NONCE_LENGTH];
            SECURE_RANDOM.nextBytes(nonce);

            // Create cipher
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(TAG_LENGTH_BITS, nonce);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

            // Encrypt
            byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
            byte[] ciphertextWithTag = cipher.doFinal(plaintextBytes);

            // Extract ciphertext and tag (GCM appends tag to ciphertext)
            int ciphertextLength = ciphertextWithTag.length - TAG_LENGTH;
            byte[] ciphertext = new byte[ciphertextLength];
            byte[] tag = new byte[TAG_LENGTH];
            System.arraycopy(ciphertextWithTag, 0, ciphertext, 0, ciphertextLength);
            System.arraycopy(ciphertextWithTag, ciphertextLength, tag, 0, TAG_LENGTH);

            return new EncryptResult(
                bytesToHex(ciphertext),
                bytesToHex(nonce),
                bytesToHex(tag)
            );
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed: " + e.getMessage(), e);
        }
    }

    /**
     * Decrypt ciphertext using AES-GCM.
     *
     * @param key 32-byte AES key
     * @param ciphertext ciphertext as hex string
     * @param nonce nonce as hex string
     * @param tag authentication tag as hex string
     * @return decrypted plaintext
     * @throws IllegalArgumentException if key, nonce, or tag length is invalid
     */
    public static String decrypt(byte[] key, String ciphertext, String nonce, String tag) {
        byte[] ciphertextBytes = hexToBytes(ciphertext);
        byte[] nonceBytes = hexToBytes(nonce);
        byte[] tagBytes = hexToBytes(tag);

        if (key.length != AES_KEY_LENGTH) {
            throw new IllegalArgumentException(
                String.format("Wrong key length: %d, expected %d", key.length, AES_KEY_LENGTH));
        }

        if (nonceBytes.length != NONCE_LENGTH) {
            throw new IllegalArgumentException(
                String.format("Wrong nonce length: %d", nonceBytes.length));
        }

        if (tagBytes.length != TAG_LENGTH) {
            throw new IllegalArgumentException(
                String.format("Wrong tag length: %d, expected %d", tagBytes.length, TAG_LENGTH));
        }

        try {
            // Combine ciphertext and tag (GCM expects them together)
            byte[] ciphertextWithTag = new byte[ciphertextBytes.length + tagBytes.length];
            System.arraycopy(ciphertextBytes, 0, ciphertextWithTag, 0, ciphertextBytes.length);
            System.arraycopy(tagBytes, 0, ciphertextWithTag, ciphertextBytes.length, tagBytes.length);

            // Create cipher
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(TAG_LENGTH_BITS, nonceBytes);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

            // Decrypt
            byte[] plaintextBytes = cipher.doFinal(ciphertextWithTag);
            return new String(plaintextBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed: " + e.getMessage(), e);
        }
    }

    /**
     * Convert bytes to hex string.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Convert hex string to bytes.
     */
    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * Encryption result record.
     *
     * @param ciphertext ciphertext as hex string
     * @param nonce nonce as hex string
     * @param tag authentication tag as hex string
     */
    public record EncryptResult(String ciphertext, String nonce, String tag) {
    }
}

